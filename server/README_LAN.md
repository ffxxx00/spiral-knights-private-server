# Servidor privado con amigos

Para una instalación desde cero, seguí la [guía completa del anfitrión Windows](../docs/GUIA_HOSTING_WINDOWS.md).
Incluye los permisos específicos de VPN: la regla LAN de LocalSubnet/Privado no equivale a permitir clientes VPN.

La infraestructura actual usa TCP fiable, relojes autoritativos compartidos por
escena y una cola de salida independiente por cliente. Haven y Training Hall
emiten a 50 Hz (20 ms) con la configuración actual. Está pensada para una LAN
o una VPN privada de confianza; no expongas todavía el puerto directamente a
Internet porque el emulador acepta autenticación local sin contraseña real.

## Anfitrión

1. Inicia `start_server.bat` (puerto predeterminado 47624).
2. Abre PowerShell como administrador y ejecuta una vez:
   `powershell -ExecutionPolicy Bypass -File .\allow_lan_firewall.ps1`
3. Comparte con tus amigos una de las direcciones LAN que imprime el servidor.

Para usar otro puerto: `start_server.bat 48000` y agrega `-Port 48000` al script
del firewall.

## Cada invitado

Debe tener esta misma instalación/version de datos y ejecutar desde CMD en su carpeta:

`launch_client.bat 192.168.1.20 47624 NombreLocal`

Reemplaza `192.168.1.20` por la IP del anfitrión. El launcher genera una copia
temporal de `deployment.properties`; no altera la configuración base local.
`NombreLocal` es el usuario local del login; el servidor asigna el nombre del
personaje por conexión. No crea una cuenta ni progreso persistente.

## Diagnóstico

- `status` en la consola muestra sesiones y jugadores por escena.
- `[NETWORK] join/leave` confirma altas y bajas.
- `[NETWORK][TICK]` debe converger a 50 Hz en ambas escenas.
  `maxGapMs` muestra pausas puntuales de JVM/sistema sin convertirlas en pérdida
  permanente de tiempo de juego.
- Cada `SceneDeltaEvent` debe avanzar exactamente 20 ms en ambas escenas y
  referenciar el timestamp del delta inmediatamente anterior. El ACK
  del cliente no detiene esa cadena ordenada.
- `[NETWORK][ACK] lagging/recovered` informa si el cliente deja de consumir la
  cadena. `outboundQueue=0/512` separa atraso de render/procesamiento del cliente
  de una congestión real del socket del servidor.
- `[NETWORK][STATE]` resume cada 100 deltas enviados. Con snapshots cada 20 ms
  muestra 100 `actorStateTicks`. `immediateTicks` cuenta ataques y transiciones.
- `[NETWORK] client send budget=120 messages/sec` evita el límite original de
  10 mensajes/s y la acumulación de controles.
- `[NETWORK][WARNING] outbound queue overflow` identifica únicamente al cliente
  lento; los demás continúan en el ticker compartido.
- Si no conecta, prueba en el invitado:
  `Test-NetConnection 192.168.1.20 -Port 47624`.

No se implementó UDP: el bootstrap anuncia únicamente el transporte TCP que el
servidor realmente atiende.

El launcher desactiva la instrumentación interna del cliente para juego normal.
El modo de render sin buffer y el input de 20 ms se activan solo para loopback.
LAN/VPN conserva la política original del cliente para absorber variaciones de red.
Usa `launch_client.ps1 ... -DebugProtocol` únicamente al capturar un diagnóstico,
porque los cuatro modos debug reducen sensiblemente el rendimiento de Tudey.
