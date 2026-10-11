"""Index JVM class files without executing the game or needing Python packages.

Call edges are instruction-level symbolic targets, not a resolved runtime graph.
Decompiler output is a navigation aid; JVM descriptors/bytecode remain authoritative.
"""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import struct
import sys
import time
import zipfile

SCHEMA_VERSION = 1


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, count):
        end = self.pos + count
        if end > len(self.data):
            raise ValueError("Truncated class file")
        value = self.data[self.pos:end]
        self.pos = end
        return value

    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack(">H", self.take(2))[0]
    def u4(self): return struct.unpack(">I", self.take(4))[0]


def mutf8(data):
    # Java encodes NUL specially and supplementary characters as surrogate pairs.
    value = data.replace(b"\xc0\x80", b"\0").decode("utf-8", "surrogatepass")
    return value.encode("utf-16", "surrogatepass").decode("utf-16", "replace")


class ClassFile:
    def __init__(self, data):
        reader = Reader(data)
        if reader.u4() != 0xCAFEBABE: raise ValueError("Not a JVM class file")
        self.minor, self.major = reader.u2(), reader.u2()
        size = reader.u2()
        self.cp = [None] * size
        index = 1
        while index < size:
            tag = reader.u1()
            if tag == 1: value = mutf8(reader.take(reader.u2()))
            elif tag in (3, 4): value = reader.take(4).hex()
            elif tag in (5, 6): value = reader.take(8).hex()
            elif tag in (7, 8, 16, 19, 20): value = reader.u2()
            elif tag in (9, 10, 11, 12, 17, 18): value = (reader.u2(), reader.u2())
            elif tag == 15: value = (reader.u1(), reader.u2())
            else: raise ValueError(f"Unknown constant pool tag {tag}")
            self.cp[index] = (tag, value)
            index += 2 if tag in (5, 6) else 1
        self.access = reader.u2()
        self.name = self.classname(reader.u2())
        self.parent = self.classname(reader.u2())
        self.interfaces = [self.classname(reader.u2()) for _ in range(reader.u2())]
        self.fields = self.members(reader)
        self.methods = self.members(reader)
        self.attrs = self.attributes(reader)
        if reader.pos != len(data): raise ValueError("Trailing class bytes")

    def utf(self, index): return self.cp[index][1]
    def classname(self, index): return self.utf(self.cp[index][1]).replace("/", ".") if index else None

    def memberref(self, index):
        tag, (owner, nt) = self.cp[index]
        name, descriptor = self.cp[nt][1]
        return self.classname(owner), self.utf(name), self.utf(descriptor)

    def attributes(self, reader):
        result = {}
        for _ in range(reader.u2()):
            name = self.utf(reader.u2())
            result[name] = reader.take(reader.u4())
        return result

    def members(self, reader):
        result = []
        for _ in range(reader.u2()):
            access, name, descriptor = reader.u2(), self.utf(reader.u2()), self.utf(reader.u2())
            result.append((access, name, descriptor, self.attributes(reader)))
        return result

    def instructions(self, code_attr):
        reader = Reader(code_attr)
        reader.take(4)  # max stack / locals
        code = reader.take(reader.u4())
        offset = 0
        while offset < len(code):
            opcode = code[offset]
            cp_index = None
            if 178 <= opcode <= 186:
                cp_index = struct.unpack_from(">H", code, offset + 1)[0]
            yield offset, opcode, cp_index
            if opcode in (170, 171):
                aligned = (offset + 4) & ~3
                if opcode == 170:
                    _, low, high = struct.unpack_from(">iii", code, aligned)
                    if high < low: raise ValueError("Invalid tableswitch")
                    offset = aligned + 12 + (high - low + 1) * 4
                else:
                    _, pairs = struct.unpack_from(">ii", code, aligned)
                    if pairs < 0: raise ValueError("Invalid lookupswitch")
                    offset = aligned + 8 + pairs * 8
            elif opcode == 196:
                offset += 6 if code[offset + 1] == 132 else 4
            elif opcode in (185, 186, 200, 201): offset += 5
            elif opcode == 197: offset += 4
            elif opcode in (17, 19, 20, 132, 187, 189, 192, 193, 198, 199) or 153 <= opcode <= 168 or 178 <= opcode <= 184:
                offset += 3
            elif opcode in (16, 18, 169, 188) or 21 <= opcode <= 25 or 54 <= opcode <= 58:
                offset += 2
            else: offset += 1
        if offset != len(code): raise ValueError("Invalid instruction boundary")

    def bootstrap_handles(self, cp_index):
        tag, (bootstrap, _) = self.cp[cp_index]
        if tag != 18: return []
        reader = Reader(self.attrs.get("BootstrapMethods", b"\0\0"))
        handles = []
        for index in range(reader.u2()):
            method = reader.u2()
            args = [reader.u2() for _ in range(reader.u2())]
            if index != bootstrap: continue
            for candidate in [method] + args:
                if self.cp[candidate][0] == 15:
                    _, ref = self.cp[candidate][1]
                    if self.cp[ref][0] in (10, 11): handles.append(self.memberref(ref))
        return handles


DDL = """
CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL);
CREATE TABLE jars(path TEXT PRIMARY KEY,sha256 TEXT,bytes INTEGER,class_count INTEGER);
CREATE TABLE classes(id INTEGER PRIMARY KEY,jar TEXT,entry TEXT,name TEXT,parent TEXT,interfaces TEXT,
 access INTEGER,major INTEGER,sha256 TEXT,source TEXT,UNIQUE(jar,entry));
CREATE INDEX class_name ON classes(name);
CREATE TABLE methods(id INTEGER PRIMARY KEY,class_id INTEGER,name TEXT,descriptor TEXT,
 access INTEGER,code_bytes INTEGER,UNIQUE(class_id,name,descriptor));
CREATE INDEX method_names ON methods(name);
CREATE TABLE fields(class_id INTEGER,name TEXT,descriptor TEXT,access INTEGER);
CREATE TABLE calls(method_id INTEGER,offset INTEGER,kind TEXT,target_owner TEXT,target_name TEXT,target_descriptor TEXT);
CREATE INDEX call_target ON calls(target_owner,target_name,target_descriptor);
CREATE INDEX call_source ON calls(method_id);
CREATE TABLE field_access(method_id INTEGER,offset INTEGER,kind TEXT,target_owner TEXT,target_name TEXT,target_descriptor TEXT);
CREATE INDEX field_target ON field_access(target_owner,target_name);
CREATE TABLE strings(class_id INTEGER,value TEXT);
CREATE TABLE class_refs(class_id INTEGER,target TEXT);
CREATE INDEX ref_target ON class_refs(target);
"""
INVOKES = {182: "invokevirtual", 183: "invokespecial", 184: "invokestatic", 185: "invokeinterface"}
FIELDS = {178: "getstatic", 179: "putstatic", 180: "getfield", 181: "putfield"}


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""): result.update(chunk)
    return result.hexdigest()


def locations(root):
    frozen = json.loads((root / "frozen-version.json").read_text(encoding="utf-8-sig"))
    return frozen, root / "analysis" / f"client-{frozen['version']}"


def find_source(root, output, name):
    # '$' is also a legal character in a top-level JVM name. Vineflower may
    # emit anonymous classes separately instead of merging them into their owner.
    for owner in (name, name.split("$")[0]):
        candidate = output / "sources" / (owner.replace(".", "/") + ".java")
        if candidate.is_file(): return candidate.relative_to(root).as_posix()
    return None


def source_inventory(db, root, output, summary):
    for class_id, name in db.execute("SELECT id,name FROM classes").fetchall():
        relative = find_source(root, output, name)
        db.execute("UPDATE classes SET source=? WHERE id=?", (relative, class_id))
    sources = sorted((output / "sources").rglob("*.java"))
    markers = []
    for source in sources:
        for line_number, line in enumerate(source.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if "Couldn't be decompiled" in line or "$VF: Couldn't" in line:
                markers.append({"path": source.relative_to(root).as_posix(), "line": line_number, "message": line.strip()})
    summary.update(classes_with_source_navigation=db.execute("SELECT count(*) FROM classes WHERE jar='code/projectx-pcode.jar' AND source IS NOT NULL").fetchone()[0],
                   java_source_files=len(sources), decompiler_error_markers=len(markers))
    log = output / "decompile.log"
    lines = log.read_text(encoding="utf-8", errors="replace").splitlines() if log.is_file() else []
    summary["decompiler_log_diagnostics"] = {level: sum(line.startswith(level + ":") for line in lines) for level in ("WARN", "ERROR")}
    (output / "inventory.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    (output / "decompiler-errors.json").write_text(json.dumps(markers, indent=2) + "\n", encoding="utf-8")
    db.commit()


def build(root, args):
    start = time.monotonic()
    frozen, output = locations(root)
    for record in frozen["files"]:
        if digest(root / record["path"]).lower() != record["sha256"].lower():
            raise ValueError(f"Frozen input changed: {record['path']}")
    output.mkdir(parents=True, exist_ok=True)
    db_path = output / "index.sqlite"
    script_hash = digest(Path(__file__))
    if db_path.is_file():
        cache = sqlite3.connect(db_path)
        metadata = dict(cache.execute("SELECT key,value FROM meta"))
        cached_jars = dict(cache.execute("SELECT path,sha256 FROM jars"))
        current_jars = {jar.relative_to(root).as_posix(): digest(jar) for jar in (root / "code").glob("*.jar")}
        if metadata.get("indexer_sha256") == script_hash and cached_jars == current_jars:
            summary = json.loads((output / "inventory.json").read_text())
            summary["bytecode_cache_reused"] = True
            source_inventory(cache, root, output, summary)
            cache.close()
            print(json.dumps({key: value for key, value in summary.items() if key not in ("packages", "jars", "limitations")}, indent=2))
            return
        cache.close()
    temporary = output / "index.build.sqlite"
    if temporary.exists(): temporary.unlink()
    db = sqlite3.connect(temporary)
    db.executescript(DDL)
    db.execute("INSERT INTO meta VALUES('schema',?)", (str(SCHEMA_VERSION),))
    db.execute("INSERT INTO meta VALUES('version',?)", (frozen["version"],))
    db.execute("INSERT INTO meta VALUES('indexer_sha256',?)", (script_hash,))
    artifacts = []
    for jar in sorted((root / "code").glob("*.jar")):
        relative = jar.relative_to(root).as_posix()
        with zipfile.ZipFile(jar) as archive:
            entries = sorted(n for n in archive.namelist() if n.endswith(".class"))
            db.execute("INSERT INTO jars VALUES(?,?,?,?)", (relative, digest(jar), jar.stat().st_size, len(entries)))
            artifacts.append({"path": relative, "sha256": digest(jar), "classes": len(entries)})
            for entry in entries:
                data = archive.read(entry)
                try: cls = ClassFile(data)
                except Exception as error: raise ValueError(f"Failed to index {relative}:{entry}: {error}") from error
                source_path = find_source(root, output, cls.name)
                class_id = db.execute("INSERT INTO classes(jar,entry,name,parent,interfaces,access,major,sha256,source) VALUES(?,?,?,?,?,?,?,?,?)",
                    (relative, entry, cls.name, cls.parent, json.dumps(cls.interfaces), cls.access, cls.major, hashlib.sha256(data).hexdigest(), source_path)).lastrowid
                for access, name, descriptor, _ in cls.fields:
                    db.execute("INSERT INTO fields VALUES(?,?,?,?)", (class_id, name, descriptor, access))
                for access, name, descriptor, attrs in cls.methods:
                    code = attrs.get("Code")
                    code_bytes = struct.unpack_from(">I", code, 4)[0] if code else 0
                    method_id = db.execute("INSERT INTO methods(class_id,name,descriptor,access,code_bytes) VALUES(?,?,?,?,?)", (class_id, name, descriptor, access, code_bytes)).lastrowid
                    if not code: continue
                    for offset, opcode, cp_index in cls.instructions(code):
                        if opcode in INVOKES:
                            db.execute("INSERT INTO calls VALUES(?,?,?,?,?,?)", (method_id, offset, INVOKES[opcode], *cls.memberref(cp_index)))
                        elif opcode in FIELDS:
                            db.execute("INSERT INTO field_access VALUES(?,?,?,?,?,?)", (method_id, offset, FIELDS[opcode], *cls.memberref(cp_index)))
                        elif opcode == 186:
                            for target in cls.bootstrap_handles(cp_index):
                                db.execute("INSERT INTO calls VALUES(?,?,?,?,?,?)", (method_id, offset, "dynamic_bootstrap", *target))
                for constant in cls.cp[1:]:
                    if not constant: continue
                    tag, value = constant
                    if tag == 8: db.execute("INSERT INTO strings VALUES(?,?)", (class_id, cls.utf(value)))
                    elif tag == 7:
                        db.execute("INSERT INTO class_refs VALUES(?,?)", (class_id, cls.utf(value).replace("/", ".")))
        db.commit()
        print(json.dumps({"indexed": relative, "classes": len(entries)}), flush=True)
    counts = {table: db.execute(f"SELECT count(*) FROM {table}").fetchone()[0]
              for table in ("classes", "methods", "fields", "calls", "field_access", "strings", "class_refs")}
    packages = db.execute("SELECT name FROM classes WHERE jar='code/projectx-pcode.jar'").fetchall()
    package_counts = collections.Counter(name.rsplit(".", 1)[0] for (name,) in packages)
    sources = sorted((output / "sources").rglob("*.java"))
    markers = []
    for source in sources:
        for line_number, line in enumerate(source.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if "Couldn't be decompiled" in line or "$VF: Couldn't" in line:
                markers.append({"path": source.relative_to(root).as_posix(), "line": line_number, "message": line.strip()})
    primary_classes = db.execute("SELECT count(*) FROM classes WHERE jar='code/projectx-pcode.jar'").fetchone()[0]
    navigation = db.execute("SELECT count(*) FROM classes WHERE jar='code/projectx-pcode.jar' AND source IS NOT NULL").fetchone()[0]
    summary = {"schema": SCHEMA_VERSION, "frozen_version": frozen["version"], "counts": counts,
               "primary_classes": primary_classes, "classes_with_source_navigation": navigation,
               "java_source_files": len(sources), "decompiler_error_markers": len(markers),
               "elapsed_seconds": round(time.monotonic() - start, 2), "jars": artifacts,
               "packages": dict(sorted(package_counts.items())),
               "limitations": ["Symbolic bytecode calls; virtual dispatch, reflection and network events need separate evidence.",
                               "dynamic_bootstrap edges include bootstrap factories and method handles, not proven runtime calls.",
                               "Source navigation maps nested classes to outer source; presence does not prove decompilation correctness."]}
    summary["bytecode_cache_reused"] = False
    source_inventory(db, root, output, summary)
    db.commit()
    db.close()
    temporary.replace(db_path)
    print(json.dumps({key: value for key, value in summary.items() if key not in ("packages", "jars", "limitations")}, indent=2))


def query(root, args):
    _, output = locations(root)
    db_path = output / "index.sqlite"
    if not db_path.is_file(): raise ValueError("Run build first")
    db = sqlite3.connect(db_path.as_uri() + "?mode=ro", uri=True)
    db.row_factory = sqlite3.Row
    owner = args.term.replace("/", ".") if getattr(args, "term", None) else None
    if args.command == "stats":
        summary = json.loads((output / "inventory.json").read_text())
        print(json.dumps({key: value for key, value in summary.items() if key not in ("packages", "jars")}, indent=2))
        return
    params = []
    if args.command == "find":
        pattern = "%" + owner + "%"
        queries = {
            "classes": ("SELECT jar,entry,name,parent,source FROM classes WHERE name LIKE ? ORDER BY name", [pattern]),
            "methods": ("SELECT c.name AS owner,m.name,m.descriptor,m.code_bytes,c.source FROM methods m JOIN classes c ON c.id=m.class_id WHERE m.name LIKE ? OR m.descriptor LIKE ? ORDER BY c.name,m.name", [pattern, pattern]),
            "fields": ("SELECT c.name AS owner,f.name,f.descriptor FROM fields f JOIN classes c ON c.id=f.class_id WHERE f.name LIKE ? OR f.descriptor LIKE ? ORDER BY c.name,f.name", [pattern, pattern]),
            "strings": ("SELECT c.name AS owner,s.value,c.source FROM strings s JOIN classes c ON c.id=s.class_id WHERE s.value LIKE ? ORDER BY c.name,s.value", [pattern]),
        }
        sql, params = queries[args.kind]
    elif args.command == "class":
        classes = [dict(row) for row in db.execute("SELECT * FROM classes WHERE name=? ORDER BY jar", [owner])]
        for cls in classes:
            cls["methods"] = [dict(row) for row in db.execute("SELECT name,descriptor,access,code_bytes FROM methods WHERE class_id=? ORDER BY name,descriptor", [cls["id"]])]
            cls["fields"] = [dict(row) for row in db.execute("SELECT name,descriptor,access FROM fields WHERE class_id=?", [cls["id"]])]
        print(json.dumps(classes, indent=2))
        return
    elif args.command in ("calls", "callers"):
        sql = "SELECT c.name AS owner,m.name AS method,m.descriptor,r.offset,r.kind,r.target_owner,r.target_name,r.target_descriptor,c.source FROM calls r JOIN methods m ON m.id=r.method_id JOIN classes c ON c.id=m.class_id WHERE "
        prefix = "c.name" if args.command == "calls" else "r.target_owner"
        method = "m.name" if args.command == "calls" else "r.target_name"
        desc = "m.descriptor" if args.command == "calls" else "r.target_descriptor"
        sql += prefix + "=?"
        params = [owner]
        if args.method: sql += " AND " + method + "=?"; params.append(args.method)
        if args.descriptor: sql += " AND " + desc + "=?"; params.append(args.descriptor)
        sql += " ORDER BY c.name,m.name,m.descriptor,r.offset"
    elif args.command == "field-uses":
        sql = "SELECT c.name AS owner,m.name AS method,m.descriptor,r.offset,r.kind,r.target_owner,r.target_name,r.target_descriptor,c.source FROM field_access r JOIN methods m ON m.id=r.method_id JOIN classes c ON c.id=m.class_id WHERE r.target_owner=?"
        params = [owner]
        if args.method: sql += " AND r.target_name=?"; params.append(args.method)
        sql += " ORDER BY c.name,m.name,r.offset"
    elif args.command == "references":
        sql = "SELECT DISTINCT c.name AS owner,c.jar,c.source FROM class_refs r JOIN classes c ON c.id=r.class_id WHERE r.target=? ORDER BY c.name"
        params = [owner]
    else: raise ValueError(args.command)
    total = db.execute("SELECT count(*) FROM (" + sql + ")", params).fetchone()[0]
    rows = [dict(row) for row in db.execute(sql + " LIMIT ?", params + [args.limit])]
    print(json.dumps({"total": total, "shown": len(rows), "rows": rows}, indent=2))
    db.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="command", required=True)
    for command in ("build", "stats", "find", "class", "calls", "callers", "field-uses", "references"):
        sub = subs.add_parser(command)
        sub.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
        sub.add_argument("--limit", type=int, default=20)
        if command not in ("build", "stats"): sub.add_argument("term")
        if command == "find": sub.add_argument("--kind", choices=("classes", "methods", "fields", "strings"), default="classes")
        if command in ("calls", "callers", "field-uses"):
            sub.add_argument("--method")
            sub.add_argument("--descriptor")
    args = parser.parse_args()
    if not 1 <= args.limit <= 200: parser.error("--limit must be between 1 and 200")
    root = args.root.resolve()
    try:
        if args.command == "build": build(root, args)
        else: query(root, args)
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__": sys.exit(main())
