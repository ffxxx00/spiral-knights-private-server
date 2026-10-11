# Spiral Knights Server: checkout de fuentes

Repositorio: [ffxxx00/spiral-knights-server](https://github.com/ffxxx00/spiral-knights-server).

```powershell
git clone https://github.com/ffxxx00/spiral-knights-server.git
cd spiral-knights-server
```

Este repositorio contiene el servidor, los parches locales del cliente,
los lanzadores, las pruebas y la documentacion. El historial inicial fue
reconstruido: ver [HISTORY_RECONSTRUCTION.md](HISTORY_RECONSTRUCTION.md).

**Clonar este repositorio no descarga el juego completo.** Para compilar y
ejecutar se necesita la copia **20261006114613**, con sus propios
`code/`, `rsrc/`, `scenes/`, `native/`, `java_vm/` y `jdk/`. Estos archivos no
estan en Git. No completar una copia faltante con actualizaciones del juego
en vivo ni mezclar versiones. Las huellas disponibles estan registradas en
`frozen-version.json`; ese manifiesto verifica ocho archivos clave, no toda
la instalacion.

Para trabajar sobre la copia local existente, clonar en una carpeta separada
y colocar alli los recursos correspondientes, conservando las
fuentes de este repositorio. No sobrescribir `server/server.properties` de
una instancia personalizada sin revisar sus cambios. Conservar `full.dat`.

En Windows, desde una copia completa:

1. Ejecutar `docs/verificar-copia.ps1` para revisar version y archivos clave.
2. Ejecutar `test_server.bat` para compilar y probar el protocolo y el combate.
3. Ejecutar `start_server.bat` y luego `launch_client.bat`.

Consultar [LEEME_PRIMERO.md](LEEME_PRIMERO.md) y [docs/README.md](docs/README.md)
para hosting, LAN/VPN y diagnostico. El combate sigue teniendo las limitaciones
descritas en [docs/COMBATE_VANILLA.md](docs/COMBATE_VANILLA.md).

La politica `.gitignore` incluye solo fuentes y documentos seleccionados.
Los perfiles, logs, clases compiladas, ZIP, dumps de bibliotecas y archivos
del juego quedan fuera. Algunas notas mencionan evidencia local excluida;
las herramientas incluidas permiten repetir pruebas con la copia completa.

Los commits iniciales documentan estados recuperados y pueden no ser ejecutables
por si solos. Para desarrollo usar el ultimo commit. No se incluye una licencia
de terceros ni se concede permiso de redistribucion de los recursos del juego.
