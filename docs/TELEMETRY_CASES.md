# Casos locales de telemetría

La captura MissionRecorder de axes_of_evil fue preservada localmente por SHA-256,
sin modificar el origen de Steam ni ejecutar su agente. Las fuentes y resultados
se guardan en analysis/telemetry/axes-of-evil/, excluidos de Git.

SHA-256 del JSONL original:
7109e50e41d5ad73b19d5ca932e8e5288dcd83ce5ce68d4641f76c951f84c136

La captura contiene 1.154 deltas de escena, 808 lotes de input, 1.783 frames
de entrada antes de deduplicar el historial y 56 snapshots. Tiene cierre normal.
60 registros contienen estructuras truncadas. La versión/hash del cliente que
produjo la grabación no está disponible en su cabecera.

## Preparación realizada

telemetry_cases.py produce cinco segmentos según contexto e identidad de escena.
Ordena el historial de entrada por timestamp, deduplica únicamente frames exactos
y conserva conflictos de contenido para un mismo timestamp. Los segmentos con
entrada contienen 204 y 569 frames únicos. Conservar ambos relojes originales:
el tiempo relativo de observación no sustituye al reloj de input ni al de servidor.

Se extraen campos explícitos de actores, altas, bajas y efectos. Los deltas
reflectivos se conservan aparte. La captura serializó su _clazz como java.lang.Class
sin el nombre de la clase real; no se interpreta su máscara ni se asignan sus
valores a campos por conjetura. Los IDs reales también permanecen intactos, porque
las referencias internas de los deltas aún no se pueden traducir con seguridad.

Todos los segmentos tienen replay_ready=false. Falta vincular cliente y assets,
completar estado inicial y resolver el esquema de los deltas. Las discontinuidades
de cadena se señalan como diagnóstico; no se declara defectuoso el servidor
original por esos registros. Estos archivos son candidatos a casos, no pruebas
de comportamiento vanilla ya reproducido.

## Comparador disponible

El comando compare acepta resultados de un futuro ejecutor controlado:

```json
{
  "case_id": "identidad del caso",
  "input_sha256": "hash de los inputs",
  "initial_state_sha256": "hash del estado inicial",
  "client_sha256": "hash del cliente de referencia",
  "samples": [
    {"time_ms": 100, "actors": [
      {"id": "enemy-1", "position": {"x": 1.0, "y": 2.0}, "health": 0.8}
    ]}
  ]
}
```

Rechaza experimentos con identidades distintas o sin datos, relojes inválidos
y actores duplicados. Compara campos observados, IDs, cantidad de muestras y
tiempos; admite tolerancia explícita de posición. Los demás campos se comparan
exactamente. No alinea automáticamente ataques distintos ni oculta actores extra.
El ejecutor real contra CombatWorld aún no está conectado; las pruebas de esta
entrega verifican el normalizador y comparador con fixtures controlados.

## Próxima captura útil

Registrar versión y hash del cliente, configuración y argumentos del arma,
profundidad, estado inicial completo, nombres de clase de los deltas, input y
snapshots autoritativos. Conservar semillas cuando sean observables; si no lo son,
separar verificaciones deterministas de las estadísticas de azar.

Empezar con un escenario breve por mecanismo: caminar/detenerse, espada básica,
bala, bomba y carga de Combuster. Comparar altas, geometría, impacto, daño, estado,
muerte y replicación. La captura CombatRecorder existente dura unos 10 segundos
y tiene 48 muestras; sirve como diagnóstico, pero no demuestra una carga completa.
