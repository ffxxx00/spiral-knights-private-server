# Documentación de Spiral Knights Private

Cliente/datos: **20261006114613**. Revisión de documentación: **2026-10-11**. Plataforma de esta entrega: **Windows x64**.

| Documento | Para qué sirve |
| --- | --- |
| [Guía del anfitrión](GUIA_HOSTING_WINDOWS.md) | Instalar, probar, alojar y conectar amigos por LAN o VPN |
| [Guía del cliente](GUIA_CLIENTE.md) | Entrar al servidor del anfitrión, controles y modos de conexión |
| [Operación y diagnóstico](OPERACION_Y_DIAGNOSTICO.md) | Arranque, parada, logs, errores y pruebas |
| [Preparar la entrega](PREPARAR_ENTREGA.md) | Qué copiar, qué excluir y cómo entregar una copia independiente |
| [Referencia técnica](REFERENCIA_TECNICA.md) | Estructura, configuración, protocolo y limitaciones |
| [Combate vanilla](COMBATE_VANILLA.md) | Probar daño real en Training Hall y consultar las mecánicas pendientes |
| [Mensaje para tu amigo](MENSAJE_PARA_TU_AMIGO.txt) | Texto breve para acompañar la entrega |
| [Verificador de copia](verificar-copia.ps1) | Comprobar archivos y hashes sin cambiar la instalación |

**MANUAL_COMPLETO.html** reúne las guías en un solo archivo, abre en el navegador y puede imprimirse con Ctrl+P. Su contenido está incorporado: no necesita descargar CSS, fuentes ni scripts. Los enlaces a sitios externos sólo sirven como referencias.

El ZIP de documentación es **exports/Documentacion-SpiralKnightsPrivate-20261006114613.zip**, en la raíz de la copia. Sólo contiene documentación; para alojar o jugar también necesitás los archivos de la copia completa.

Las guías usan `C:\Games\SpiralKnightsPrivate` como ruta de ejemplo. Nuestra carpeta de trabajo puede estar en otra ubicación: los lanzadores resuelven rutas desde su propia carpeta.

## Alcance de las comprobaciones

Se verificaron el protocolo y dos clientes mediante la prueba integral, el cliente gráfico local en Haven y Training Hall, la detención al soltar una tecla y el backend FFM de memoria con Unsafe prohibido. Una conexión desde la PC del amigo por su LAN/VPN y las reglas de su firewall se verifican durante su instalación: no se presume que estén probadas en su red.

Evidencia técnica de desarrollo en `validation/`, especialmente `test-hosting-documentation.log`, `hosting-documentation-notes.md`, `test-ffm-backend.log`, `ffm-memory-notes.md`, `stop-movement-notes.md` y `low-latency-notes.md`. No hacen falta los sondeos antiguos para iniciar el juego.
