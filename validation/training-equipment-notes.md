# Movimiento, cargas e inventario — 2026-10-11

Referencia única: copia 20261006114613. Ningún JAR o recurso modificado.

- `Mobile._maxStep` comienza en cero y la clase del cliente no lo inicializa.
  `tudey.util.j.d(float,int)` limita a ese valor cada integración: se reproducía
  MOVING=true, speed=5, posición constante. El controlador del servidor elimina
  ese límite adicional para conservar su política de movimiento sin colisiones
  detalladas. Regresión exige desplazamiento real y detención tras soltar.
- `ItemCodes.Group` confirma HELM=[0], ARMOR=[1], SHIELD=[2], armas=[6,7,8,9].
  `MissionCodes.bAR` confirma rango máximo 10-3. No se inventa un rango superior.
- Catálogo derivado de las entradas creables Weapon/ de item.dat y sus tipos
  Sword, Handgun o Bomb; se conserva una sola instancia por nombre. Descubrimiento
  en arranque y caché por SceneManager, evitando explorar miles de objetos en login.
  El constructor de LevelItem recibe el config ID original, separado del instance ID.
- Se enlaza ItemMarshaller en bootstrap; su bytecode identifica method 9 como
  (itemId, Location, slot, ConfirmMarshaller). Se validan slots nativos antes de
  modificar equipo, se replica equipment/quickbar y se actualiza PartyMember.
  Cambios inválidos no modifican las ranuras. Equipo sin persistencia entre sesiones.
- DungeoneerSprite (dungeon.client.a.i) adjunta su propio escudo. Ox() devuelve
  true y selecciona bone_shield. El modelo base combate omite Shield y Weapon,
  evitando duplicar esas piezas; el modelo de Haven conserva el escudo guardado.
- La carga utiliza i: mantener ataque lleva a 283 y soltar completa lleva a 290.
  Se restaura el contenedor indicado por los paths exportados para Burst Bullet:
  el SpawnActor de la explosión debe ocupar actions[1], no actions[0]. Se conserva
  count como String/Number y los parámetros de daño, escala y color de cada arma.
- La plantilla importada no conserva el contador/tick original. La onda local
  aplica combat.burstIntervalMs=100 explícitamente como compatibilidad, NO como
  temporización vanilla verificada. La utilidad DamageSummary.t se usa
  para la división de daño mixto sin splitDepth; esa rama necesita comparación
  con resultados vanilla. Quemadura y otras condiciones no están implementadas.

Pruebas: test_server.bat; evidencia local en test-training-equipment.log.
La comprobación TCP cubre equipar/desequipar con notificaciones antes de confirmar.
La prueba temporal ahora precalienta Training 1250 ms y conserva su tolerancia
de tres ticks; no elimina la comprobación de cadencia ni continuidad.
La inspección del modelo y bytecode no sustituye una verificación visual del juego.

Resultado: suite integral y simulación aprobadas. test-combat-equipment-refresh.log
confirma conservar el índice de arma y cancelar el ataque al cambiar equipo.
verificar-copia.ps1 confirmó los ocho hashes de datos/JAR sin cambios.
