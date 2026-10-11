# Guía para alojar el servidor en una PC con Windows

Cliente congelado **20261006114613**. Revisión: **10 de octubre de 2026**.

## 1. Qué necesitás

- Una PC con Windows x64 y acceso de escritura a la carpeta de la copia. El paquete incluye Java y JDK para Windows x64.
- La **carpeta completa de Spiral Knights Private** de esta entrega. Los invitados necesitan esa misma copia, cada uno en su PC.
- PowerShell de Windows para los lanzadores. No hace falta instalar Java en el sistema, Git, Steam, Docker ni una base de datos.
- Para recibir conexiones remotas, permisos de administrador al configurar el firewall. El servidor y el juego se inician normalmente sin elevación si la carpeta es escribible.
- Si también vas a jugar en el anfitrión, una GPU/controlador que pueda ejecutar el cliente OpenGL. Alojar sólo el servidor no requiere abrir el cliente gráfico.

La carpeta de trabajo medida ocupa aproximadamente **1,3 GB**, incluidos diagnósticos. Reservar **4 GB libres** da margen para extraer y mantener una copia de respaldo; es una orientación de instalación, no un requisito mínimo del motor. No hay un mínimo de RAM ni un límite de jugadores certificado. La regresión automática cubre dos clientes; ampliá gradualmente la prueba si van a ser más.

## 2. Recibir y extraer la copia

Extraé todo fuera del ZIP, por ejemplo en:

```text
C:\Games\SpiralKnightsPrivate
```

No la mezcles con una carpeta de Steam ni con una instalación anterior. Abrí la carpeta extraída y comprobá que contiene `start_server.bat`, `launch_client.bat`, `test_server.bat`, `jdk`, `java_vm`, `code`, `rsrc`, `scenes`, `server`, `portable` y `client-local`.

Desde ahora, todos los comandos de esta guía se ejecutan **en esa carpeta**. En PowerShell:

```powershell
Set-Location -LiteralPath 'C:\Games\SpiralKnightsPrivate'
```

Opcionalmente verificá la integridad, sin permisos de administrador:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\docs\verificar-copia.ps1
```

Resultado esperado: `COPIA VERIFICADA: 20261006114613`. Si falta un archivo o un hash no coincide, pedí nuevamente la copia; no lo reemplaces desde Steam. Este verificador confirma archivos, no la conexión de red.

## 3. Probar el servidor antes de configurar la red

Abrí CMD en la carpeta y ejecutá:

```bat
test_server.bat > prueba-inicial.log 2>&1
```

Abrí `prueba-inicial.log` y buscá al final:

```text
PRIVATE SERVER WORLD-ENTRY PROTOCOL TEST PASSED
```

La prueba compila, inicia su propio servidor en **TCP 47625**, verifica login, Haven, movimiento, Training Hall y retorno, combate, dos clientes y keepalive; después lo detiene. No deja alojado el servidor para jugar. El puerto 47625 debe estar libre; no necesita abrirse a invitados en el firewall.

## 4. Iniciar y probar en la misma PC

Doble clic en **start_server.bat**, o desde CMD:

```bat
start_server.bat
```

Esperá estas líneas:

```text
Servidor escuchando en: 0.0.0.0:47624
Version de datos efectiva: 20261006114613
Estado: LISTO PARA CONEXIONES
```

Dejá esa consola abierta. En otra ventana, abrí **launch_client.bat**. Elegí el personaje disponible y comprobá que carga Haven, podés moverte y podés entrar y volver de Training Hall.

El servidor no usa cuentas oficiales. Los personajes son temporales: los genera por conexión como HeroKnight, HeroKnight2, etc. El parámetro HeroKnight del lanzador no crea una cuenta persistente ni cambia el nombre que asigna el servidor.

## 5. Elegir la red para los invitados

| Dónde están los jugadores | Dirección para conectar | Preparación |
| --- | --- | --- |
| En la PC anfitriona | localhost | Ningún puerto remoto que abrir |
| En la misma red local | IPv4 LAN del anfitrión | Regla de firewall LAN y red privada |
| En otras casas | IP de una VPN privada | VPN conectada y permiso para los invitados |

**El emulador acepta autenticación local sin contraseña real.** La ruta documentada para amigos en otras casas es una VPN privada. No abras directamente 47624 en el router ni lo publiques con un túnel público. Una contraseña escrita en el login no añade autenticación al servidor.

### A. Invitados en la misma LAN

1. Conectá las PCs a la misma red local. Una red de invitados del router o el aislamiento Wi-Fi pueden impedir conexiones entre dispositivos.
2. En el anfitrión, seleccioná perfil de red **Privado** sólo para esa LAN de confianza en Configuración de Windows.
3. Abrí PowerShell **como administrador**, entrá en la carpeta del juego y ejecutá:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\allow_lan_firewall.ps1
```

La regla permite TCP 47624 en perfil Privado, sólo desde `LocalSubnet`. No modifica el router. El script requiere el módulo NetSecurity de Windows.

4. Usá la IPv4 de la tarjeta LAN/Wi-Fi del anfitrión. Se puede ver con `ipconfig` o en las direcciones que imprime el servidor. Si aparecen varias IP, elegí la de la red compartida; no una interfaz virtual que el invitado no alcanza.
5. El invitado prueba en PowerShell, reemplazando la IP de ejemplo:

```powershell
Test-NetConnection 192.168.1.20 -Port 47624
```

Resultado esperado: `TcpTestSucceeded : True`. Luego, desde CMD en la carpeta de su copia:

```bat
launch_client.bat 192.168.1.20 47624 HeroKnight
```

### B. Invitados en otras casas mediante Tailscale

Tailscale es una opción concreta para una VPN privada. Consultá su [instalación oficial para Windows](https://tailscale.com/docs/install/windows). No hace falta sincronizar el juego con ningún servicio oficial.

1. Anfitrión e invitados instalan Tailscale e inician sesión con sus propias cuentas.
2. Autorizá a los invitados en la red privada o compartí la PC anfitriona mediante **Machines > Share** de la consola de Tailscale y pasales la invitación. Los invitados la aceptan. No compartan contraseñas. Pasos oficiales: [compartir una máquina](https://tailscale.com/docs/features/sharing).
3. Anotá la IPv4 VPN del anfitrión que ve cada invitado y las IPv4 VPN de los invitados. Las políticas de Tailscale deben permitir el servicio TCP 47624. La IP VPN del anfitrión se usa para el juego, no su IP pública. Referencia: [conectar dispositivos y servicios](https://tailscale.com/docs/how-to/connect-to-devices).

El script `allow_lan_firewall.ps1` **no configura la VPN**: su permiso LocalSubnet/Privado puede no abarcar a sus clientes. Si Windows bloquea la conexión VPN, creá esta regla específica en PowerShell **como administrador**. Las IP siguientes son ejemplos: reemplazalas por las reales antes de ejecutar.

```powershell
Set-Location -LiteralPath 'C:\Games\SpiralKnightsPrivate'
$IpHostVPN = '100.101.102.10'
$IpsInvitadosVPN = @('100.101.102.11', '100.101.102.12')
$ReglaVPN = 'SpiralKnightsPrivateServer-VPN-TCP-47624'
New-NetFirewallRule -Name $ReglaVPN `
    -DisplayName 'Spiral Knights Private - invitados VPN' `
    -Direction Inbound -Action Allow -Enabled True -Profile Any `
    -Protocol TCP -LocalPort 47624 -LocalAddress $IpHostVPN `
    -RemoteAddress $IpsInvitadosVPN -Program "$PWD\jdk\bin\java.exe"
```

`Profile Any` cubre el perfil que Windows asigne a la interfaz VPN; el permiso sigue limitado a esas IP, a la IP VPN local, al puerto y al Java del servidor. No se usa una regla abierta a todas las direcciones. Si la regla ya existe, revisala antes de intentar crearla otra vez. Si cambian la IP VPN, carpeta o puerto, actualizá esa regla.

Referencia de los filtros de Windows: [New-NetFirewallRule](https://learn.microsoft.com/en-us/powershell/module/netsecurity/new-netfirewallrule).

En cada invitado, primero probá:

```powershell
Test-NetConnection 100.101.102.10 -Port 47624
```

Con `TcpTestSucceeded : True`, abrí CMD en su copia y ejecutá:

```bat
launch_client.bat 100.101.102.10 47624 HeroKnight
```

Las reglas de acceso de la VPN y el firewall de Windows son controles independientes: ambos deben permitir la conexión. La conexión real desde otra casa queda validada cuando este paso funciona en la PC del invitado.

## 6. Verificar una sesión entre amigos

- Cada invitado elige el personaje que le ofrece el emulador.
- En la consola del anfitrión, `status` muestra sesiones activas y jugadores en Haven/Training.
- `[AUTH] success` confirma entrada; `[NETWORK] join` confirma incorporación a una escena.
- Comprobá que se ven dos personajes y que uno ve moverse al otro. Probá ida y vuelta a Training Hall.
- Cerrá un cliente: los demás deben seguir conectados y el personaje retirarse de la escena.

Registrá IP usada, puerto y cantidad de clientes para poder repetir la prueba. No se certificó una capacidad máxima de jugadores.

## 7. Dejarlo alojado y detenerlo

La PC debe seguir encendida, conectada y sin suspensión automática durante la sesión. La consola del servidor debe permanecer abierta. No es un servicio de Windows y no se inicia solo con el sistema.

Para terminar, avisá a los jugadores y escribí `stop` en la consola del servidor. Verificá `Servidor detenido`. Los cambios de `server/server.properties` se leen al iniciar: para aplicarlos, detené y volvé a iniciar el servidor.

Si querés retirar el permiso VPN creado arriba, en PowerShell como administrador:

```powershell
Remove-NetFirewallRule -Name 'SpiralKnightsPrivateServer-VPN-TCP-47624'
```

Esto sólo retira esa regla, no cambia las reglas generales de Windows.

## 8. Usar otro puerto

Por ejemplo, TCP 48000:

```bat
start_server.bat 48000
launch_client.bat localhost 48000 HeroKnight
```

Para LAN, creá la regla correspondiente:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\allow_lan_firewall.ps1 -Port 48000
```

Los invitados usan `launch_client.bat IP_DEL_HOST 48000 HeroKnight`. Para VPN, adaptá LocalPort y el nombre de la regla. No edites deployment.properties para elegir otro puerto: el lanzador lo genera con los argumentos.

## Si algo falla

Seguí [Operación y diagnóstico](OPERACION_Y_DIAGNOSTICO.md), empezando por la prueba local y luego la conectividad TCP. Si sólo falla al jugar desde otra PC, no reemplaces archivos del juego antes de comprobar IP, firewall y VPN.
