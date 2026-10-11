# Operación, mantenimiento y resolución de problemas

Revisión: **2026-10-10**. Comandos de ejemplo desde la raíz de la copia en Windows.

## Rutina del anfitrión

1. Mantener la copia y su respaldo fuera de Steam.
2. Iniciar `start_server.bat` y esperar `LISTO PARA CONEXIONES`.
3. Dar a los invitados la IP LAN/VPN correcta y el puerto de esa sesión.
4. Usar `status` para ver sesiones activas y ocupantes de cada escena.
5. Avisar antes de apagar y usar `stop` para cerrar las sesiones y el listener.

`help` lista los comandos. `exit` y `quit` también detienen el servidor. Mantenerlo abierto no equivale a guardar progreso: los jugadores y objetos del emulador están en memoria.

## Capturar un registro

Desde CMD, con el puerto elegido libre:

```bat
start_server.bat 47624 > servidor.log 2>&1
```

La salida se escribe en el archivo; esa consola seguirá ocupada por el servidor. Para detenerlo escribí `stop` y Enter aunque no veas el prompt interactivo. No inicies otra copia en el mismo puerto. El cliente habitual escribe `projectx.log` en su carpeta.

Para un diagnóstico puntual del cliente:

```bat
launch_client.bat 192.168.1.20 47624 HeroKnight -DebugProtocol > cliente-debug.log 2>&1
```

El debug puede reducir FPS. Volvé al lanzador sin ese flag para jugar. Al entregar un registro, incluí hora del problema, escena, IP usada, puerto, número de jugadores y pasos para repetirlo. Compartilo sólo con quienes diagnostiquen la copia: puede contener nombres e IP.

## Pruebas y resultados esperados

```bat
test_server.bat > prueba-protocolo.log 2>&1
```

El resultado válido es `PRIVATE SERVER WORLD-ENTRY PROTOCOL TEST PASSED` y código de salida 0. Un mensaje anterior `LISTO PARA CONEXIONES` sólo confirma que inició el servidor de prueba; no implica que toda la prueba pasó. La prueba usa 47625 y cierra su servidor al terminar.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\docs\verificar-copia.ps1
```

El verificador no abre puertos, no compila, no modifica preferencias ni cambia el firewall.

## Diagnosticar de adentro hacia afuera

1. En el anfitrión, confirmar que la consola muestra el puerto correcto y la versión 20261006114613.
2. Probar `launch_client.bat localhost 47624 HeroKnight` en el anfitrión.
3. Desde el invitado, probar `Test-NetConnection IP_DEL_HOST -Port 47624`.
4. Con TCP disponible, abrir el cliente del invitado y buscar `[AUTH] success` y `[NETWORK] join` en el servidor.
5. Si TCP conecta pero el juego falla, comparar `projectx.log`, la consola y hashes/versiones.

`IP_DEL_HOST` es un marcador: reemplazalo por la IP real, sin escribir corchetes ni una URL. Un ping ICMP fallido no reemplaza la prueba del puerto TCP.

## Errores frecuentes

| Síntoma | Qué comprobar o hacer |
| --- | --- |
| El ZIP sólo tiene documentos | Conseguir la copia completa: el ZIP de documentación no contiene el juego |
| Doble clic no inicia o faltan JAR/DLL | Extraer todo; revisar jdk, java_vm, code, native y rsrc; no ejecutar dentro del ZIP |
| Fallo la compilacion del servidor | Leer el error de javac; conservar fuentes y JDK incluidos; no usar javac del PATH |
| El puerto esta ocupado | Cerrar la instancia propia que corresponda o elegir otro puerto; no matar procesos desconocidos |
| TcpTestSucceeded False | Servidor iniciado, IP correcta, VPN autorizada, regla del puerto y perfil LAN; revisar aislamiento Wi-Fi |
| Conecta local pero no desde otra casa | No usar IP LAN ni localhost fuera de su red; conectar la VPN y usar su IP, con permisos de Windows y VPN |
| Firewall VPN no permite al invitado | LocalSubnet no equivale a clientes VPN; revisar la regla específica de IP/puerto/programa de la guía |
| New-NetFirewallRule no existe | Abrir Windows PowerShell en Windows con módulo NetSecurity; no ejecutar ese paso en Linux |
| Access denied al configurar firewall | Ese paso requiere PowerShell como administrador; iniciar el juego no requiere elevar si la carpeta es escribible |
| Version mismatch accepted for local development | El emulador acepta versiones distintas con aviso: eso no prueba compatibilidad; usar esta misma copia congelada |
| La biblioteca congelada cambio | El SHA-256 difiere; volver a la entrega correcta y conservar cualquier cambio propio aparte |
| Falta movement.deltaIntervalMs | Restaurar server/server.properties de la entrega; no borrar la configuración para recurrir a defaults |
| No carga Training Hall | Revisar scenes/294, rsrc/config, copia completa y hashes; adjuntar el log, sin mezclar datos de Steam |
| No carga OpenGL o error de DLL | Revisar controlador gráfico y native/code completos; servidor solo y cliente gráfico son procesos distintos |
| Reaparece objectFieldOffset de LWJGL | Usar launch_client.bat actualizado; su ps1 debe seleccionar memoryBackend=ffm; no agregar unsafe-memory-access=allow |
| Bajo FPS o pausas iniciales | Esperar la carga, quitar DebugProtocol, cerrar sondeos que uno haya abierto y medir nuevamente; no cambiar velocidad para compensar |
| Deslizamiento sólo en LAN/VPN | El overlay sin suavizado sólo se aplica en loopback; la política original de red se conserva remotamente |
| Nombre cambia al reconectar | Los nombres se asignan por conexión; no hay cuenta persistente que recuperar |
| Error del feed social o paneles sociales | El servicio social no está implementado en este emulador |

## Leer los logs de red

| Línea o campo | Significado |
| --- | --- |
| AUTH success | La sesión fue aceptada por el emulador |
| NETWORK join / leave | Entrada o salida de un jugador de una escena |
| NETWORK TICK | Reloj compartido; con la configuración actual tiende a 50 Hz por escena |
| maxGapMs | Mayor pausa puntual del ticker en esa ventana; no equivale por sí sola a ping |
| client send budget=120 messages/sec | Presupuesto negociado para evitar el límite inicial de 10 mensajes/s del cliente |
| ACK lagging / recovered | El cliente se atrasó o volvió a consumir la cadena de estados |
| outboundQueue | Mensajes pendientes del socket del servidor; no mide por sí sola FPS o RTT |
| STATE actorStateTicks | Cantidad de estados de actor enviados en la ventana; actualiza cada 20 ms |
| outbound queue overflow | El cliente no consumió su cola de 512 mensajes; el servidor protege a las otras sesiones |

50 Hz significa un estado cada 20 ms, no latencia física cero. No se implementó UDP: sólo TCP transporta el juego.

## Configuración y respaldo

Los ajustes están en `server/server.properties`. Detené el servidor antes de guardar una modificación y reinicialo después. La configuración actual debe conservarse para comparar resultados: velocidad base 5.0, multiplicador 1.0 y los tres intervalos a 20 ms.

Con servidor y cliente cerrados, copiá la carpeta completa a otra carpeta o unidad. Si sólo guardás cambios propios, respaldá `server`, `portable`, `client-local/src`, lanzadores y `profile/preferences`; para una restauración completa también necesitás los binarios congelados. Ese respaldo guarda archivos/configuración, no progreso de juego en memoria.

Para restaurar, extraé el respaldo en una carpeta nueva, verificá hashes y ejecutá la prueba integral antes de volver a alojar. No sobrescribas automáticamente una instalación en uso. Al mover la carpeta, las reglas VPN limitadas a la ruta de Java deben actualizarse.

## Actualizaciones controladas

Steam no actualiza esta carpeta. Una revisión del servidor se entrega de forma explícita, conservando la versión de datos. Si se decide cambiar el cliente/datos congelados, preparar otra carpeta, otra verificación de compatibilidad y un paquete igual para todos los jugadores. No mezclar JAR, DLL o mapas de distintas revisiones.
