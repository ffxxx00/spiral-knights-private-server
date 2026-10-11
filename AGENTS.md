# Emulador privado congelado

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
