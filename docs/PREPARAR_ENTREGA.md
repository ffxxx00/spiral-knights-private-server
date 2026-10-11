# Preparar la copia para entregar al anfitrión y a los jugadores

La documentación sola no aloja el juego. Tu amigo necesita los archivos de la copia y las guías; cada invitado necesita la misma copia para abrir el cliente.

## Contenido de la entrega

| Carpeta/archivo | Incluir | Motivo |
| --- | --- | --- |
| code, rsrc, scenes, native | Sí, completos | Bibliotecas, recursos, mapas y librerías nativas |
| java_vm, jdk | Sí, completos | Java del cliente y herramientas/Java del servidor, sin instalación de sistema |
| server | Sí | Fuentes, clases y configuración del emulador |
| portable | Sí | Preferencias aisladas y generador del overlay |
| client-local | Sí | Ajustes del cliente local y sus fuentes |
| start_server.bat, run_server.bat, run_server.ps1 | Sí | Arranque del servidor |
| launch_client.bat, launch_client.ps1 | Sí | Arranque/configuración del cliente y selección FFM |
| test_server.bat, allow_lan_firewall.ps1 | Sí | Prueba integral y permiso LAN opcional |
| frozen-version.json, getdown.txt, version.txt, full.dat | Sí | Versión, integridad y marcador de copia instalada |
| README.md, LEEME_PRIMERO.md, docs, AGENTS.md | Sí | Uso, documentación e instrucciones de desarrollo de la copia |
| profile | No en una entrega limpia | Preferencias locales del remitente; el cliente crea su propio perfil |
| validation, projectx.log, old-projectx.log, exports | Opcional / excluir del paquete de juego limpio | Sondeos, registros y paquetes previos; no son necesarios para jugar |

`getdown-pro.jar` puede conservarse al copiar la raíz; el lanzador privado no lo utiliza para iniciar. `server/runtime-client-config` se regenera al abrir el cliente. No hace falta editar sus IP antes de entregar.

La opción más sencilla es copiar la carpeta completa a una **carpeta nueva de entrega**, y excluir las preferencias y registros sólo de esa copia. No borres profile ni diagnósticos del original de trabajo. No uses enlaces o accesos directos que dependan de tu instalación de Steam.

## Cómo comprimir y recibir

1. Detener el servidor y cerrar el cliente para tomar una copia coherente.
2. Copiar los archivos a una carpeta nueva, por ejemplo `SpiralKnightsPrivate-20261006114613`.
3. Verificar la copia con docs/verificar-copia.ps1 y ejecutar test_server.bat desde ella.
4. Comprimir esa carpeta completa, manteniendo su estructura, con la herramienta de archivos que utilices.
5. Entregar el archivo por el medio elegido junto con la guía HTML o el ZIP de documentación. No se publica ni se envía automáticamente desde esta tarea.
6. El receptor extrae la carpeta completa, verifica integridad y realiza la prueba local antes de tocar el firewall.

No dividir una entrega copiando sólo los .bat. No extraer el paquete encima del juego oficial. Los usuarios no deben compartir cuentas oficiales ni claves de VPN para probarlo.

## Identificar una revisión

El número **20261006114613** identifica cliente/datos. Las revisiones del emulador pueden cambiar manteniendo ese número. Anotá también la fecha/revisión del paquete que estás enviando y entregá el mismo paquete a todos.

El verificador comprueba las ocho huellas de frozen-version.json, la versión declarada, archivos principales y la presencia del backend FFM. No firma el paquete ni garantiza que todas las fuentes del servidor sean iguales; compará el paquete original o su hash de archivo comprimido si hay dudas sobre la revisión recibida.

## Documentación lista para pasar

- `docs/MANUAL_COMPLETO.html`: manual completo en un archivo, para doble clic y lectura sin conexión.
- `exports/Documentacion-SpiralKnightsPrivate-20261006114613.zip`: guías, verificador y mensaje para el amigo. **No contiene el juego.**
- `docs/MENSAJE_PARA_TU_AMIGO.txt`: mensaje breve editable que podés copiar y mandar vos.

Si el amigo devuelve un fallo, pedile prueba-inicial.log, projectx.log, ruta extraída, versión/revisión del paquete, IP/puerto usados y si falló localmente o sólo por LAN/VPN. No hace falta que te pase contraseñas.
