"""Pinned upstream reference and public field compatibility against client bytecode.

This lightweight Java declaration scanner is not a compiler or a semantic proof.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import urllib.request


def declarations(source):
    # Mask comments/strings before counting braces; retain offsets.
    source = re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                    lambda match: " " * len(match[0]), source, flags=re.S)
    classes = []
    for match in re.finditer(r"\bclass\s+(\w+)[^{;]*\{", source):
        begin = match.end() - 1
        level, end = 1, begin + 1
        while level and end < len(source):
            level += (source[end] == "{") - (source[end] == "}")
            end += 1
        parent = [entry for entry in classes if entry[1] < begin < entry[2]]
        name = (max(parent, key=lambda entry: entry[1])[0] + "$" if parent else "") + match[1]
        classes.append((name, begin, end))
    fields = {name: {} for name, _, _ in classes}
    pattern = r"\bpublic\s+(?:(?:static|final|transient|volatile)\s+)*([\w.]+(?:<[^;{}=]+>)?(?:\[\])*)\s+(\w+(?:\s*,\s*\w+)*)\s*(?:=|;)"
    for match in re.finditer(pattern, source):
        if re.search(r"\bstatic\b", match[0]):
            continue
        owners = [entry for entry in classes if entry[1] < match.start() < entry[2]]
        if not owners:
            continue
        name, begin, _ = max(owners, key=lambda entry: entry[1])
        # Only class-body fields, excluding local declarations in method bodies.
        text = source[begin + 1:match.start()]
        if text.count("{") != text.count("}"):
            continue
        for name_field in match[2].split(","):
            fields[name][name_field.strip()] = re.sub(r"<.*>", "", match[1]).strip()
    return fields


def descriptor_type(descriptor):
    dimensions = len(descriptor) - len(descriptor.lstrip("["))
    value = descriptor[dimensions:]
    primitives = dict(B="byte", C="char", D="double", F="float", I="int", J="long", S="short", Z="boolean")
    name = value[1:-1].rsplit("/", 1)[-1].rsplit("$", 1)[-1] if value.startswith("L") else primitives.get(value, value)
    return name + "[]" * dimensions


def check(root, download=False):
    lock = json.loads((root / "tools/reverse/clyde-reference.json").read_text(encoding="utf-8-sig"))
    directory = root / "analysis/upstream/clyde" / lock["revision"]
    directory.mkdir(parents=True, exist_ok=True)
    for record in lock["files"]:
        path = directory / record["file"]
        if not path.exists() and download:
            path.write_bytes(urllib.request.urlopen(record["url"], timeout=30).read())
        if not path.exists():
            raise ValueError("Missing reference; rerun with --download: " + record["file"])
        if hashlib.sha256(path.read_bytes()).hexdigest() != record["sha256"]:
            raise ValueError("Reference hash mismatch: " + record["file"])
    target = root / "analysis/client-20261006114613/index.sqlite"
    report = {"revision": lock["revision"], "families": {}, "semantic_compatibility": "not established"}
    with sqlite3.connect(target.as_uri() + "?mode=ro", uri=True) as db:
        for family in ("Action", "Condition", "Expression", "Handler"):
            upstream = declarations((directory / (family + "Config.java")).read_text(encoding="utf8"))
            rows = db.execute("SELECT c.name,f.name,f.descriptor FROM classes c JOIN fields f ON f.class_id=c.id WHERE c.jar=? AND c.name LIKE ? AND (f.access & 1)!=0 AND (f.access & 8)=0",
                              ("code/projectx-pcode.jar", "com.threerings.tudey.config." + family + "Config%"))
            client = {}
            for owner, name, descriptor in rows:
                client.setdefault(owner.rsplit(".", 1)[-1], {})[name] = descriptor_type(descriptor)
            # Include classes that declare no public fields.
            for (owner,) in db.execute("SELECT name FROM classes WHERE jar=? AND name LIKE ?",
                                       ("code/projectx-pcode.jar", "com.threerings.tudey.config." + family + "Config%")):
                client.setdefault(owner.rsplit(".", 1)[-1], {})
            common = set(upstream) & set(client)
            changes = []
            for owner in sorted(common):
                for field in sorted(set(upstream[owner]) | set(client[owner])):
                    a, b = upstream[owner].get(field), client[owner].get(field)
                    if a != b:
                        changes.append({"class": owner, "field": field, "upstream": a, "client": b})
            report["families"][family] = {"common_classes": len(common), "upstream_only": sorted(set(upstream) - set(client)),
                                         "client_only": sorted(set(client) - set(upstream)), "field_differences": changes}
    (directory / "compatibility.json").write_text(json.dumps(report, indent=2), encoding="utf8")
    lines = ["# Evaluación inicial de Clyde/Tudey", "", f"Referencia fijada: `{lock['revision']}`.", "",
             "Se verificaron los hashes de ocho archivos originales. Sus fuentes y licencias",
             "permanecen locales en analysis/upstream/clyde/. No se añadieron al runtime.", "",
             "La comparación usa campos públicos de instancia de cuatro familias de configuración",
             "y los campos/descriptores del bytecode. El scanner de Java es limitado:",
             "esto identifica diferencias de esquema, no demuestra compatibilidad binaria,",
             "de comportamiento ni una compilación completa del servidor original.", "",
             "| Familia | Clases comunes | Solo upstream | Solo cliente | Diferencias de campos |",
             "| --- | ---: | ---: | ---: | ---: |"]
    for family, data in report["families"].items():
        lines.append(f"| {family} | {data['common_classes']} | {len(data['upstream_only'])} | {len(data['client_only'])} | {len(data['field_differences'])} |")
    lines += ["", "## Uso previsto", "",
              "ActionLogic, ConditionLogic, ExpressionLogic y HandlerLogic aportan referencias",
              "de semántica para acciones comunes. Primero contrastar las clases concretas y",
              "sus dependencias; después adaptar un contrato pequeño y comprobarlo con casos",
              "controlados. No sustituir el JAR por una biblioteca moderna.", "",
              "Las extensiones ProjectX de daño, estados, IA y progresión siguen requiriendo",
              "reconstrucción a partir del cliente, catálogos y resultados observados.", "",
              "Consultar el detalle local compatibility.json antes de escoger una familia.",
              "El manifiesto tools/reverse/clyde-reference.json conserva URLs y SHA-256.", ""]
    (root / "docs/CLYDE_COMPATIBILITY.md").write_text("\n".join(lines), encoding="utf8")
    print(json.dumps({family: {key: len(value) if isinstance(value, list) else value for key, value in data.items()}
                      for family, data in report["families"].items()}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()
    check(args.root.resolve(), args.download)
