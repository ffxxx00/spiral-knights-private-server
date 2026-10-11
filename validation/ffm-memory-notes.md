# Corrección de memoria LWJGL — 10 de octubre de 2026

El aviso original provenía de `MemoryBackendUnsafeLegacy.objectFieldOffset`,
al inicializar el backend de memoria de LWJGL con Java 25. El ajuste anterior
`--sun-misc-unsafe-memory-access=allow` sólo desactivaba el aviso; fue retirado.

El lanzador ahora selecciona `org.lwjgl.system.memoryBackend=ffm`. El JAR
congelado ya contiene `MemoryBackendFFM` en META-INF/versions/25; no se descargó
ni actualizó ninguna biblioteca. Esta implementación usa java.lang.foreign
para acceder a memoria nativa, en lugar de las operaciones sun.misc.Unsafe.
Se conserva Java 25.0.1 y los avisos de la JVM permanecen habilitados.

Referencia del fabricante: [Configuración de backends de LWJGL](https://javadoc.lwjgl.org/org/lwjgl/system/Configuration.html#MEMORY_BACKEND).
El contrato local se confirmó con javap --multi-release 25 sobre code/lwjgl.jar
(`lwjgl-ffm-bytecode.txt` y `lwjgl-memory-util-bytecode.txt`).

## Verificación

- `UnsafeMemoryProbe` se ejecutó con el Java habitual del cliente y
  `--sun-misc-unsafe-memory-access=deny`. Confirma la clase activa
  `org.lwjgl.system.MemoryBackendFFM$P64CL32`; comprueba reserva/liberación,
  direcciones, alias de buffers, vistas tipadas, copias de arrays, UTF8,
  MemoryStack y PointerBuffer. Aprobado en `unsafe-ffm-memory.log`.
- El sondeo gráfico `launch-ffm-probe.ps1`, derivado del lanzador actualizado,
  también utiliza deny. Se conecta sólo a un servidor de prueba propio en
  localhost:48024, con configuración y preferencias separadas. Confirma FFM,
  audio OpenAL, render Intel HD Graphics Gen11, Haven y Training Hall.
  Evidencia en `ffm-client.log`, `ffm-client-errors.log` y `ffm-server.log`.
  No se registraron avisos de objectFieldOffset ni excepciones de memoria Unsafe.
- `test_server.bat` aprobado en `test-ffm-backend.log`: login, escenas,
  movimiento, combate, dos clientes y keepalive.
- Sintaxis del lanzador verificada con el parser de PowerShell.

El sondeo gráfico se ejecutó mientras estaba abierto otro cliente del usuario;
sus FPS no son una comparación de rendimiento. Un aviso existente sobre una
ventana GUI no administrada al entrar a Training Hall se conserva en el log.
No se filtra stderr ni se modifica la política de avisos del cliente habitual.

SHA-256 de code/lwjgl.jar:
46EECA5471833C3CF5DA3C1DA015B41E3BB3EB16DD3DA03F366886DA10096751.
Los recursos, las clases de juego y los ajustes de movimiento permanecen intactos.
El cambio se activa al reiniciar el cliente con launch_client.bat.
