package server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SpiralServer {
    public static final int DEFAULT_PORT = 47624;

    private final int port;
    private final String dataVersion;
    private final DObjectManager dobjMgr;
    private final SceneManager sceneMgr;
    private final MovementConfig movementConfig;
    private final SceneNetworkManager sceneNetwork;
    private final Map<Integer, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(1);
    private ServerSocketChannel serverChannel;
    private volatile boolean running = true;

    public SpiralServer(int port) {
        this.port = port;
        this.dataVersion = detectDataVersion();
        this.dobjMgr = new DObjectManager();
        this.sceneMgr = new SceneManager(this.dobjMgr);
        this.movementConfig = MovementConfig.load();
        this.sceneNetwork = new SceneNetworkManager(
            this.movementConfig.deltaIntervalMs,
            this.movementConfig.trainingDeltaIntervalMs);
    }

    public void start() throws IOException {
        System.setProperty("com.threerings.io.enumPolicy", "ORDINAL");
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        this.serverChannel.bind(new InetSocketAddress(this.port), 128);
        this.sceneNetwork.start();

        System.out.println("==================================================================");
        System.out.println("   SPIRAL KNIGHTS - SERVIDOR PRIVADO CASERO (MINI EMULADOR)       ");
        System.out.println("==================================================================");
        System.out.println(" Servidor escuchando en: 0.0.0.0:" + this.port);
        System.out.println(" Direcciones para clientes LAN/VPN: " + findClientAddresses());
        System.out.println(" Transporte: TCP NoDelay/KeepAlive, colas por cliente, ticks Haven="
            + this.movementConfig.deltaIntervalMs + "ms Training="
            + this.movementConfig.trainingDeltaIntervalMs + "ms");
        System.out.println(" Version de datos efectiva: " + this.dataVersion);
        System.out.println(" Protocolo: Three Rings Presents / Narya / DObject (Framed)");
        System.out.println(" Sala de Pruebas: OID " + SceneManager.SCENE_OID + " (Haven / Level Scene)");
        System.out.println(" Training Hall: OID " + SceneManager.TRAINING_PLACE_OID
            + " (Scene " + SceneManager.TRAINING_SCENE_ID + " / Advanced Training)");
        System.out.println(" Combate: Dungeoneer + armas/ataques/carga/defensa/esquiva habilitados");
        System.out.println(" Movimiento: " + this.movementConfig.describe());
        System.out.println(" Inventario: Armas 5 Estrellas (Leviathan, Combuster, Polaris, etc.)");
        System.out.println(" Estado: LISTO PARA CONEXIONES");
        System.out.println("==================================================================");

        Thread acceptThread = new Thread(this::acceptLoop, "SpiralServer-Acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running && serverChannel.isOpen()) {
            try {
                SocketChannel clientChannel = serverChannel.accept();
                clientChannel.configureBlocking(true);
                clientChannel.socket().setTcpNoDelay(true);
                clientChannel.socket().setKeepAlive(true);
                clientChannel.socket().setReceiveBufferSize(256 * 1024);
                clientChannel.socket().setSendBufferSize(256 * 1024);
                int connId = sessionCounter.getAndIncrement();
                ClientSession session = new ClientSession(
                    clientChannel, dobjMgr, sceneMgr, sceneNetwork, movementConfig,
                    connId, dataVersion, this.port,
                    closed -> this.sessions.remove(closed.getConnectionId(), closed));
                this.sessions.put(connId, session);
                Thread sessionThread = new Thread(session, "ClientSession-" + connId);
                sessionThread.setDaemon(true);
                sessionThread.start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("[SpiralServer] Error accepting client: " + e.getMessage());
                }
            }
        }
    }

    private static String detectDataVersion() {
        Path getdown = Path.of("getdown.txt");
        if (!Files.isRegularFile(getdown)) {
            return "unknown";
        }
        try {
            for (String line : Files.readAllLines(getdown)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("version")) {
                    int separator = trimmed.indexOf('=');
                    if (separator >= 0) {
                        return trimmed.substring(separator + 1).trim();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[SpiralServer] No se pudo leer getdown.txt: " + e.getMessage());
        }
        return "unknown";
    }

    private static String findClientAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> members = network.getInetAddresses();
                while (members.hasMoreElements()) {
                    InetAddress address = members.nextElement();
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException error) {
            System.out.println("[NETWORK][WARNING] no se pudieron enumerar las IP LAN: "
                + error.getMessage());
        }
        return addresses.isEmpty() ? "no detectadas" : String.join(", ", addresses);
    }

    public void stop() {
        this.running = false;
        try {
            if (this.serverChannel != null && this.serverChannel.isOpen()) {
                this.serverChannel.close();
            }
        } catch (IOException e) {}
        for (ClientSession session : this.sessions.values()) {
            session.close();
        }
        this.sessions.clear();
        this.sceneNetwork.close();
        System.out.println("[SpiralServer] Servidor detenido.");
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Puerto invalido, usando puerto por defecto: " + DEFAULT_PORT);
            }
        }

        SpiralServer server = new SpiralServer(port);
        try {
            server.start();

            Scanner scanner = new Scanner(System.in);
            while (server.running) {
                if (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if ("stop".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                        server.stop();
                        break;
                    } else if ("status".equalsIgnoreCase(line)) {
                        System.out.println("[Status] Servidor activo. Sesiones activas: "
                            + server.sessions.size() + ", aceptadas: "
                            + (server.sessionCounter.get() - 1)
                            + ", Haven: "
                            + server.sceneNetwork.participantCount(SceneManager.DEFAULT_SCENE_ID)
                            + ", Training: "
                            + server.sceneNetwork.participantCount(SceneManager.TRAINING_SCENE_ID));
                    } else if ("help".equalsIgnoreCase(line)) {
                        System.out.println("Comandos disponibles: status, stop, help");
                    }
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {}
                }
            }
        } catch (Exception e) {
            System.err.println("[SpiralServer] Error fatal al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
