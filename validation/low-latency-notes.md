# Baja latencia comprobada el 9 de octubre de 2026

Todos los sondeos utilizaron exclusivamente la copia privada.
El sondeo gráfico ejecutó el cliente real, midió su reloj de render y FPS,
y solicitó Training Hall mediante el SceneDirector original. Su duración es
90 segundos; el sondeo se cierra solo. No modifica el comportamiento de juego.

## Resultado

| Medida | Antes | Modo local |
| --- | --- | --- |
| Tick Haven | 100 ms | 20 ms |
| Tick Training Hall | 200 ms | 20 ms |
| Buffer de render Haven | 200 ms | 0 ms |
| Buffer de render Training Hall | 400 ms | 0 ms |
| Input Haven / Training | 100 / 33 ms | 20 / 20 ms |
| Presupuesto de envío del cliente | 10 mensajes/s | 120 mensajes/s |

Ping/Pong TCP real, incluyendo procesamiento y la cola del servidor, 200 muestras
después de 30 de calentamiento: mínimo 0,407 ms; mediana 0,733 ms; P95 2,441 ms.
Ver `latency-rtt.log` y `MeasureLoopback.java`. No se falsificó el ping.
La prueba integral midió 15 ms desde enviar movimiento hasta recibir su estado
autoritativo (`test-low-latency.log`). Los FPS, el tick y el procesamiento siguen
añadiendo tiempo; no existe una garantía física de 0 ms.

El ensayo inicial a 50 Hz descubrió una cola de controles en el cliente: su
límite inicial de 10 mensajes/s quedó por debajo de la frecuencia de input.
Se resolvió con UpdateThrottleMessage, cuya firma y manejo se verificaron con
javap. En el ensayo final, los ACK no acumularon atraso: típicamente 0–40 ms;
las colas de salida permanecieron vacías o con un mensaje. No se bloqueó el tick
por ACK ni se descartó la cadena de deltas.

El gráfico final sostuvo aproximadamente 46–56 FPS en Haven y 39–52 FPS en
Training Hall, con movimiento y controles de combate, después de cargar recursos.
Durante carga hubo pausas y acumulación transitoria que luego se drenó. No se
afirma que se eliminaran todas las pausas de carga o los límites de la GPU.
Ver `latency-baseline-client.log`, `latency-verified-client.log` y
`latency-verified-server.log`. El feed social ausente conserva su error previo.

## Validación

- `test_server.bat`: login, preferencias, inventario, Haven, velocidad, rotación,
  Training Hall y regreso, combate, cadena/cadencia, dos clientes y keepalive.
- La misma prueba con `client-local/classes` primero en el classpath para
  comprobar exportación/serialización de las escenas con el overlay.
- `CheckLocalClient`: clases originales 100/33 ms y buffer 40 ms para elapsed=20;
  overlay 20/20 ms y buffer 0 con la propiedad; buffer 40 al desactivarla.
- SHA-256 de todos los archivos enumerados en `frozen-version.json`: intactos.

Registros: `test-low-latency.log`, `test-low-latency-overlay.log` y
`test-local-client-policy.log`. La copia previa a estos cambios se conserva
en `before-low-latency.zip`.
