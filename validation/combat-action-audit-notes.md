# Auditoría de contratos del arsenal — 2026-10-11

La herramienta InspectCombatCatalog importa los catálogos mediante
InstalledConfigManager. Recorre las 374 armas creables y sigue referencias tipadas
de ataques, acciones de fuego, actores, condiciones de estado y handlers. No abre
sockets ni ejecuta simulación. Las clases de análisis se compilan en analysis/,
sin sobrescribir el servidor compilado.

Resultado: 142 Sword, 122 Handgun, 22 SwingingHandgun y 88 Bomb. Se alcanzan 71
tipos de configuración; no hubo referencias no resueltas en las familias recorridas.
126 armas alcanzan un SpawnActor sin referencia o una condición Tagged vacía.
El importador conserva tres advertencias de binding: shape dos veces, value una.
Estos resultados no certifican integridad de todos los datos ni comportamiento.

report_combat.py pasó sus verificaciones: conjunto de armas único y completo,
conteos consistentes, hashes de todos los catálogos importados, tipos presentes
en el índice JVM y valores concretos de Combuster. El reporte detallado y log
quedan fuera de Git. Solo se publica el resumen escrito por la herramienta propia.

Una segunda ejecución produjo un combat-catalog.json con el mismo SHA-256.
verificar-copia.ps1 confirmó los ocho hashes del manifiesto sin cambios.

El bytecode de MultiSpawnPrespawned confirma recorrido ordenado de spawns,
filtro Qj y aumento del ID por hijo. El inventario lo vincula a cinco armas.
CombatWorld no interpreta esta acción; antes de habilitarla deben completarse
los actores OrbitalBullet/CompoundOrbitalBullet (trayectoria, vida, colisiones
y efectos), evitando dejar discos estáticos indefinidos. Evidencia local:
analysis/client-20261006114613/multispawn-bytecode.txt y action-contract-signatures.txt.

Los SpawnRotatedActor y SpawnTransformedActor heredan de SpawnActor; la rama
actual del emulador acepta el tipo base sin aplicar todos sus campos adicionales.
El nuevo informe identifica su alcance (72 y 69 armas respectivamente), pero
esa presencia tampoco equivale a una implementación correcta.

No se modificó lógica de servidor/cliente en esta entrega. No se atribuyen nuevas
mecánicas a la prueba funcional anterior. La siguiente implementación debe escoger
un contrato completo y extender tanto simulación como prueba TCP cuando cambie
el comportamiento observable.
