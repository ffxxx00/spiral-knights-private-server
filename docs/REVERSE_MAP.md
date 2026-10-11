# Mapa del cliente congelado y ruta de implementación

Versión de datos: **20261006114613**. Análisis: **2026-10-11**.

Este documento organiza la exploración previa a los refactores. El inventario
global es automático; los contratos profundizados y las incertidumbres se
registran por separado. No significa que se haya recuperado el servidor original
ni que se conozca cada sistema vanilla por completo.

## Inventario y navegación

- JAR principal: **8.327 clases**, incluidas clases internas y bibliotecas integradas.
- Todos los JAR de `code/`: **10.052 entradas de clase**, **76.805 métodos**,
  **38.628 campos** y **215.485 aristas simbólicas de llamada**. Las variantes
  multi-release son entradas distintas; no son 10.052 clases únicas en ejecución.
- **52 archivos .dat** en `rsrc/config/` y **96 archivos** en `scenes/`.
  Esto cuenta archivos locales, no todas las misiones o mapas posibles.
- Metadatos y cobertura: [REVERSE_INVENTORY.json](REVERSE_INVENTORY.json).
- Herramientas: [procedimiento reproducible](../tools/reverse/README.md).
- Código descompilado e índice: `analysis/client-20261006114613/`, solo locales.

Los 24 lotes produjeron **4.435 archivos Java**, con navegación a fuentes para
las **8.327 clases** del JAR principal. Dos cuerpos de método de `KnightSprite`
no pudieron reconstruirse; su bytecode está disponible localmente. El log conserva
366 advertencias y 53 errores del descompilador. La cobertura de navegación no
equivale a reconstrucción correcta de todos los métodos. Ver
[validación de las herramientas](../validation/reverse-toolchain-notes.md).

El índice conserva clase, método, descriptor JVM y offset de instrucción. La
unidad de identidad es esa combinación, no un nombre como `a` o `b`. La herencia,
los callbacks y la reflexión deben seguirse además de la llamada simbólica.
La descompilación aporta navegación; el bytecode y los datos son la evidencia.

## Mapa general

Los conteos siguientes proceden del índice del JAR principal e incluyen clases
internas. Los paquetes nombrados son localizadores; no prueban que cada función
esté disponible o implementada en el emulador.

| Sistema | Paquetes ProjectX y clases | Contrato/dependencias | Estado local |
|---|---|---|---|
| Arranque e interfaz | client 595, config 131, data 110 | ProjectXApp, bootstrap, PlayerObject, preferencias, objetos compartidos | Entrada de mundo probada; muchas interfaces/servicios aún sin implementar |
| Combate y mazmorras | dungeon 846 | Input → controlador → Attack/FireAction/Damage/Actor → deltas; incluye arena | Contrato de controles y combate básico documentado; estados, IA y daño entrante pendientes |
| Inventario y equipo | item 248 | Item/LevelItem, slots, permisos, binding, marshaller, modelos | Equipo, arsenal y permisos probados; binding completo y persistencia pendientes |
| Rango y misiones | mission 82, reward 7 | RankPair, progressFlags, objetivos, MissionObject/Marshaller, recompensas | Rango/permisos locales; campañas y progreso persistente ausentes |
| Ciudades y mapas | town 23, map 8, building 3 | TownScene, escenas Tudey, portales, geometría, interacción | Haven/Training probados; no se han habilitado todas las escenas |
| Fabricación y diseño | craft 104, design 72 | Recetas, requisitos, consumo de materiales, LevelItem resultante | Solo localizados; falta reconstruir transacciones y validar resultados |
| Sprites | sprites 50 | BattleSprite, harness, configuración y progresión | Tipos localizados; su sistema de combate/progreso no implementado |
| PvP | pvp 48; arena dentro de dungeon | Party, equipos, reglas y ciclo de partida | Localizado; no implementado |
| Economía | auction 59, exchange 72, shop 66, trade 41 | Servicios, inventario, divisas y confirmación de transacciones | Localizados; no se debe interpretar una confirmación genérica como transacción completa |
| Eventos y juegos adicionales | casino 27, lottery 10, event 13 | Configuraciones, objetos y servicios específicos | Localizados; contratos por estudiar |
| Social y comunidad | social 95, guild 146, love 46, board 7, broadcast 1, uplink 66 | Notificaciones, chats, membresías, objetos y marshallers | Paneles/contratos localizados; cobertura del emulador parcial |
| Integraciones | billing 3, partner 18 | Puentes y servicios externos | Inventariados; fuera del funcionamiento privado sin servicios oficiales |
| Administración/editor | admin 147, tools 85 | Configuración, inspectores, herramientas y servicios admin | Referencias útiles para interpretar datos; no equivalen a un backend recuperado |
| Apoyo | effect 3, expr 3, util 43 | Expresiones, efectos, utilidades | Navegación disponible; seguir dependencias por sistema |
| Paquetes ofuscados sin semántica confirmada | a 5, b 2 | Falta seguir consumidores/descriptores | No asignar significado por el nombre |

Todos los grupos ProjectX del inventario quedan representados en esta tabla.
Los detalles por subpaquete están en el inventario local; los conteos no son una
medida de porcentaje de paridad funcional.

## Motor y dependencias

| Capa | Evidencia del inventario principal | Responsabilidad relevante |
|---|---|---|
| Presents / IO / Crowd | 278 / 77 / 125 clases | Transporte, InvocationMarshaller, DObjects, ocupantes y serialización |
| Tudey | 523 clases | Escena, actores, geometría, movimiento, InputFrame y SceneDeltaEvent |
| Config / Export / Expr | 123 / 107 / 202 clases | ConfigReference, parámetros, importación binaria y expresiones |
| OpenGL / OpenAL | 1.090 / 79 clases | Modelos, sprites, interfaz y sonido; no sustituir animación por simulación |
| Math / Delta | 23 / 31 clases | Transformaciones, formas, comparación y deltas de estado |
| Guava / Guice | 692 / 453 clases | Bibliotecas integradas; menor prioridad para reconstruir reglas del juego |

Las bibliotecas LWJGL y Commons externas también se indexan. Se descompila el JAR
principal; no se descompilan todas las bibliotecas externas ni los binarios
nativos. Su análisis profundo solo es necesario si una dependencia concreta lo requiere.

## Flujos profundizados

### Sesión, escenas y reloj

`AuthRequest → BootstrapNotification → PlayerObject → selección → ZoneMarshaller
→ TudeySceneModel → suscripción → SceneDeltaEvent → InputEnqueueEvent`.

El emulador corresponde a `ClientSession`, `SceneManager`, `DObjectManager` y
`SceneNetworkManager`. Los actores se publican por snapshots y ActorDelta; el
reloj autoritativo de escena no debe seguir el reloj predictivo enviado por el
cliente. Confirmar input y transmitir estado son frecuencias distintas.

Evidencia: `server/TestEmulatorProtocol.java`, `validation/low-latency-notes.md`
y `validation/stop-movement-notes.md`. Falta revisar cada servicio que hoy usa
una respuesta genérica: devolver confirmación no demuestra que haya cambiado
el estado, persistido una transacción o enviado todas sus notificaciones.

### Equipo y permisos

`item.client.M / ItemConfig.Level / LoadoutPanel → PlayerObject.dN(rarity)
→ PlayerObject.dP(mask) → progressFlags`.

La consulta `callers PlayerObject --method dN` identifica siete instrucciones
de llamada, incluidas interfaz, loadout y LevelItem. El método no consulta el
rango visible para los permisos de 2..5 estrellas: usa bits 64/128/256/512.
Esto explica el defecto anterior y muestra por qué el contrato debe incluir
todos los consumidores, no solo la pantalla de personaje.

`ItemMarshaller → move → validación de ranuras → equipment/quickbar
→ PartyMember → modelo/arma/escudo del actor`. Yelmo 0, armadura 1, escudo 2,
armas 6..9. Los números de método del marshaller se verifican con bytecode;
los códigos de servicio enlazados por este emulador no son constantes universales.

Evidencia: `validation/clearance-collision-notes.md` y
`validation/training-equipment-notes.md`; pruebas directas y por TCP. Falta
documentar completamente binding, loadouts, variantes, accesorios y persistencia.

### Movimiento y colisiones

`DungeonInputFrame → dungeon.util.i → tudey.util.j/b → Mobile
→ contexto de colisión → TudeySceneModel y actores sólidos`.

La integración original subdivide a 60 Hz y resuelve penetración con hasta tres
pasadas. El mapa debe inicializarse con tile/placeable/path; devolver false en
las consultas de contexto convierte cualquier escena en atravesable. `_maxStep`
en cero también anulaba desplazamientos aunque el flag de caminar estuviera activo.

Actor.e/gK/Nz permiten seguir máscara, flags y condiciones del actor. Los cubos
usan su forma congelada; el emulador libera el paso al morir. Las balas tienen
otra máscara: Polaris usa 137 frente a 43 del jugador. Una barrera baja no tiene
por qué bloquear disparos. No convertir todas las colisiones en un mismo booleano.

Evidencia: `server/SceneCollision.java`, TestCombatSimulation y
`validation/clearance-collision-notes.md`. Faltan validación de desniveles,
reacciones/empuje y comparación visual; no se agregó bloqueo entre jugadores.

### Combate y estados

`dungeon.util.i → AttackConfig → FireActionConfig → ActionConfig /
DungeonActionConfig → DamageConfig → Actor/Monster y su estado replicado`.

El controlador instalado aporta combo/carga/recuperación/defensa/esquiva. Las
acciones que producen efectos de mundo requieren implementación autoritativa.
CombatWorld interpreta un subconjunto; no hay que extenderlo con casos por
nombre de arma si el catálogo declara una acción o condición común.

La [auditoría del arsenal](COMBAT_ACTION_AUDIT.md) sigue las 374 armas creables:
71 tipos alcanzables y 126 armas con indicios de nodos incompletos del cliente.
194 armas alcanzan DamageConfig.Status. La referencia concreta de Combuster llega
a Fire 3 con intervalo de 2.000 ms y duración declarada de 4.000 a 10.000 ms.
No confundir estos datos con el algoritmo autoritativo de aplicación de estados.
También se detectaron tres advertencias de binding; resolución de referencias y
aplicación de parámetros son comprobaciones distintas.

El índice separa DamageConfig y sus subtipos. `DamageConfig.Status` contiene
flags y una referencia statusCondition; sus métodos de esta copia son constructor
y preload. `StatusConditionConfig.Fire` declara damageInterval, damageStart,
damageEnd y fireSpread. Su constructor fija 2.000 ms como valor por defecto:
**no es una medición del intervalo de quemadura de Combuster**, porque el catálogo
puede reemplazarlo. Monster.Rp devuelve sus referencias `_conditions`.

Estos datos permiten localizar el contrato de quemadura, pero no contienen por
sí solos toda la lógica de aplicación, resistencia, renovación, propagación y
daño periódico del servidor. Hay que leer la configuración concreta, seguir
sus consumidores y probar cada transición antes de habilitarla.

Evidencia: consultas del índice, `analysis/client-20261006114613/status-contract-bytecode.txt`,
`validation/authoritative-combat-notes.md` y pruebas de combate. La cadencia local
de la ráfaga sigue identificada como inferida, no como vanilla verificada.

## Refactores propuestos y orden

1. **Ejecución de acciones y condiciones:** contrato común de Compound,
   Conditional, timers, variables, expresiones, spawn y eliminación. Permite
   retirar excepciones por arma con pruebas de comportamiento equivalentes.
2. **Daño y estados:** centralizar resolución de ataque, profundidad, defensa,
   resistencias y condiciones. Validar quemadura/duración/renovación e interrupción
   con datos reales; no asumir que una fórmula de tooltip es todo el cálculo de servidor.
3. **Enemigos y daño entrante:** seguir DungeonBehaviorConfig y sus consumidores,
   separar comportamiento de snapshots e integrar defensa/esquiva/vida del jugador.
4. **Inventario/progreso:** extraer de ClientSession las transacciones completas,
   requisitos, binding y notificaciones; después agregar persistencia.
5. **Escenas/servicios restantes:** habilitar por contrato, con notificaciones
   y consecuencias de estado, conservando la cadena temporal del protocolo.

Este orden es una propuesta técnica, no cambios ya hechos. Primero escribir la
ficha del contrato, después implementar el sistema y solo entonces quitar el
código antiguo. Comparar entradas/salidas con la versión estable antes de mezclar
un cambio funcional con una limpieza estructural.

## Ficha mínima para cada contrato

- Clase, método, descriptor y versión/hash de origen.
- Mensaje o input que lo activa; objetos y atributos involucrados.
- Validación y orden de mutaciones/notificaciones; efectos de error/cancelación.
- Reloj, duración, dependencias y configuración concreta del catálogo.
- Evidencia: bytecode, datos importados, prueba y observación visual cuando corresponda.
- Separación explícita: confirmado / inferido / desconocido / implementado / probado.

Consultar el índice con límites pequeños; leer la fuente de la clase localizada
y confirmar el método con javap. Registrar el resultado aquí o en una ficha
específica para que el siguiente análisis no empiece de cero.
