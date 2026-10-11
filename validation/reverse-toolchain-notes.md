# Validación del análisis del cliente congelado

Fecha: 2026-10-11. Versión: 20261006114613.

Se descargó Vineflower 1.12.0 desde su release oficial y se verificó su SHA-256
contra el digest publicado por GitHub. El manifiesto reproducible se conserva
en tools/reverse/toolchain.json. Los ocho archivos del manifiesto congelado se
verifican antes de descompilar o construir el índice.

La descompilación terminó en 24 lotes, con 4.435 fuentes y navegación para las
8.327 clases del JAR principal. Dos cuerpos de KnightSprite contienen el marcador
de fallo de Vineflower. Su alternativa de inspección está en
analysis/client-20261006114613/KnightSprite-bytecode.txt. El log conserva 366 WARN
y 53 ERROR; consultar el bytecode cuando una conclusión dependa de estos métodos
o de una reconstrucción dudosa.

La prueba tools/reverse/test_index_client.py pasó usando el javac y javap del
proyecto. Compara offsets y tipos de instrucciones de llamadas y accesos a campos;
incluye switches, instrucciones wide, lambda/method handles, UTF-8 modificado y
navegación de clases con nombres que contienen $. No depende del código del juego
para producir sus resultados esperados.

Una segunda ejecución de Decompile reutilizó los 24 lotes completos. Una segunda
construcción del índice reutilizó el análisis de bytecode y actualizó navegación
y diagnósticos. Las consultas son de solo lectura y tienen salida limitada.

El inventario incluye 10.052 entradas de clase de todos los JAR de code/, 76.805
métodos y 215.485 llamadas simbólicas. Las variantes multi-release se cuentan como
entradas separadas. El índice no resuelve automáticamente despacho virtual,
reflexión ni contratos de red.

Los logs, fuentes, bytecode de apoyo, JAR descargado y SQLite permanecen locales
y fuera de Git. Se versionan las herramientas propias y los informes. Esta entrega
no modifica el servidor ni el cliente; la prueba funcional previa del emulador no
se presenta como validación de nuevos comportamientos vanilla.
