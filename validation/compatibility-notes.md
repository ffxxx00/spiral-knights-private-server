# Compatibilidad con la versión congelada 20261006114613

Las firmas y bytecodes en esta carpeta se extrajeron exclusivamente con el
javap incluido y code/projectx-pcode.jar de esta copia. Las bibliotecas no se
modificaron. Las fuentes anteriores se guardaron antes de la adaptación.

Contratos comprobados:
- DObject.xL() y DEvent.xH(): OID del objeto y destinatario del evento.
- Invocation*.yc(): método; yd(): argumentos; Request.ye(): servicio;
  Response.yf(): request ID; Notification.yb(): receiver ID.
- Stream input.qe(), output.bf(), sink.sH()/sI(): objetos y framing.
- Actor.Mv()/vw()/tz()/tA(): configuración, ID, posición y rotación.
- Active.adU()/adV()/aU(): actividad, inicio y actualización.
- InputFrame.QY()/tA()/adz(): tiempo, rotación y dirección.
- SceneDeltaEvent.aee()/aef()/aeh()/QY()/aej()/aek()/ael(): escena,
  ACK, referencia, tiempo y actores añadidos/actualizados/eliminados.
- DungeonInputFrame.au()/Qv(): ranura de arma; Nh(): cambio de acción.
- Dungeoneer.Ri()/Rj()/Rk()/aw(): arma, configuración, escudo y combo.
- AttackConfig.Original.Qf()/Qg(): duración total y tiempo de rearmado.
- Item.VA() lee el ID por instancia de bvX; LevelItem.VN() produce el
  objeto de arma para combate. ConfigReference.kW() contiene argumentos.
- RotatingGateSummary requiere ownerKnightId y flags separados: el gate
  local conserva ownerKnightId=0 y flags=0.
- PingRequest.ym() es el tiempo recibido, yl() es el timestamp transitorio
  del emisor. PongResponse necesita el tiempo recibido para processDelay.
- PlayerObject.prefs debe ser una DSet vacía para que OptionsDialog lea los
  valores por defecto mediante PlayerObject.MO(), sin NullPointerException.
- ProjectXApp.Ha() omite Getdown si ya existe full.dat. El lanzador crea ese
  marcador antes de la primera ejecución, puesto que se copiaron todos los assets.

La prueba integral usa la versión exacta congelada y distingue posición,
rotación, actividad y tiempos, además de probar el flujo completo y dos clientes.
La prueba gráfica conectó a localhost:48024, recibió Haven, inició el estado
Tudey y emitió input. Los ticks observados se mantuvieron en 10 Hz. Ver
smoke-graphical-client.log y smoke-server.log. Los procesos de esa prueba se cerraron.

El primer sondeo gráfico detectó prefs nulo y falta de servicio de feed social.
Se corrigió prefs y se añadió una regresión que ejecuta la lectura real que hace
OptionsDialog después de serializar/deserializar el PlayerObject. El feed social
no existe todavía en el mini emulador; no se añadió un servicio ficticio ni se
conectó al oficial. Los paneles que dependan de él conservan esa limitación.