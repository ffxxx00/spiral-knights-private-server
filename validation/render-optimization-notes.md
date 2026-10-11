# Reducción de tirones de carga — 9 de octubre de 2026

Equipo observado: Intel Celeron N5100, 4 procesadores lógicos, 3,8 GiB de RAM,
Intel HD Graphics Gen11. Se conservaron las preferencias gráficas existentes.

## Causas verificadas

El director Tudey de la biblioteca dedica 50 ms a cada una de cuatro
etapas de precarga. `director-bytecode.txt` confirma `tick(float)`, `acF()`,
`acG()` y `acH()`, todas referenciando la misma constante long.
JFR y los logs de GC también registraron llamadas explícitas a System.gc() con
pausas completas de 111–217 ms; el máximo de todas las pausas fue 219,80 ms.

## Cambios

- Overlay del director: presupuesto de precarga de 8 ms por etapa. Conserva
  las listas de recursos y su proceso de carga, sin sustituir ni omitir assets.
- Lanzador: G1, objetivo de pausa de 20 ms y ExplicitGCInvokesConcurrent.
  Conserva las solicitudes de limpieza, atendidas mediante ciclos concurrentes.
- No se cambió el heap máximo, la velocidad, controles ni la calidad gráfica.

## Comparación

Los dos sondeos comparativos usaron el mismo JDK incluido (Temurin 25.0.3),
cliente, perfil, servidor y visita a Training Hall. El runtime habitual
del cliente (Java 25.0.1 reducido) no contiene JFR. Un sondeo separado con ese
runtime registró Haven y la primera ventana de Training Hall con telemetría
gráfica y logs de GC; ese sondeo se interrumpió antes de completar 70 segundos.

| Medición de carga | Antes | Después |
| --- | --- | --- |
| Haven, primera ventana de 5 s | 12,0 FPS; P95 199,17 ms | 27,1 FPS; P95 90,97 ms |
| Haven, siguiente ventana | 10,9 FPS; P95 194,66 ms | 33,4 FPS; P95 71,02 ms |
| Training, primera ventana | 10,0 FPS; P95 165,93 ms | 29,4 FPS; P95 63,98 ms |
| Pausa GC máxima del ensayo completo | 219,80 ms | 33,91 ms |
| Tiempo acumulado de pausa GC | 1413,84 ms | 781,93 ms |

En escenas ya cargadas ambos ensayos alcanzaron aproximadamente 54–57 FPS.
La mejora se concentra en la carga y las pausas. Una carga individual, el sistema
operativo, la GPU y el procesamiento aún pueden causar tirones; los presupuestos
son objetivos, no límites físicos garantizados. En el ensayo posterior no hubo
Pause Full y las colas temporales de mensajes volvieron a 0–1.

Evidencia: `render-before-client.log`, `render-after-client.log`,
`render-before-gc.log`, `render-after-gc.log`, `render-before-profile.txt`,
`render-after-profile.txt` y los JFR correspondientes. El ensayo con el runtime
habitual se registra en `render-runtime-client.log` y `render-runtime-gc.log`.

`CheckFrozenOverlay` compara las clases generadas byte por byte con la biblioteca:
estas tres cambian exactamente un operando; todos sus demás bytes permanecen
intactos (`test-frozen-overlay.log`). También comprueba por separado el ajuste
posterior del suavizado del personaje local, documentado en
`stop-movement-notes.md`. El generador rechaza contratos distintos.
Las bibliotecas no se modificaron.

Prueba integral final, después del ajuste de detención: `test-stop-movement.log`
y `test-stop-movement-overlay.log`. La versión anterior del lanzador
y generador está en `before-render-optimization.zip`.
