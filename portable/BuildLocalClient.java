package portable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Generates a local class overlay while preserving the frozen JAR byte-for-byte. */
public final class BuildLocalClient {
    public static void main(String[] args) throws Exception {
        int interval = Integer.parseInt(args[2]);
        if (interval < 10 || interval > 100) throw new IllegalArgumentException("Local interval must be 10..100 ms");
        patchInterval(args[0], args[1], "com/threerings/tudey/data/TudeySceneConfig.class", 100, interval);
        patchInterval(args[0], args[1], "com/threerings/projectx/dungeon/data/LevelSceneConfig.class", 33, interval);
        System.out.println("[LOCAL] Client input interval=" + interval + "ms; frozen JAR unchanged");
    }
    private static void patchInterval(String jarPath, String outputDirectory,
            String entry, int originalInterval, int interval) throws Exception {
        byte[] bytes;
        try (JarFile jar = new JarFile(jarPath)) {
            bytes = jar.getInputStream(jar.getJarEntry(entry)).readAllBytes();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != 0xCAFEBABE) throw new IllegalStateException("Invalid class file");
        buffer.getInt(); // minor + major
        String[] utf8 = new String[u2(buffer)];
        for (int index = 1; index < utf8.length; index++) {
            int tag = buffer.get() & 255;
            switch (tag) {
                case 1: {
                    byte[] value = new byte[u2(buffer)]; buffer.get(value);
                    utf8[index] = new String(value, StandardCharsets.UTF_8); break;
                }
                case 3: case 4: buffer.position(buffer.position()+4); break;
                case 5: case 6: buffer.position(buffer.position()+8); index++; break;
                case 7: case 8: case 16: case 19: case 20: buffer.position(buffer.position()+2); break;
                case 9: case 10: case 11: case 12: case 17: case 18: buffer.position(buffer.position()+4); break;
                case 15: buffer.position(buffer.position()+3); break;
                default: throw new IllegalStateException("Unsupported constant pool tag " + tag);
            }
        }
        buffer.position(buffer.position()+6); // access, this, super
        int interfaces = u2(buffer); buffer.position(buffer.position()+2*interfaces);
        int fields = u2(buffer);
        for (int index = 0; index < fields; index++) skipMember(buffer);
        int methods = u2(buffer), patched = 0;
        for (int index = 0; index < methods; index++) {
            int access = u2(buffer);
            String name = utf8[u2(buffer)], descriptor = utf8[u2(buffer)];
            int attributes = u2(buffer);
            for (int attr = 0; attr < attributes; attr++) {
                String kind = utf8[u2(buffer)]; int length = buffer.getInt(); int start = buffer.position();
                if (name.equals("CU") && descriptor.equals("()I") && kind.equals("Code")) {
                    buffer.getInt(); // max_stack + max_locals
                    int codeLength = buffer.getInt(), offset = buffer.position();
                    // javap: base CU() returns 100; Level CU() returns 33. Preserve instruction size,
                    // stack maps, serialization, constructors and every other method.
                    if ((access & 1) == 0 || codeLength != 3 || bytes[offset] != 0x10
                            || bytes[offset+1] != originalInterval || (bytes[offset+2] & 255) != 0xac) {
                        throw new IllegalStateException("Frozen CU() contract changed; refusing overlay");
                    }
                    bytes[offset+1] = (byte)interval; patched++;
                }
                buffer.position(start+length);
            }
        }
        if (patched != 1) throw new IllegalStateException("Expected exactly one CU() implementation");
        Path output = Path.of(outputDirectory).resolve(entry);
        Files.createDirectories(output.getParent()); Files.write(output, bytes);
    }
    private static int u2(ByteBuffer buffer) { return buffer.getShort() & 65535; }
    private static void skipMember(ByteBuffer buffer) {
        buffer.position(buffer.position()+6); int attributes = u2(buffer);
        for (int index = 0; index < attributes; index++) {
            buffer.getShort(); int length = buffer.getInt(); buffer.position(buffer.position()+length);
        }
    }
}
