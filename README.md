# Spiral Knights Server

Reimplementación del servidor de Spiral Knights para ejecutar el cliente contra
un servidor propio. El objetivo es reproducir el comportamiento vanilla mediante
contratos del cliente y pruebas de protocolo, combate y escenas. El proyecto está
en desarrollo y todavía no alcanza paridad 1:1.

Repositorio: [ffxxx00/spiral-knights-server](https://github.com/ffxxx00/spiral-knights-server).

**El repositorio Git contiene fuentes y documentación; no descarga el juego ni
Java.** Para preparar un checkout, consultar [README_GITHUB.md](README_GITHUB.md).
Las etapas iniciales se recuperaron de respaldos y están identificadas en
[HISTORY_RECONSTRUCTION.md](HISTORY_RECONSTRUCTION.md).

El [mapa de sistemas del cliente](docs/REVERSE_MAP.md) organiza contratos,
dependencias y próximos refactores. Las [herramientas de análisis](tools/reverse/README.md)
generan fuentes descompiladas e índice local por versión, con hashes y caché.
Los resultados derivados del cliente quedan fuera de Git; se versionan las
herramientas propias y la evidencia escrita para el proyecto.

Esta copia local usa la versión **20261006114613**. Tiene recursos, mapas,
bibliotecas, Java del cliente y JDK propios. Las actualizaciones de Steam no
modifican estos archivos. Todo el desarrollo se realiza aquí; no sincronizar
los recursos con la instalación oficial. `frozen-version.json` registra las
huellas SHA-256 de las bibliotecas y datos principales.

## Documentación para alojarlo y compartirlo

Empezá por [LEEME_PRIMERO.md](LEEME_PRIMERO.md). El [manual completo en HTML](docs/MANUAL_COMPLETO.html)
abre con doble clic y funciona sin conexión. El [índice de documentación](docs/README.md)
incluye guías de anfitrión Windows, cliente, LAN/VPN, firewall, operación,
diagnóstico, entrega y referencia técnica. La documentación se revisó el 11 de octubre de 2026.

## Uso

1. Abrir `start_server.bat` para iniciar el servidor en TCP 47624.
2. Abrir `launch_client.bat` para conectar a localhost y elegir el caballero.
3. Ejecutar `test_server.bat` para compilar y verificar el protocolo.

Otro puerto: `start_server.bat 48000` y
`launch_client.bat localhost 48000 HeroKnight`.
El servidor avisa si el puerto está ocupado; no cierra otros procesos.

## Baja latencia local

Al conectar a localhost, 127.0.0.1 o ::1, el lanzador activa automáticamente
el modo local. Haven y Training Hall actualizan estado cada 20 ms (50 Hz).
El cliente envía controles con intervalo de 20 ms y elimina el colchón de
renderizado anterior de 200 ms en Haven y 400 ms en Training Hall.
El servidor negocia un presupuesto de 120 mensajes/s para evitar que el límite
original de 10 mensajes/s acumule controles y ACK atrasados.
El personaje local sigue directamente su posición predicha: al soltar la tecla,
se elimina el avance visual residual producido por el suavizado original.
La velocidad de movimiento permanece en 5 unidades/s.

Los ajustes del cliente viven en `client-local/`: el lanzador genera un overlay
de clases a partir de la biblioteca y comprueba su SHA-256. No cambia
los JAR, recursos, velocidad del personaje ni controles. Para usar la política
original del cliente en loopback: `launch_client.bat localhost 47624 HeroKnight -NetworkMode`.
En conexiones LAN/VPN se conserva automáticamente el buffer original.
Después de cambiar de modo, reinicia el cliente; después de cambiar la cadencia,
reinicia también el servidor.

El modo local reduce esperas de protocolo; no garantiza 0 ms físicos: siguen
existiendo el tick, el procesamiento y el tiempo de renderizar cada fotograma.

La precarga local ahora se reparte en tandas con presupuesto de 8 ms por etapa,
en vez de 50 ms. Las llamadas de limpieza de memoria usan recolección concurrente
para reducir tirones. Se conservan los recursos completos y la calidad gráfica
seleccionada por el usuario. Mediciones en `validation/render-optimization-notes.md`.

Los lanzadores resuelven todo desde su propia carpeta, incluidos Java y JDK.
El cliente utiliza el backend FFM incluido en el LWJGL
(`org.lwjgl.system.memoryBackend=ffm`). Reemplaza los accesos obsoletos de memoria
sun.misc.Unsafe por java.lang.foreign de Java 25. Los avisos siguen habilitados.
Puedes mover la carpeta completa. `profile/preferences` contiene las opciones
locales del cliente y no comparte el Registro de Windows con el juego oficial.
El perfil comienza nuevo. El servidor conserva `server/server.properties`.

El cliente abre directamente, sin Steam. El lanzador crea `full.dat` antes de
iniciar: la rutina ProjectXApp.Ha() de esta versión comprueba ese marcador y
omite la descarga de recursos por Getdown. `getdown.txt` solo contiene metadata
local de versión; no tiene un origen de actualización oficial.

## Adaptación comprobada el 9 de octubre de 2026

Se actualizaron los nombres de métodos y campos del emulador contra la biblioteca
del cliente, incluida la serialización, objetos y RPC, actores, input, inventario,
configuración de escenas, puertas, preferencias del jugador y tiempos de keepalive. Se conservó el protocolo
y el comportamiento del emulador.

`test_server.bat` compila y termina con
`PRIVATE SERVER WORLD-ENTRY PROTOCOL TEST PASSED`.
La prueba cubre autenticación con la versión, inventario y modelo,
Haven, movimiento a velocidad 5.0 y rotación, ida y vuelta al Training Hall,
armas, ataque, carga, defensa, esquiva y shield bash, sincronización de dos
clientes, cadena y cadencia de deltas y keepalive con timestamps válidos.
El registro está en `validation/test-protocol.log`. También se probó el cliente gráfico: autenticó, recibió Haven y comenzó a enviar input al servidor local. Ver `validation/compatibility-notes.md`.

`validation/server-before-adaptation.zip` conserva las fuentes anteriores.
Los registros compile-full.log y compile.log documentan el fallo original;
no representan el estado actual.

El Training Hall incorpora daño real contra sus 11 cubos originales, con espada,
proyectil de Polaris y bomba cargada de Ash of Agni. El controlador original gobierna
combos, carga, recuperación y controles. `test_server.bat` también ejecuta
`VANILLA COMBAT SIMULATION TEST PASSED`; evidencia en
`validation/test-authoritative-combat.log`. Ver [combate vanilla](docs/COMBATE_VANILLA.md).
Todavía faltan estados alterados, IA, daño al jugador, reacciones, autoapuntado,
desniveles y validación de todas las armas para afirmar paridad 1:1.
No hay progreso persistente.
El arsenal local reúne las 374 espadas, pistolas y bombas creables del catálogo
del cliente, a nivel 10, más cinco piezas de equipo. El rango máximo es 10-3,
con los permisos nativos de equipo de 2 a 5 estrellas habilitados.
Se pueden equipar y desequipar desde el cliente; yelmo y armadura usan sus ranuras
originales y el modelo de combate evita duplicar el escudo y el arma del sprite.
Combuster cargada genera cinco explosiones secundarias con daño real. La cadencia
de esa ráfaga usa compatibilidad local de 100 ms y aún necesita comparación vanilla.
Verificación actual: `validation/test-training-equipment.log`.
El movimiento resuelve paredes, tiles y objetos del mapa en Haven y
Training Hall, y las formas de los cubos vivos. Los cubos muertos dejan de bloquear.
Las balas usan su propia máscara de colisión y se detienen contra las paredes
que les corresponden; una barrera baja puede bloquear al jugador y permitir disparos.
Pruebas de colisión y permisos: `validation/test-clearance-collision-full.log`.
El feed social tampoco está implementado; sus paneles pueden mostrar errores. Para LAN/VPN consulta `server/README_LAN.md`.

## Reconstrucción por contratos

El [plan del servidor](docs/SERVER_SIDE_PLAN.md) organiza la adaptación del motor,
la validación con telemetría y los sistemas propios de Spiral Knights. Ver también
la [evaluación de Clyde](docs/CLYDE_COMPATIBILITY.md) y los
[casos locales de telemetría](docs/TELEMETRY_CASES.md).
