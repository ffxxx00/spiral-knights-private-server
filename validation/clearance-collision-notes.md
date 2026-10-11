# Permisos de equipo y colisiones — 2026-10-11

Datos congelados 20261006114613; sin modificar recursos, mapas ni JAR.

El rango visible no bastaba: ItemConfig.Level y item.client.M consultan
PlayerObject.dN(rarity). Para rarezas 2..5, dN comprueba progressFlags a través
de dP con máscaras 64, 128, 256 y 512. Se habilitan solo esos permisos, se mantiene
MissionCodes.bAR y se verifican las seis rarezas tras serializar PlayerObject por TCP.

Las consultas del Context de VanillaCombatController devolvían false/0.
SceneManager ahora carga tile.dat, placeable.dat y path.dat y llama init en los
dos TudeySceneModel, construyendo sus índices nativos. SceneCollision delega en
las consultas nativas del mapa (intersección, penetración y dirección) y añade
las formas reales de Monster vivo usando Actor.e para respetar máscaras.
Las consultas del mapa están sincronizadas: el modelo tiene vectores/listas de
trabajo compartidos. No se agrega bloqueo entre jugadores.

Training usa la integración original a 60 Hz y su resolución de tres pasadas.
Haven subdivide movimientos en pasos de hasta 0.08 unidades y resuelve la misma
penetración, restaurando la posición anterior si no consigue resolverla.
Knight también se inicializa en Haven para disponer de forma y máscaras.

CombatWorld busca el primer contacto de las balas con geometría estática y
enemigos usando la forma barrida y búsqueda binaria. Conserva la máscara del
proyectil (Polaris: 137), distinta de la del jugador (43): los obstáculos bajos
no necesariamente bloquean disparos. Ejecuta la detonación en el contacto.

TestCombatSimulation comprueba movimiento real desde un spawn libre, paredes
de ambos mapas, controlador nativo frente a una pared, enemigo vivo y muerto,
disparo contra su propio límite de mapa y todas las rarezas del arsenal.
El fixture de carga se trasladó al rango abierto: antes disparaba desde fuera
de la habitación occidental atravesando su pared, incompatible con la física.
TestEmulatorProtocol comprueba permisos y rango recibidos por el cliente,
además de equipar/desequipar, entradas de mundo, controles, dos clientes y keepalive.

Evidencia local: validation/test-clearance-collision-full.log.
Todavía faltan validación visual, desniveles, autoapuntado, IA y daño al jugador;
no se declara paridad total 1:1.

Resultado: test_server.bat terminó con ambas suites PASSED (exit 0).
verificar-copia.ps1 confirmó los ocho hashes congelados sin cambios.
