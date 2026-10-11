# Preparación de telemetría y referencia del motor — 2026-10-11

Se consultaron exclusivamente archivos de telemetría existentes en tools/ de
Steam con autorización del usuario. No se ejecutaron agentes ni bots ni se
conectó al juego oficial. La captura MissionRecorder fue copiada por hash a
analysis/telemetry/axes-of-evil/ dentro del proyecto privado.

La normalización produjo cinco segmentos, preservando relojes originales, IDs,
altas/bajas/efectos y deltas reflectivos opacos. De 1.783 frames enviados quedaron
204 y 569 únicos en los segmentos con inputs. Todos permanecen bloqueados para
reproducción: falta vincular versión, assets y estado completo; también existen
truncamientos y diagnósticos de continuidad. Estos resultados no equivalen a una
simulación de la misión.

Se fijó Clyde en 8b0c64bb4f37b0d9af6e6b154867ffd095f4cc01 y se verificaron SHA-256
de Action/Condition/Expression/Handler Config y Logic. El scanner compara nombres
y tipos de campos públicos de instancia con el índice del bytecode.
Resultado: 37, 20, 31 y 28 clases comunes respectivamente, sin diferencias de
campos en esas clases. Action tiene además un enum y una interfaz del cliente
fuera del conjunto de declaraciones de clase del scanner. Esto no verifica
métodos, herencia, genéricos, defaults, dependencias ni semántica de ejecución.

Las pruebas controladas verifican: tolerancia de posición, diferencias de vida,
apariciones inesperadas, tiempos, identidades de experimento, muestras ausentes,
IDs duplicados, orden/deduplicación del input, límites de escena y conservación
de deltas truncados. El scanner tiene una prueba con clases anidadas, campos
múltiples, comentarios, strings con llaves y exclusión de campos estáticos.

No se modificó el runtime del servidor/cliente. La comparación real contra
CombatWorld queda pendiente de un ejecutor de casos con estado inicial completo.
Las fuentes externas, capturas y resultados detallados permanecen fuera de Git.
