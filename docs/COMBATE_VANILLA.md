# Combate en la copia congelada

Revisión: **2026-10-11**. Datos: **20261006114613**.

El Training Hall ahora tiene daño autoritativo contra los **11 cubos originales** de sus puntos de reaparición. Espadas, disparos y bombas producen impactos reales. Es una primera implementación; **todavía no reproduce todo vanilla 1:1**.

## Probarlo

1. Reiniciar el servidor con `start_server.bat` y el cliente con `launch_client.bat`.
2. Entrar al Training Hall desde Haven y acercarse a los cubos.
3. Atacar con Leviathan Blade. El golpe requiere alcance y orientación correctos y ocurre en el momento de impacto del ataque.
4. Elegir Polaris y disparar hacia un cubo: el daño llega cuando el proyectil lo toca.
5. Elegir Ash of Agni, mantener ataque hasta completar la carga y soltarlo junto a un cubo. La bomba explota después de su mecha y aplica el daño del área configurada.
6. Esperar la reaparición del cubo, cuyo tiempo procede del mapa congelado.
7. Elegir Combuster, mantener ataque hasta completar la carga y soltar: genera
   la onda y cinco explosiones secundarias, con color, forma y daño del arma.

El inventario local incluye las **374 armas creables** de tipo espada, pistola y
bomba de esta versión, además de cinco piezas de equipo: **379 objetos** en total.
Se entregan a nivel 10. El rango es el máximo original **10-3**, con cuatro ranuras
de armas y dos de talismanes. El servicio de objetos aplica equipar y desequipar
y notifica el equipo y las armas de la party. Yelmo ocupa la ranura 0 y armadura
la 1. El sprite de combate dibuja su propio escudo en la mano; el modelo base
ya no envía otro escudo ni otra arma. En Haven se conserva el escudo guardado.

La ráfaga de Brandish/Combuster usa `combat.burstIntervalMs=100` como cadencia
local de compatibilidad: el catálogo del cliente conserva el contador y la
referencia de las explosiones, pero omite las acciones de servidor necesarias
para ejecutarlos. **Esos 100 ms todavía no están verificados como vanilla.**
Los estados alterados, incluida quemadura, siguen pendientes.

Se conservan los controles configurados en el cliente. Tocar y mantener ataque son acciones distintas. El controlador original gobierna combos, carga, recuperación, defensa y esquiva; el cambio de arma respeta los bloqueos del golpe actual.

Los cubos de profundidad 0 tienen 30 de vida. La espada equipada puede matarlos de un golpe; el daño no es un valor fijo compartido por todas las armas.

## Alcance verificado

| Mecánica | Estado |
| --- | --- |
| Ataques, combos, carga, recuperación y controles | Máquina de estados original; controles ejercitados por la prueba integral |
| Cubos, posición, forma, salud, muerte y reaparición | Datos originales; comprobación determinista |
| Daño compartido entre jugadores | Dos clientes por TCP reciben la misma vida e instante de muerte del cubo |
| Alcance y orientación de espada | Formas originales; golpe, fallo y cancelación comprobados |
| Daño, rareza, nivel y defensa | Catálogos originales y curva de DamageSummary; daño real comprobado |
| Polaris básico | Actor de bala, movimiento, contacto y daño comprobados |
| Combuster cargada | Carga nativa y cinco explosiones secundarias; cadencia local pendiente de comparación vanilla |
| Bala que vence sin contacto | Fizzle original, sin daño de explosión al vencer |
| Ash of Agni cargada | Actor de bomba, mecha, radio y daño inicial comprobados |
| Nube química y secundarios de Polaris | Actores y temporizadores básicos; estados alterados y efectos pendientes |
| Escudo y esquiva | Estados originales; bloqueo de daño entrante aún sin validar |
| IA, ataques enemigos y daño al jugador | Pendientes; los cubos de esta implementación son estacionarios |
| Autoapuntado, paredes, desniveles y objetos | Pendientes; se conserva el límite exterior del movimiento |
| Reacciones, empuje, invulnerabilidad y limitadores | Pendientes |
| Todas las armas y acciones especiales | Sin validación exhaustiva; la consola identifica acciones sin implementar |

Una animación de escudo no demuestra bloqueo ni una nube demuestra quemadura. La paridad completa requiere implementar y probar esas capas.

## Pruebas y desarrollo

`test_server.bat` ejecuta `TestCombatSimulation`, con relojes deterministas, y `TestEmulatorProtocol`, sobre TCP 47625. Comprueba impactos, fallos de alcance/orientación, cancelación y desconexión, salud por ActorDelta, muerte/reaparición, bala/contacto/fizzle, bomba/mecha/nube y detención del movimiento. La prueba integral conserva login, Haven, portales, controles, cadencia, ACK, dos clientes y keepalive.

La simulación no sustituye una comparación visual cuadro por cuadro con vanilla ni una prueba entre dos PCs. Evidencia: `validation/test-authoritative-combat.log`.

`CombatWorld` pertenece a la escena y se actualiza una vez por tick; todas las sesiones reciben actores de la misma simulación. `VanillaCombatController` reutiliza el controlador instalado. `ClientSession` conserva la captura conjunta del estado y su input confirmado.

`InstalledConfigManager` admite ConfigGroup.Data y restaura en memoria los contenedores de región/señal indicados por los parámetros exportados. Esto permite aplicar las formas originales que se importaban como regiones vacías. No modifica los archivos congelados.

El combate y el equipo viven en memoria. Reiniciar el servidor restablece los
actores y reconectar restablece el inventario/equipo inicial; no hay progreso
persistente ni conexión con el juego oficial.
