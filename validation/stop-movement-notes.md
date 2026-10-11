# Detención al soltar la tecla — 10 de octubre de 2026

## Causas y correcciones

El cliente congelado detiene la predicción al recibir keyReleased, pero el
controlador `com.threerings.tudey.a.a.ai(float)` interpola la posición visible
hacia esa predicción con `1 - exp(20 * log(0.5) * dt)`: semivida de 50 ms.
La imagen continúa desplazándose durante varios fotogramas aun con moving=false.
El overlay local conserva la interpolación Vector2f.d, usando alpha=1.
El generador verifica el bloque completo en los offsets 397..429 y sustituye
sólo la expresión por fconst_1 y nops, conservando offsets y stack maps.
La velocidad, flags, integración, reconciliación y otros actores no cambian.

La prueba integral también reprodujo una carrera del servidor: llegaba input
entre la captura compartida de actores y su publicación. El ACK podía confirmar
el input nuevo junto con la posición anterior. `TickSnapshot` captura el actor
y el timestamp de input bajo el mismo bloqueo; la publicación confirma sólo
el input incluido en esa captura. La regresión determinista cubre llegada de
movimiento y de key release entre ambos pasos. No se amplió ni relajó el criterio
de la prueba de dos clientes que detectó el fallo.

## Prueba gráfica real

`MeasureStop` usa el runtime habitual java_vm (25.0.1), el perfil local, Haven
y el dispatcher de teclado real de la raíz GUI del cliente. Espera la carga,
pulsa W cinco veces, suelta la tecla y observa cada fotograma durante al menos
600 ms por ensayo. No modifica la velocidad ni inserta frames de protocolo.
La línea inputClass confirma que el ensayo posterior cargó el overlay.

El ensayo anterior antepone la clase original a.class, extraída del JAR congelado
a `validation/baseline-client-classes`, mediante `measure-stop.ps1 -Baseline`.
La comparación usa visualGap (distancia visible-predicción en el instante de
soltar) y las coordenadas registradas. La columna forward del primer sondeo
usaba el ángulo del actor; el sondeo final usa la dirección del desplazamiento
real. Esa columna anterior no se usa como medida comparativa.

| Ensayo | Separación visible-predicha antes (unidades) | Después |
| --- | --- | --- |
| 1 | 0,36398 | 0,00000 |
| 2 | 0,01796 | 0,00000 |
| 3 | 0,06375 | 0,00000 |
| 4 | 0,07369 | 0,00000 |
| 5 | 0,02603 | 0,00000 |

En los cinco ensayos posteriores, inputFlags=0, moving=false, avance residual
máximo=0,00000, y posición visible/predicha estable. Se verificó que cada tecla
sí desplazara el personaje antes de soltar. La prueba exige separación y avance
menores de 0,0001 unidades; el log muestra cinco decimales. Esto verifica la
detención en el escenario probado; no es una medida de latencia física de 0 ms.

Evidencia: `stop-before-client.log`, `stop-after-client.log`, logs de servidor
correspondientes y `MeasureStop.java`. Los procesos usan sólo localhost:48024
y la copia congelada independiente.

## Regresiones

- `test_server.bat`: aprobado en `test-stop-movement.log`, incluyendo login,
  Haven, Training Hall y retorno, velocidad 5, detención, combate, dos clientes
  y keepalive; respuesta de movimiento observada: 11 ms.
- La misma integral con el overlay y lowLatency=true: aprobada en
  `test-stop-movement-overlay.log`.
- Captura/ACK con input nuevo entre captura y publicación, incluida detención:
  aprobada dentro de ambas integrales.
- `CheckFrozenOverlay`: las cuatro clases sólo cambian sus sitios previstos;
  todos los demás bytes intactos (`test-frozen-overlay.log`).
- Los ocho SHA-256 de frozen-version.json coinciden (`test-frozen-stop.log`).

El overlay del cliente se aplica sólo en loopback sin NetworkMode. El ajuste
del ACK se aplica al servidor en todos los transportes. Para activar ambos,
reiniciar el servidor privado y el cliente desde esta carpeta.
