# Reconstrucción del servidor por contratos

El objetivo es ejecutar las reglas del juego de forma autoritativa y producir los
objetos y deltas esperados por el cliente congelado 20261006114613.

## Base disponible

- Cliente y catálogos congelados, índice JVM y auditoría de las 374 armas.
- Referencia pública de Clyde fijada por commit y hashes: ver CLYDE_COMPATIBILITY.md.
- Contratos genéricos contrastados en esa referencia: ver ENGINE_ACTION_CONTRACTS.md.
- Capturas preservadas, cinco segmentos de misión y comparador: ver TELEMETRY_CASES.md.
- Emulador funcional de sesión/escenas y subconjunto de combate con pruebas propias.

No sustituir configuraciones importadas incompletas por valores supuestos. Un nodo
resuelto no demuestra que sus parámetros, ejecutor y estado inicial estén completos.

## Orden de integración

1. Adaptar un contrato genérico pequeño de Clyde. Separar el contexto de ejecución
   (reloj de escena, fuente, activador, RNG y acceso al mundo) del transporte TCP.
   Mantener las configuraciones congeladas como entrada. Examinar primero None,
   Compound y apariciones simples; registrar resultado y efectos de cada acción.
2. Conectar un ejecutor de casos al mismo contexto utilizado por CombatWorld.
   Capturar snapshots en tiempos solicitados y emitir el formato del comparador.
   Un caso requiere escena, estado inicial, input, versión y política de azar.
3. Completar temporizadores, condiciones, variables y señales como sistemas
   comunes, contrastando la semántica de Clyde y las extensiones ProjectX.
   Ejecutar Startup/Shutdown y cancelaciones con un orden definido y probado.
4. Reconstruir proyectiles e impactos; después daño, estados y reacciones. Validar
   un circuito completo de espada, pistola, bomba y Combuster antes de ampliar.
5. Integrar comportamiento de enemigos y daño al jugador; después transacciones
   de inventario, progreso, persistencia y el resto de los servicios de escenas.

Cada paso debe conservar las regresiones existentes y añadir evidencia observable.
La compatibilidad de campos de Clyde permite escoger candidatos, pero no reemplaza
la comprobación de métodos, dependencias, valores importados y comportamiento.

## Evidencia que permite avanzar

Antes de declarar un contrato compatible, conservar: origen/hash, configuración
concreta, disparador, estado previo, orden de mutaciones, reloj, cancelaciones,
salida replicada y prueba. Clasificar cada conclusión como confirmada, inferida
o pendiente. Una prueba de herramientas no se presenta como prueba del servidor.

Las grabaciones antiguas permiten estudiar protocolos y trayectorias. Para medir
paridad deben completarse sus identidades y estado inicial, o generar nuevas
capturas con el esquema requerido. No ejecutar los bots para suplir datos faltantes
durante una auditoría offline.
