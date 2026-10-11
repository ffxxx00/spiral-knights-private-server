"""Bounded, resumable Vineflower batches for machines with limited RAM."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import zipfile

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--force", action="store_true")
parser.add_argument("--batch-size", type=int, default=350)
args = parser.parse_args()
if not 50 <= args.batch_size <= 1000: parser.error("batch-size must be 50..1000")
frozen = json.loads((root / "frozen-version.json").read_text(encoding="utf-8-sig"))
tool = json.loads((root / "tools/reverse/toolchain.json").read_text())["vineflower"]
output = root / "analysis" / f"client-{frozen['version']}"
work = output / "batches"
work.mkdir(parents=True, exist_ok=True)
sources = output / "sources"
sources.mkdir(parents=True, exist_ok=True)
jar = root / "code/projectx-pcode.jar"
tool_jar = root / f"tools/reverse/vendor/vineflower-{tool['version']}.jar"
signature = hashlib.sha256(jar.read_bytes()).hexdigest() + ":" + tool["sha256"] + ":768m-2threads-runtime0-v1:" + str(args.batch_size)
if hashlib.sha256(tool_jar.read_bytes()).hexdigest() != tool["sha256"]:
    raise SystemExit("Decompiler SHA256 mismatch")
for record in frozen["files"]:
    if hashlib.sha256((root / record["path"]).read_bytes()).hexdigest().lower() != record["sha256"].lower():
        raise SystemExit("Frozen input changed: " + record["path"])
stamp = output / "decompile.stamp"
records = []
with zipfile.ZipFile(jar) as archive:
    families = {}
    for name in sorted(n for n in archive.namelist() if n.endswith(".class")):
        families.setdefault(name.split("$")[0].removesuffix(".class"), []).append(name)
    batches, current = [], []
    for family in families.values():
        if current and len(current) + len(family) > args.batch_size:
            batches.append(current); current = []
        current.extend(family)
    if current: batches.append(current)
    start = time.monotonic()
    for number, entries in enumerate(batches, 1):
        batch = work / f"batch-{number:03d}.jar"
        done = work / f"batch-{number:03d}.stamp"
        log = work / f"batch-{number:03d}.log"
        batch_signature = signature + ":" + hashlib.sha256("\n".join(entries).encode()).hexdigest()
        sources_present = all((sources / entry.removesuffix('.class')).with_suffix('.java').is_file()
            or (sources / (entry.split('$')[0].removesuffix('.class') + '.java')).is_file() for entry in entries)
        if not args.force and sources_present and done.is_file() and done.read_text().strip() == batch_signature:
            records.append({"batch": number, "classes": len(entries), "cached": True})
            print(json.dumps(records[-1]), flush=True)
            continue
        with zipfile.ZipFile(batch, "w", zipfile.ZIP_DEFLATED) as shard:
            for entry in entries: shard.writestr(entry, archive.read(entry))
        command = [str(root / "jdk/bin/java.exe"), "-Xmx768m", "-jar", str(tool_jar),
                   "--folder", "--log-level=warn", "--thread-count=2", "--include-runtime=0"]
        # The full frozen JAR supplies signatures for classes outside this shard.
        # External dependencies are read as libraries, never executed/decompiled here.
        command += ["-e=" + str(library) for library in sorted((root / "code").glob("*.jar"))]
        command += [str(batch), str(sources)]
        print(json.dumps({"starting_batch": number, "of": len(batches), "classes": len(entries)}), flush=True)
        batch_start = time.monotonic()
        with log.open("wb") as stream:
            result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, cwd=root, timeout=300)
        if result.returncode:
            raise SystemExit(f"Batch {number} failed with exit {result.returncode}; inspect {log}")
        done.write_text(batch_signature + "\n", encoding="ascii")
        records.append({"batch": number, "classes": len(entries), "cached": False,
                        "seconds": round(time.monotonic() - batch_start, 2)})
        print(json.dumps(records[-1]), flush=True)
    stamp.write_text(signature + "\n", encoding="ascii")
    (output / "decompile-batches.json").write_text(json.dumps({"signature": signature, "batches": records,
        "seconds": round(time.monotonic() - start, 2)}, indent=2) + "\n")
    with (output / "decompile.log").open("w", encoding="utf-8") as combined:
        for number in range(1, len(batches) + 1):
            log = work / f"batch-{number:03d}.log"
            combined.write(f"\n--- Batch {number} ---\n")
            if log.is_file(): combined.write(log.read_text(encoding="utf-8", errors="replace"))
    print(json.dumps({"completed_batches": len(batches), "classes": sum(map(len, batches)),
                      "java_sources": len(list(sources.rglob('*.java')))}), flush=True)
