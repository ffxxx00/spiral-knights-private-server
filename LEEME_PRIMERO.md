# Spiral Knights Private: empezar aquí

Versión congelada del cliente: **20261006114613**. Guías revisadas el **10 de octubre de 2026** para una PC con Windows x64.

Abrí **docs/MANUAL_COMPLETO.html** con doble clic: contiene toda la documentación y funciona sin conexión. Las fuentes editables están en [docs/README.md](docs/README.md).

## Si vas a alojar el servidor

1. Recibí la carpeta completa de esta copia independiente y extraela, por ejemplo, en `C:\Games\SpiralKnightsPrivate`.
2. Desde esa carpeta, ejecutá `test_server.bat`. Tiene que terminar con `PRIVATE SERVER WORLD-ENTRY PROTOCOL TEST PASSED`.
3. Abrí `start_server.bat` y esperá `LISTO PARA CONEXIONES`.
4. Probá `launch_client.bat` en la misma PC y elegí el personaje disponible.
5. Seguí [la guía del anfitrión](docs/GUIA_HOSTING_WINDOWS.md) para conectar invitados por LAN o VPN privada.

## Si sólo vas a jugar

Necesitás la misma copia completa y la IP del anfitrión. Desde su carpeta, en CMD:

```bat
launch_client.bat 192.168.1.20 47624 HeroKnight
```

La IP es un ejemplo: reemplazala por la IP LAN o VPN que te pase el anfitrión. No abras un servidor propio para entrar al de tu amigo. Detalles en [la guía del cliente](docs/GUIA_CLIENTE.md).

## Qué estás probando

Es un mini emulador con Haven, Training Hall, movimiento, controles de combate y sincronización entre clientes. No ofrece todavía cuentas con contraseña real, progreso persistente ni el juego completo. El acceso remoto se prepara para amigos por red privada; no para publicar el puerto del juego en Internet.

Los archivos Java, recursos, mapas y herramientas están incluidos. No necesitás instalar Java del sistema ni usar Steam para iniciarlo. Las actualizaciones del juego oficial no actualizan esta copia.
