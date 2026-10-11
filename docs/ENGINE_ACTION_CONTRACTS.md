# Contratos genéricos candidatos a adaptación

Referencia Clyde: 8b0c64bb4f37b0d9af6e6b154867ffd095f4cc01. Las fuentes originales
están en analysis/upstream/clyde/<revision> y sus URLs/hashes en clyde-reference.json.
Estos contratos describen esa revisión pública; no certifican la versión del
servidor propietario de Spiral Knights. Los esquemas públicos de instancia de las
clases comunes coinciden con el bytecode congelado, según la comparación inicial.

| Contrato | Semántica de la referencia | Requisito del adaptador |
| --- | --- | --- |
| None | Devuelve éxito sin efectos | Resultado explícito de acción |
| Compound | Ejecuta hijos en orden y acumula éxito con OR | Conservar orden y resultado; no reducir a una lista de efectos |
| Conditional | Evalúa condición, ejecuta action o elseAction; sin rama devuelve éxito | Resolver condición y rama alternativa con contexto |
| SetVariable | Resuelve objetivos, evalúa valor con activador y valor anterior, actualiza variable con timestamp/fuente | Variables por entidad, Previous y notificación de cambio |
| Timer | Primera espera=max(0, interval+offset); limit=0 equivale a límite máximo; dispara con reloj actual y reprograma intervalo | Scheduler de escena y cancelación al shutdown |
| SpawnRotatedActor | Rotación absoluta o relativa al objetivo, variación uniforme centrada y normalización angular | Resolución de objetivo y RNG controlable |
| SpawnTransformedActor | Rota la traslación local por la orientación del objetivo y la suma a su posición | Transformación local/mundo separada de la rotación del actor creado |

En Compound, stopOnFailure usa el éxito acumulado de la revisión examinada;
después de un primer éxito, un fallo posterior no vuelve falso ese acumulador.
Registrar este detalle en una prueba al adaptar: no asumir por el nombre del
campo que se comprueba únicamente el resultado del último hijo.

El temporizador de la referencia reprograma desde su ejecución, no exige recuperar
todos los disparos vencidos desde una rejilla temporal histórica. Nuestro Timer
actual incrementa next por interval; esa diferencia debe resolverse con una prueba
de retrasos y la política elegida para el reloj autoritativo, no cambiarse a ciegas.

La fuente y el activador son entidades diferentes. Una explosión heredada de un
proyectil necesita conservar propiedad y contexto del ataque; no reducir ambas
referencias a un actor ID compartido. El nacimiento del actor debe inicializar
su contexto antes de ejecutar Startup. Shutdown cancela los temporizadores.

Antes de reemplazar el ejecutor actual: pruebas de resultados/orden, condición
falsa y elseAction, variable anterior, temporizador con offset negativo/límite y
cancelación, rotación relativa/absoluta y traslación local con objetivo girado.
Después verificar snapshots/deltas y las regresiones de escena por TCP.
