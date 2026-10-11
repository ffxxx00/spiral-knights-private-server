# Guía para entrar al servidor de tu amigo

Esta entrega utiliza el cliente/datos **20261006114613** y Java incluido para Windows x64.

## Preparar tu PC

Recibí la misma copia completa que utiliza el anfitrión. Extraela fuera del ZIP y fuera de Steam, por ejemplo en `C:\Games\SpiralKnightsPrivate`. No hace falta instalar Java del sistema. El cliente sí necesita un controlador gráfico que permita ejecutar el juego OpenGL.

Pedile al anfitrión la **IP LAN o VPN** y el **puerto**. El predeterminado es TCP 47624. Si están en distintas casas, ambos deben tener la VPN conectada y el anfitrión debe autorizar tu dispositivo/usuario.

## Conectarte

Abrí CMD en la carpeta de la copia y ejecutá uno de estos ejemplos, reemplazando la IP:

```bat
launch_client.bat 192.168.1.20 47624 HeroKnight
```

```bat
launch_client.bat 100.101.102.10 47624 HeroKnight
```

El primero representa una LAN y el segundo una IP VPN de ejemplo. No pegues una URL con http:// ni pongas el puerto dentro de la IP. Host y puerto son argumentos separados.

En PowerShell, para ejecutar un archivo de la carpeta actual, agregá `.\`:

```powershell
.\launch_client.bat 192.168.1.20 47624 HeroKnight
```

Esperá el login local y elegí el personaje ofrecido. El servidor genera nombres por conexión, como HeroKnight o HeroKnight2. El argumento llamado KnightName en el script se pasa como usuario de login; no renombra el personaje. No ingreses datos de tu cuenta oficial: la contraseña local no se verifica como una cuenta real.

## No abrir un servidor por error

En la PC invitada abrí sólo el cliente. `start_server.bat` aloja otra instancia en tu propia PC. `localhost` siempre significa **la PC donde ejecutás el cliente**, por lo que no sirve para entrar al servidor de otra persona.

Si el anfitrión también juega desde la PC que aloja el servidor, puede usar `launch_client.bat` sin parámetros: conecta a localhost:47624.

## Controles y rendimiento

Los controles se ajustan dentro del juego en **Options > Controls**. No cambies la velocidad del servidor para modificar las teclas. La velocidad actual es 5 unidades/s.

Al conectar por localhost se aplica el overlay local: input cada 20 ms, sin colchón de render y sin suavizado residual del personaje controlado. Al conectar por LAN/VPN el lanzador conserva el comportamiento de red original del cliente para tolerar variaciones. La detención visual puede sentirse diferente fuera de loopback; no se prometen 0 ms por Internet.

El backend de memoria FFM se utiliza en ambos modos. La corrección de LWJGL reemplaza los accesos obsoletos: no se ocultan los avisos de Java. No reemplaces las DLL/JAR por archivos del juego en vivo.

## Opciones del lanzador

```text
launch_client.bat [host] [puerto] [usuarioLocal] [-NetworkMode] [-DebugProtocol]
```

| Opción | Uso |
| --- | --- |
| host | localhost, una IP LAN/VPN o un nombre DNS alcanzable |
| puerto | Número 1 a 65535; predeterminado 47624 |
| usuarioLocal | 1 a 24 caracteres: letras, números, guion y guion bajo; predeterminado HeroKnight |
| -NetworkMode | Conserva la política de red original incluso al conectar a localhost |
| -DebugProtocol | Activa logs internos del cliente para diagnóstico; puede reducir los FPS |

Los flags deben ir después de los argumentos. Reiniciá el cliente después de cambiar de modo.

## Si no conecta

Con el anfitrión ya iniciado, desde PowerShell:

```powershell
Test-NetConnection 192.168.1.20 -Port 47624
```

Reemplazá la IP. Si `TcpTestSucceeded` es False, revisen la red/VPN y el firewall del anfitrión. Si es True pero no entra al juego, compartí la hora del intento y el archivo `projectx.log` de tu copia para compararlos con la consola del servidor.

## Qué se conserva

Las opciones del cliente se guardan en `profile/preferences` de tu copia. El emulador crea estado de jugador en memoria; no hay progreso ni cuentas persistentes. Reconectar o reiniciar el servidor puede generar otro nombre y restablecer el estado. Desconectarte del juego cierra tu sesión; no detiene el servidor de tu amigo.
