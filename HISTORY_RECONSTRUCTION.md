# Historial reconstruido del servidor privado

No existia un repositorio Git en esta carpeta. Estos commits fueron creados al
importar respaldos el 11 de octubre de 2026, con la identidad Git configurada.
No son commits originales recuperados ni tienen fechas de autoria retroactivas.
La primera copia disponible ya contiene desarrollo; no permite recuperar el
trabajo anterior a ese respaldo. Los timestamps ZIP son indicios locales,
no fechas verificadas de autoria.

Los cuatro primeros commits recuperan entradas exactas de respaldos. El tercero
solo conserva tres archivos y arrastra los restantes del estado anterior.
Los siguientes agrupan fuentes actuales por funcion, sin pretender reproducir
cada cambio intermedio ni el orden exacto de todo el desarrollo.

Los commits historicos pueden carecer de auxiliares o ser incompatibles con
la biblioteca. No se declara que cada commit historico compile ni
pase las pruebas. Para trabajar, usar el ultimo commit y los recursos indicados
en README_GITHUB.md. La reconstruccion no modifico fuentes ni archivos del juego.

## Etapas y evidencia

- `af18bd36f099` — reconstruct: importar servidor anterior a la adaptacion

  Fuentes exactas de server-before-adaptation.zip. Es la primera copia disponible, no prueba de que sea la primera version creada. Puede no compilar con el cliente.

- `0a602c5da5b7` — reconstruct: adaptar protocolo al cliente

  Entradas exactas de before-low-latency.zip. Estado previo a la baja latencia. Este respaldo no contiene todos los archivos necesarios para ejecutar el cliente.

- `b578a72a6a2e` — reconstruct: recuperar primer ajuste de latencia local

  Tres archivos exactos de before-render-optimization.zip; el resto conserva el respaldo anterior. Es una reconstruccion parcial, no un snapshot integral probado.

- `38025eef6247` — reconstruct: recuperar servidor previo al combate autoritativo

  Fuentes y configuracion exactas de before-authoritative-combat.zip. Agrupa ajustes posteriores de red y controles sin inventar snapshots intermedios.

- `8353417bdc53` — reconstruct: consolidar copia portable y cliente local optimizado

  Archivos actuales: aislamiento de preferencias, lanzadores, congelacion, overlay local, renderizado, detencion del movimiento y backend FFM. Sin respaldos completos para separar con exactitud cada correccion del cliente.

- `8a074ae2f03b` — reconstruct: implementar combate autoritativo y regresiones

  Diferencia entre el respaldo previo al combate y las fuentes actuales. Incluye controlador vanilla, cubos, dano, proyectiles, bombas y pruebas. Paridad incompleta y comprobacion visual pendiente; ver COMBATE_VANILLA.md.

- `ac338fa46b55` — reconstruct: documentar hosting Windows y evidencia de validacion

  Documentacion y herramientas actuales. Las notas describen pruebas historicas; los logs, dumps, recursos, perfiles y binarios referenciados no se distribuyen.

Los SHA-256 de respaldos y entradas estan en docs/HISTORY_EVIDENCE.json.
Los ZIP originales permanecen locales en validation/ y no entran al repositorio.
El manifiesto permite comparar las fuentes actuales con el estado importado.
El ultimo commit agrega esta trazabilidad y las reglas de inclusion de archivos.

Los futuros cambios se registraran como commits normales, sin reconstruccion.
