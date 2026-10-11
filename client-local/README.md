# Overlay de baja latencia

`launch_client.ps1` utiliza este overlay únicamente en loopback y comprueba
primero el SHA-256 de `code/projectx-pcode.jar` en `frozen-version.json`.
`-NetworkMode` conserva la política original en loopback. LAN/VPN no carga
estas clases. Los binarios congelados permanecen intactos.

`portable/BuildLocalClient.java` genera copias de TudeySceneConfig y
LevelSceneConfig. Cambia únicamente el operando del retorno de `CU()` a la
cadencia configurada (20 ms): respectivamente 100 y 33 ms en el original.
Rechaza otro bytecode. Conserva campos, exportación, serialización y los demás
métodos, incluida la predicción `ey(int)` de combate.

`src/com/threerings/tudey/util/q.java` conserva la API verificada del original
(constructor público y método estático `hd(int)`). Con la propiedad explícita
`spiral.local.lowLatency=true`, `hd` devuelve 0 para quitar la espera de render.
Con la propiedad desactivada conserva el cálculo original `2 * elapsed`.

El overlay de `com.threerings.tudey.a.a` elimina el suavizado de posición del
personaje controlado. En `ai(float)` el original interpola hacia la predicción
local con una semivida de 50 ms: aunque el input de movimiento ya esté apagado,
la imagen continúa avanzando hasta alcanzar esa posición. El generador verifica
el bloque completo de bytecode y sustituye sólo el cálculo de alpha por 1.
Mantiene los offsets, stack maps, predicción, reconciliación, flags y velocidad.
No modifica el movimiento de otros actores. Evidencia: `validation/stop-movement-notes.md`.

El servidor también debe negociar UpdateThrottleMessage: sin él, Presents
conserva el límite inicial de diez mensajes/s y acumula input a esta cadencia.
La configuración actual negocia 120 mensajes/s, dejando margen para RPCs y ping.

El overlay del director `com.threerings.tudey.a.i` reduce de 50 a 8 ms el
presupuesto de cada una de sus cuatro etapas de precarga (`tick`, `acF`, `acG`,
`acH`). El generador verifica sus cuatro instrucciones y cambia únicamente la
constante long compartida. No omite recursos: las listas pendientes se completan
en más tandas. Una carga individual aún puede exceder el presupuesto.

El lanzador utiliza G1 con objetivo de pausa de 20 ms y
`ExplicitGCInvokesConcurrent`. Las llamadas explícitas a System.gc() siguen
solicitando recolección; se atienden con un ciclo concurrente, evitando las
pausas completas observadas durante carga. El objetivo de 20 ms no es un límite
estricto y no cambia las preferencias gráficas del usuario.

Pruebas y mediciones: `validation/low-latency-notes.md`,
`validation/render-optimization-notes.md` y `validation/stop-movement-notes.md`.
