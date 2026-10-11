"""Export compact, authored metadata about the index; no decompiled game code."""
import collections
import json
from pathlib import Path
import sqlite3

root = Path(__file__).resolve().parents[2]
frozen = json.loads((root / "frozen-version.json").read_text(encoding="utf-8-sig"))
output = root / "analysis" / f"client-{frozen['version']}"
inventory = json.loads((output / "inventory.json").read_text())
db = sqlite3.connect((output / "index.sqlite").as_uri() + "?mode=ro", uri=True)
classes = [row[0] for row in db.execute("SELECT name FROM classes WHERE jar='code/projectx-pcode.jar'")]
game = collections.Counter(name.split(".")[3] for name in classes if name.startswith("com.threerings.projectx."))
engine = collections.Counter(".".join(name.split(".")[:3]) for name in classes if not name.startswith("com.threerings.projectx."))
report = {
    "frozen_version": frozen["version"],
    "primary_jar": next(item for item in inventory["jars"] if item["path"] == "code/projectx-pcode.jar"),
    "tool": json.loads((root / "tools/reverse/toolchain.json").read_text())["vineflower"],
    "counts": inventory["counts"],
    "decompilation": {key: inventory[key] for key in ("primary_classes", "classes_with_source_navigation", "java_source_files", "decompiler_error_markers")},
    "game_namespaces": dict(sorted(game.items())),
    "engine_and_embedded_libraries": dict(sorted(engine.items())),
    "config_catalog_files": len(list((root / "rsrc/config").glob("*.dat"))),
    "scene_files": len([file for file in (root / "scenes").iterdir() if file.is_file()]),
    "limits": inventory["limitations"],
}
report["decompilation"]["log_diagnostics"] = inventory.get("decompiler_log_diagnostics", {})
report["decompilation"]["missing_source_navigation"] = [row[0] for row in db.execute("SELECT name FROM classes WHERE jar='code/projectx-pcode.jar' AND source IS NULL ORDER BY name")]
(root / "docs/REVERSE_INVENTORY.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
