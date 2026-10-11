# Referencia técnica de la copia independiente

Versión de datos: **20261006114613**. Estado documentado: **2026-10-11**.

## Componentes

El servidor es un proceso Java separado del cliente. Los lanzadores utilizan rutas relativas a su carpeta y runtimes propios. El juego abre ProjectXApp directamente, sin Steam ni el actualizador Getdown. `full.dat` indica al cliente que los recursos ya están instalados. `getdown.txt` conserva la versión local que anuncia el emulador.

| Componente | Responsabilidad |
| --- | --- |
| SpiralServer | Listener TCP, sesiones, comandos de consola y versión de datos |
| ClientSession | Bootstrap, DObjects, RPC, input y publicación de estados para cada cliente |
| SceneManager | Configuraciones/mapas de Haven y Training Hall, actores y transiciones |
| SceneNetworkManager | Relojes de escena compartidos y difusión de actores a los participantes |
| CombatWorld | Cubos originales, daño, actores de proyectil/bomba y reaparición compartidos |
| VanillaCombatController | Máquina de estados de combate original de la biblioteca |
| MovementConfig | Lectura y validación de server/server.properties |
| InstalledConfigManager / ItemCatalog | Configuración e inventario desde los datos instalados |
| portable/LocalPreferencesFactory | Opciones del cliente en profile/preferences, aisladas del Registro |
| portable/BuildLocalClient | Overlay de las clases para loopback, verificado antes de generar |

Los scripts recompilan fuentes al iniciar cuando corresponde. `client-local/classes` contiene clases generadas; no sustituye los JAR originales. El lanzador comprueba el SHA-256 de projectx-pcode.jar antes de aplicar el overlay local.

## Red y protocolo

- Three Rings Presents / Narya / DObject sobre TCP.
- Puerto normal 47624; prueba integral 47625.
- Listener de juego en 0.0.0.0:puerto, con TCP NoDelay/KeepAlive y cola de salida por cliente.
- Sin UDP implementado. No es un servidor HTTP ni requiere abrir un sitio web.
- Las escenas conocidas son Haven (sceneId 1, placeOid 500) y Training Hall (sceneId 294, placeOid 501).
- Los deltas se encadenan contra el registro anterior. Los ACK de escena informan consumo, sin bloquear el reloj.
- Actor e input confirmado se capturan juntos: un ACK no debe confirmar input que no está representado en ese estado.
- Autenticación local de desarrollo: no verifica contraseña real; las cuentas y estado de jugador no son persistentes.

## Ajustes actuales

| Propiedad | Valor de esta entrega | Efecto |
| --- | --- | --- |
| movement.enabled | true | Procesar InputFrame |
| movement.sendAuthoritativeUpdates | true | Publicar estado autoritativo |
| movement.baseSpeed | 5.0 | Base compatible con los actores instalados |
| movement.speedMultiplier | 1.0 | Velocidad efectiva 5.0 |
| movement.maxFrameDeltaMs | 250 | Acotar el tiempo procesado por frame atrasado |
| movement.maxFramesPerPacket | 64 | Acotar historial por paquete |
| movement.deltaIntervalMs | 20 | Tick base / Haven, 50 Hz |
| movement.trainingDeltaIntervalMs | 20 | Training Hall, 50 Hz |
| movement.actorStateIntervalMs | 20 | Estado/reconciliación de actores |
| movement.clampToMapBounds | true | Límites exteriores del mapa; no colisión detallada |
| movement.logEveryPackets | 20 | Resumen cada 20 paquetes; 0 desactiva ese resumen |

Training y el intervalo de estado deben ser múltiplos del tick base. El generador local acepta intervalo entre 10 y 100 ms. Cambiar velocidad o datos del actor sin adaptar la predicción puede causar correcciones visuales; no es una solución al lag.

## Diferencias de los modos del cliente

| Comportamiento | Localhost / loopback | LAN/VPN o -NetworkMode |
| --- | --- | --- |
| Overlay de input, render y precarga | Activo | No se carga |
| Suavizado residual del personaje controlado | Eliminado | Política original del cliente |
| Precarga por etapa | Presupuesto 8 ms | Política original del cliente |
| Memoria LWJGL FFM | Activa | Activa |
| G1 y limpieza concurrente | Activos | Activos |
| Preferencias aisladas | Sí | Sí |

Los objetivos de tiempo son ajustes, no límites físicos garantizados de CPU/GPU/GC. TCP remoto conserva la latencia de la red. No se afirma ping físico 0 ms.

## Backend de memoria

El Java del cliente es 25.0.1; el JDK del servidor/compilación es Temurin 25.0.3. El LWJGL incluido contiene un backend FFM para Java 25. El lanzador selecciona `org.lwjgl.system.memoryBackend=ffm`; usa java.lang.foreign en vez de los accesos obsoletos de sun.misc.Unsafe. Referencia: [configuración de LWJGL](https://javadoc.lwjgl.org/org/lwjgl/system/Configuration.html#MEMORY_BACKEND).

El cliente fue probado en Haven y Training Hall con `--sun-misc-unsafe-memory-access=deny`. Ese flag se usa en el sondeo, no para ocultar avisos del cliente normal. La entrega no incluye `--sun-misc-unsafe-memory-access=allow` en el lanzador.

## Estado y límites

Se han comprobado login local, inventario/modelo, Haven, movimiento y rotación, transiciones a Training Hall y regreso, controles de armas/ataque/carga/defensa/esquiva/shield bash, estados de dos clientes y keepalive. El cliente gráfico se verificó con recursos de esta copia y Java propio.

Hay daño autoritativo comprobado contra los cubos originales del Training Hall, con espada, bala de Polaris y explosión cargada de Ash of Agni. Las formas, tiempos y valores proceden de los datos. Consultar [combate vanilla](COMBATE_VANILLA.md) para pruebas y pendientes: todavía no se acredita paridad completa, estados alterados, IA, daño entrante, reacciones ni colisiones detalladas. Economía, misiones, PvP y persistencia siguen pendientes. El feed social no está implementado. No hay panel web de administración, servicio de Windows, instalador automático ni proceso de actualización de Steam.

El alojamiento descrito corresponde a Windows x64. La entrega no incluye un runtime Linux, instrucciones de Docker ni una adaptación probada para macOS/ARM.

## Desarrollo futuro

Trabajar en una copia de esta carpeta. Conservar AGENTS.md y verificar clases ofuscadas con jdk/bin/javap.exe contra el JAR. Las firmas cambian entre versiones: no asumir que un nombre mantiene su significado. Las pruebas viven en server/TestEmulatorProtocol.java; ejecutar test_server.bat antes de afirmar compatibilidad.

Para cambiar cliente/datos, preparar otra versión aislada y una nueva adaptación/verificación. No copiar automáticamente desde la instalación en vivo ni mezclar sus recursos con esta entrega.
