# Herramientas de análisis de la copia congelada

Vineflower 1.12.0 se descarga desde su release oficial, con SHA-256 fijado en
toolchain.json. Se usa el JDK propio del proyecto. Ghidra no es necesario para
este análisis de bytecode Java. No se actualizan datos ni bibliotecas del juego.

Desde la raíz de la copia privada, PowerShell:

```powershell
& .\tools\reverse\reverse.ps1 -Action Setup
& .\tools\reverse\reverse.ps1 -Action Decompile
& .\tools\reverse\reverse.ps1 -Action Index
& .\tools\reverse\reverse.ps1 -Action Query -QueryArgs @('stats')
& .\tools\reverse\reverse.ps1 -Action Query -QueryArgs @('find','DamageConfig')
& .\tools\reverse\reverse.ps1 -Action Query -QueryArgs @('callers','com.threerings.projectx.data.PlayerObject','--method','dN','--limit','10')
& .\tools\reverse\reverse.ps1 -Action Query -QueryArgs @('field-uses','com.threerings.projectx.data.PlayerObject','--method','progressFlags')
& .\tools\reverse\reverse.ps1 -Action Query -QueryArgs @('find','m.rank_limit','--kind','strings')
```

El índice y la descompilación por lotes requieren Python 3.10+ y solo su biblioteca estándar. En esta máquina
se detecta el Python incluido en las dependencias de Codex. En otra máquina puede
indicarse `-Python 'C:\ruta\python.exe'`. Java y el cliente no dependen de Python.

Los artefactos locales se guardan en `analysis/client-<versión>/`:

- `sources/`: Java descompilado del JAR principal, incluidas sus bibliotecas integradas.
- `index.sqlite`: clases, herencia/interfaces, métodos y descriptores, campos,
  llamadas, accesos a campos, referencias de clases y literales de los JAR de code/.
- `inventory.json`: hashes, conteos, paquetes y cobertura de navegación.
- `decompiler-errors.json`: ubicaciones de cuerpos que no pudieron descompilarse.
- `decompile.log`: advertencias y errores originales de Vineflower.

Estos artefactos y el JAR descargado están excluidos de Git. Se versionan las
herramientas propias, el manifiesto de herramienta y los informes escritos para
el proyecto. La presencia de una fuente no demuestra que todos sus métodos se
hayan reconstruido correctamente; comprobar los marcadores y el log.

La descompilación tiene caché por lote y hash del JAR, herramienta y opciones.
Usa lotes de unas 350 clases, heap máximo de 768 MB y dos hilos; se puede reanudar
después de una interrupción. `-Force` repite el proceso. Regenerar el índice después
de terminar la descompilación. El índice reutiliza el bytecode si sus hashes y el
lector coinciden, y actualiza solo la navegación de fuentes y diagnósticos.
Las consultas son de solo lectura y muestran como máximo 20 filas por defecto.
`--limit` admite hasta 200. Usar clase, método y descriptor para evitar confundir
nombres ofuscados sobrecargados. `class` muestra la estructura de una clase exacta.

El grafo contiene destinos simbólicos de instrucciones JVM: no resuelve todos
los overrides virtuales, la reflexión, callbacks ni mensajes de red. Las aristas
`dynamic_bootstrap` describen factories/handles, no prueban que se ejecuten.
Las referencias de clase provienen del constant pool, no equivalen a llamadas.

Para verificar una conclusión:

```powershell
& .\jdk\bin\javap.exe -classpath code/projectx-pcode.jar -c -p com.threerings.projectx.data.PlayerObject
```

No copiar una fuente descompilada directamente al emulador sin verificar el
contrato. Los catálogos se inspeccionan con BinaryImporter/InstalledConfigManager;
no se ejecutan clases del juego para construir el índice. Ver docs/REVERSE_MAP.md.

Para el grafo de combate de todas las armas, ejecutar `-Action CombatAudit`.
Esta acción sí importa los catálogos con el importador del servidor local; no
inicia escenas, sockets ni el juego. Compila el auditor y sus dependencias en
analysis/client-<versión>/audit-classes, sin sustituir los .class del servidor.
Guarda combat-catalog.json y combat-import.log únicamente en analysis/.
Después ejecutar `report_combat.py --root .` con el Python detectado arriba para
validar datos y generar docs/COMBAT_ACTION_AUDIT.md. Requiere el índice ya creado.
La auditoría sigue referencias tipadas y nodos anidados de acciones; no mide su
ejecución ni demuestra paridad. Incluye hashes de los catálogos importados.

## Telemetría y referencia de Clyde

Con el mismo Python utilizado por el índice:

```powershell
python .\tools\reverse\check_clyde.py --download
python .\tools\reverse\test_telemetry_cases.py
python .\tools\reverse\telemetry_cases.py normalize 'C:\ruta\mission-capture.jsonl' analysis/telemetry/capture
python .\tools\reverse\telemetry_cases.py compare analysis/expected.json analysis/actual.json
```

check_clyde.py descarga solo los ocho archivos fijados en clyde-reference.json,
verifica SHA-256 y compara campos públicos de instancia contra el índice JVM.
No compila ni instala el motor externo. El detalle queda en analysis/upstream/;
el resumen propio se guarda en docs/CLYDE_COMPATIBILITY.md.

normalize admite el JSONL de MissionRecorder y conserva una copia local por hash.
No ejecuta grabadores ni agentes. Los deltas reflectivos sin identidad de clase
se conservan opacos y se indican bloqueos para reproducción; no se inventa un
estado de actor aplicando valores a campos desconocidos.

compare recibe muestras autoritativas del mismo experimento, con hashes de inputs,
estado inicial y cliente. Devuelve diferencias y exit code 1 cuando no coinciden;
rechaza casos incompatibles. Todavía requiere conectar el ejecutor del servidor.
Ver docs/TELEMETRY_CASES.md y docs/SERVER_SIDE_PLAN.md.
