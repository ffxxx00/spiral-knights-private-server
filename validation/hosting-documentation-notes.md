# Comprobación de la documentación de hosting

Fecha: 2026-10-10. Copia independiente Windows x64, datos 20261006114613.

- `docs/verificar-copia.ps1` ejecutado mediante Windows PowerShell: salida 0, ocho SHA-256 correctos, backend FFM presente y lanzador actualizado.
- `test_server.bat` ejecutado desde la copia privada: salida 0 y marcador `PRIVATE SERVER WORLD-ENTRY PROTOCOL TEST PASSED`. Registro: `test-hosting-documentation.log`. Incluye dos clientes y regresión de ACK/estado al soltar una tecla.
- Once bloques PowerShell de las guías analizados con el parser de Windows PowerShell, sin errores. Sólo se analizó la sintaxis de las reglas; no se aplicó ninguna regla de firewall.
- Manual HTML revisado en el navegador: portada, índice y navegación al capítulo del anfitrión. Ancho de página igual al viewport (1265 px), sin desbordamiento horizontal de la página.
- Ocho documentos, nueve tablas y 27 bloques preformateados en el manual. Comprobados enlaces locales, anclas y CRC del ZIP con `build-hosting-docs.py`; detalle en `hosting-documentation-checks.json`.
- Servidor HTTP temporal de revisión limitado a 127.0.0.1:48038, cerrado al finalizar. Puerto de regresión 47625 también liberado. No se abrió un servicio web permanente.

La conexión de otra PC, Tailscale y las reglas de firewall del destinatario no se probaron en su red. Las guías incluyen sus comprobaciones de instalación. El ZIP entregable contiene sólo documentos y el verificador; no contiene recursos del juego ni preferencias personales.
