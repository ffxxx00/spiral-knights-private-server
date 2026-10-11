# Combate autoritativo — 2026-10-11

Versión de datos: 20261006114613. Desarrollo exclusivo en Spiral Knights Private.

Se sustituyeron los temporizadores manuales de combate por el controlador
`com.threerings.projectx.dungeon.util.i` instalado. Gobierna ataques, cadenas,
carga, recuperación, cambio de arma, defensa, bash y esquiva. La geometría,
ataques, proyectiles, mechas y daño proceden de los catálogos.

CombatWorld simula una escena compartida: 11 Training Cubes del mapa, salud,
impactos, muerte, reaparición, balas, bombas y temporizadores básicos. La fórmula
de defensa es DamageSummary.u; se conserva el LevelItem equipado para el golpe.
Un golpe cancelado antes de su fire action no daña; un actor que abandona la
escena tampoco ejecuta acciones pendientes de ese ataque. La bala usa geometría
barrida para contacto y fizzle al vencer sin impacto.

La importación conserva ConfigGroup.Data y DerivedConfig. Algunos contenedores
de región y la señal de nube se restauran en memoria según las rutas de sus
parámetros exportados, antes de aplicar argumentos originales. No se reescriben
los catálogos ni los JAR.

## Comprobaciones aprobadas

- test_server.bat: TestCombatSimulation y TestEmulatorProtocol, exit 0.
- Espada: impacto después de land; falla por distancia/orientación; cancelación
  y salida; salud/muerte por ActorDelta; respawn original con ID nuevo.
- Polaris: bala concreta, viaje antes del daño, contacto, fizzle sin explosión.
- Ash of Agni: bomba concreta, mecha, daño de radio, nube con vida acotada.
- Controlador nativo: recuperación, cambio de arma y detención al soltar caminar.
- Dos clientes TCP: mismo cubo, misma vida e instante de muerte; salida del amigo.
- Login, inventario/modelo, Haven, Training y regreso, controles, ACK atómico,
  continuidad de deltas, cadencia de reloj compartido y keepalive.
- docs/verificar-copia.ps1: ocho hashes aprobados.
- Manual HTML y ZIP regenerados, enlaces y estructura verificados.

Registro vigente: test-authoritative-combat.log. La cadencia se mide contra el
avance del reloj autoritativo y el tiempo real transcurrido; la lectura TCP
puede incluir un lote de deltas acumulados durante la precarga.

## Límites

No es paridad vanilla completa. Faltan estados alterados, reacciones/impulsos,
limitadores, daño al jugador, bloqueo de daño entrante, AI, autoapuntado, colisión
detallada y validación exhaustiva de armas. Los handlers no implementados se
identifican una vez por tipo en consola. Los valores de daño básicos probados
no acreditan todas las combinaciones de mods, resistencias y dificultad.

El cliente gráfico aislado cargó escenas/cubos y envió ataques, pero el sondeo
automatizado de muerte y reaparición visual no quedó aprobado: hubo demoras de
precarga y se corrigió la observación para usar ActorSprite en lugar del mapa
inicial de actores. El sondeo opcional ahora espera una señal de precarga antes
de inyectar el golpe. Esta última revisión del sondeo compila, pero no se usa
como evidencia positiva de paridad visual. No se modificaron preferencias del
usuario ni se cerraron sus procesos de juego; los sondeos usan su propio perfil.

La guía de uso y la matriz de pendientes están en docs/COMBATE_VANILLA.md.
Reiniciar servidor y cliente para cargar esta implementación.
