# Auditoría de acciones del arsenal

Versión 20261006114613. Análisis: 2026-10-11.

Se recorrieron los ataques normales y cargados de las **374 armas** creables:

- Bomb: 88.
- Handgun: 122.
- SwingingHandgun: 22.
- Sword: 142.

Se encontraron **71 tipos alcanzables**, sin referencias no resueltas
dentro de las familias recorridas. Esto es alcance estático de configuraciones;
no demuestra que el servidor ejecute esas acciones correctamente.

**126 armas** alcanzan nodos con actor nulo o condición Tagged vacía.
Son indicios de configuraciones incompletas o placeholders del cliente, no una
prueba de que vanilla contenga acciones inválidas. El importador aplica las
restauraciones locales ya existentes; no se presenta este informe como un dump
sin modificaciones del exportador original.

Advertencias de binding en esta ejecución: **3** ({'shape': 2, 'value': 1}).
Por tanto, resolver una referencia no garantiza que todos sus argumentos
hayan quedado aplicados. El log local conserva esos diagnósticos.

## Dependencias por familia

Cada cifra cuenta armas distintas que alcanzan el tipo, no instancias ni
frecuencias de ejecución. Una misma arma puede aparecer en varias filas.

| Tipo | Armas |
| --- | ---: |
| `DamageConfig$Compound` | 211 |
| `DamageConfig$DepthScale` | 346 |
| `DamageConfig$DepthScaleMulti` | 33 |
| `DamageConfig$ExclusiveStatus` | 41 |
| `DamageConfig$Normal` | 42 |
| `DamageConfig$Status` | 194 |
| `DamageConfig$Unblockable` | 2 |
| `DungeonActionConfig$ApplyStatus` | 3 |
| `DungeonActionConfig$DamageRegionFromPoint` | 374 |
| `DungeonActionConfig$FirePrefired` | 62 |
| `DungeonActionConfig$ImpartImpulse` | 19 |
| `DungeonActionConfig$MultiSpawnPrespawned` | 5 |
| `DungeonActionConfig$SpawnPrespawned` | 257 |
| `DungeonActionConfig$SpawnTransformedPrespawned` | 130 |
| `DungeonActionConfig$SpawnTransformedPrespawnedArgModify` | 2 |
| `DungeonActionConfig$TableSpawn` | 1 |
| `StatusConditionConfig$Curse` | 2 |
| `StatusConditionConfig$Default` | 4 |
| `StatusConditionConfig$Expires` | 9 |
| `StatusConditionConfig$Fire` | 41 |
| `StatusConditionConfig$Freeze` | 55 |
| `StatusConditionConfig$Poison` | 21 |
| `StatusConditionConfig$Shock` | 28 |
| `StatusConditionConfig$Sleep` | 4 |
| `StatusConditionConfig$Stun` | 68 |
| `ActionConfig$Compound` | 236 |
| `ActionConfig$Conditional` | 66 |
| `ActionConfig$Delayed` | 29 |
| `ActionConfig$FireEffect` | 118 |
| `ActionConfig$None` | 32 |
| `ActionConfig$Random` | 1 |
| `ActionConfig$SetVariable` | 32 |
| `ActionConfig$Signal` | 194 |
| `ActionConfig$SpawnActor` | 198 |
| `ActionConfig$SpawnRandomTranslatedActor` | 12 |
| `ActionConfig$SpawnRotatedActor` | 72 |
| `ActionConfig$SpawnTransformedActor` | 69 |
| `ConditionConfig$Tagged` | 66 |
| `ExpressionConfig$Constant` | 32 |
| `HandlerConfig$DefaultShape` | 12 |
| `HandlerConfig$Intersection` | 12 |
| `HandlerConfig$Shutdown` | 22 |
| `HandlerConfig$Signal` | 26 |
| `HandlerConfig$SignalStart` | 9 |
| `HandlerConfig$Startup` | 40 |
| `HandlerConfig$Timer` | 194 |

## Evidencia concreta de Combuster

La referencia cargada resuelta llega a Fire 3 con chance=0.5, power=60,
damageInterval=2000 ms, minDuration=4000 ms y maxDuration=10000 ms.
El contador restaurado recibe Constant("5"). La condición del temporizador
sigue siendo Tagged con tag vacío y existe un Startup con SpawnActor.actor nulo.
Estos datos no recuperan el algoritmo de probabilidad, resistencias, renovación
o propagación. Tampoco validan la cadencia local de ráfaga de 100 ms.

## Límites y reproducción

Se siguen referencias tipadas de AttackConfig, FireActionConfig, ActorConfig,
StatusConditionConfig y ParameterizedHandlerConfig, más objetos anidados.
No se siguen efectos de medios, IA, señales resueltas en runtime ni referencias
codificadas en cadenas. La deduplicación por identidad y referencia conserva
la primera ruta a un objeto: no es un listado de todos los caminos posibles.

El informe detallado, hashes de los catálogos y diagnósticos quedan locales en
analysis/client-20261006114613/combat-catalog.json y combat-import.log.

```powershell
& .\tools\reverse\reverse.ps1 -Action CombatAudit
# Usar el Python indicado en tools/reverse/README.md:
python .\tools\reverse\report_combat.py --root .
```

La validación comprueba las 374 entradas únicas, hashes, coherencia de conteos,
existencia de tipos en el índice JVM, resolución de referencias y los valores
concretos anteriores. No sustituye test_server.bat ni la comparación visual.
