# Servidor privado

En documentación, mensajes y conversaciones, llamarlo simplemente «cliente».
Mencionar versiones y hashes solo cuando sean relevantes para el procedimiento.

Desarrollar exclusivamente dentro de esta carpeta. No leer ni modificar
Steam/Spiral Knights ni online-container para ejecutar esta copia.
Utilizar jdk/bin/javac.exe, jdk/bin/javap.exe y jdk/bin/java.exe incluidos.
No introducir enlaces, dependencias de PATH ni rutas absolutas a Steam.
No actualizar ni sincronizar code/, rsrc/, scenes/, native/ o java_vm/ desde
el juego en vivo. Versión estable: 20261006114613; hashes en frozen-version.json.
Conservar server/server.properties y el aislamiento de preferencias del cliente.
El marcador full.dat evita que ProjectXApp.Ha() inicie Getdown. Conservarlo.
No conectar con servicios ni cuentas oficiales.
Confirmar firmas y semántica con javap contra code/projectx-pcode.jar, nunca
contra una biblioteca externa. Evidencia inicial en validation/*bytecode.txt
y validation/*signatures.txt. No usar el mapa de nombres como regla universal:
un nombre ofuscado puede tener significados distintos según la clase.
Ejecutar test_server.bat antes de declarar funcional un cambio. Deben pasar
login, Haven, movimiento, portales, controles de combate, dos clientes y keepalive.
No afirmar daño, persistencia ni colisiones detalladas hasta implementarlos y probarlos.
Estado actual: adaptación compilable; prueba integral aprobada. Ver README.md.

## Análisis persistente del cliente

Consultar docs/REVERSE_MAP.md y tools/reverse/README.md antes de explorar un sistema.
Usar tools/reverse/reverse.ps1 -Action Query para localizar clases, llamadas y campos.
Fuentes e índice locales en analysis/client-20261006114613/; excluidos de Git.
No repetir la descompilación completa si coinciden los hashes de la caché.
Confirmar decisiones con javap y datos: una fuente descompilada o una
arista de llamada simbólica no demuestra comportamiento de servidor ni paridad.
Registrar contrato, evidencia e incertidumbre antes de refactorizar cada sistema.
Para combate, consultar docs/COMBAT_ACTION_AUDIT.md y el informe local
combat-catalog.json. CombatAudit no mide comportamiento: el cliente conserva
placeholders y hay advertencias de binding. No deducir compatibilidad completa
por la mera presencia o resolución de una configuración.
Consultar docs/SERVER_SIDE_PLAN.md para la integración por contratos. La referencia
de Clyde está fijada en tools/reverse/clyde-reference.json; no sustituir los JAR.
La telemetría preservada en analysis/telemetry/ es evidencia offline. Las capturas
antiguas no tienen versión/hash de cliente y sus deltas carecen de identidad de
clase; no declarar paridad usando segmentos con replay_ready=false.
