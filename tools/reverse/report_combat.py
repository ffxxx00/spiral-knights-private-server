"""Validate the frozen reachability audit and emit an authored metadata summary."""
import argparse
from collections import Counter
import hashlib
import json
import re
from pathlib import Path
import sqlite3


def report(root: Path) -> None:
    directory = root / "analysis/client-20261006114613"
    data = json.loads((directory / "combat-catalog.json").read_text(encoding="utf-8"))
    assert data["version"] == "20261006114613" and data["schema"] == 1
    weapons = data["weapons"]
    names = [weapon["name"] for weapon in weapons]
    assert len(names) == len(set(names)) == 374, "Incomplete frozen arsenal"
    counts = Counter(kind for weapon in weapons for kind in set(weapon["types"]))
    assert dict(counts) == data["weapons_by_type"], "Aggregate reachability mismatch"
    for path, digest in data["input_sha256"].items():
        assert hashlib.sha256((root / path).read_bytes()).hexdigest() == digest, path
    with sqlite3.connect((directory / "index.sqlite").as_uri() + "?mode=ro", uri=True) as db:
        classes = {row[0] for row in db.execute("SELECT name FROM classes")}
    assert set(counts) <= classes, "Audit type absent from frozen bytecode index"
    issues = [(w["name"], issue) for w in weapons for issue in w["issues"]]
    assert not issues, f"Unresolved references: {issues[:5]}"
    # Separate golden data checks from any claim of server behavior.
    combuster = next(w for w in weapons if w["name"] == "Weapon/Sword/Combuster")
    fire = [d for d in combuster["details"] if d["type"].endswith("StatusConditionConfig$Fire")]
    assert any(d["chance"] == 0.5 and d["power"] == 60 and d["damageInterval"] == 2000
               and d["minDuration"] == 4000 and d["maxDuration"] == 10000 for d in fire)
    assert any(d["type"].endswith("ExpressionConfig$Constant") and d["value"] == "5"
               for d in combuster["details"])
    assert combuster["incomplete_nodes"], "Missing detection of incomplete client actions"
    kinds = Counter(w["kind"] for w in weapons)
    incomplete = sum(bool(w["incomplete_nodes"]) for w in weapons)
    binding_log = (directory / "combat-import.log").read_text(encoding="utf-8-sig")
    binding = Counter(re.findall(r"Failed to find property\. \[name=([^,]+)", binding_log))
    lines = ["# Auditoría de acciones del arsenal congelado", "",
             "Versión 20261006114613. Análisis: 2026-10-11.", "",
             "Se recorrieron los ataques normales y cargados de las **374 armas** creables:", "",
             *[f"- {kind}: {count}." for kind, count in sorted(kinds.items())], "",
             f"Se encontraron **{len(counts)} tipos alcanzables**, sin referencias no resueltas",
             "dentro de las familias recorridas. Esto es alcance estático de configuraciones;",
             "no demuestra que el servidor ejecute esas acciones correctamente.", "",
             f"**{incomplete} armas** alcanzan nodos con actor nulo o condición Tagged vacía.",
             "Son indicios de configuraciones incompletas o placeholders del cliente, no una",
             "prueba de que vanilla contenga acciones inválidas. El importador aplica las",
             "restauraciones locales ya existentes; no se presenta este informe como un dump",
             "sin modificaciones del exportador original.", "",
             f"Advertencias de binding en esta ejecución: **{sum(binding.values())}** ({dict(binding)}).",
             "Por tanto, resolver una referencia no garantiza que todos sus argumentos",
             "hayan quedado aplicados. El log local conserva esos diagnósticos.", "",
             "## Dependencias por familia", "",
             "Cada cifra cuenta armas distintas que alcanzan el tipo, no instancias ni",
             "frecuencias de ejecución. Una misma arma puede aparecer en varias filas.", "",
             "| Tipo | Armas |", "| --- | ---: |"]
    categories = ("ActionConfig$", "HandlerConfig$", "ConditionConfig$", "ExpressionConfig$", "DamageConfig$", "StatusConditionConfig$")
    for kind, count in sorted(counts.items()):
        if any(category in kind for category in categories) and "$FireActionReference" not in kind and "$WeightedAction" not in kind:
            lines.append(f"| `{kind.rsplit('.', 1)[-1]}` | {count} |")
    lines += ["", "## Evidencia concreta de Combuster", "",
              "La referencia cargada resuelta llega a Fire 3 con chance=0.5, power=60,",
              "damageInterval=2000 ms, minDuration=4000 ms y maxDuration=10000 ms.",
              "El contador restaurado recibe Constant(\"5\"). La condición del temporizador",
              "sigue siendo Tagged con tag vacío y existe un Startup con SpawnActor.actor nulo.",
              "Estos datos no recuperan el algoritmo de probabilidad, resistencias, renovación",
              "o propagación. Tampoco validan la cadencia local de ráfaga de 100 ms.", "",
              "## Límites y reproducción", "",
              "Se siguen referencias tipadas de AttackConfig, FireActionConfig, ActorConfig,",
              "StatusConditionConfig y ParameterizedHandlerConfig, más objetos anidados.",
              "No se siguen efectos de medios, IA, señales resueltas en runtime ni referencias",
              "codificadas en cadenas. La deduplicación por identidad y referencia conserva",
              "la primera ruta a un objeto: no es un listado de todos los caminos posibles.", "",
              "El informe detallado, hashes de los catálogos y diagnósticos quedan locales en",
              "analysis/client-20261006114613/combat-catalog.json y combat-import.log.", "",
              "```powershell", "& .\\tools\\reverse\\reverse.ps1 -Action CombatAudit",
              "# Usar el Python indicado en tools/reverse/README.md:",
              "python .\\tools\\reverse\\report_combat.py --root .", "```", "",
              "La validación comprueba las 374 entradas únicas, hashes, coherencia de conteos,",
              "existencia de tipos en el índice JVM, resolución de referencias y los valores",
              "concretos anteriores. No sustituye test_server.bat ni la comparación visual.", ""]
    (root / "docs/COMBAT_ACTION_AUDIT.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"COMBAT CATALOG AUDIT PASSED: weapons=374 types={len(counts)} incompleteWeapons={incomplete}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    report(parser.parse_args().root.resolve())
