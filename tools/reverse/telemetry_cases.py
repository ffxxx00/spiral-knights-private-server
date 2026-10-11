"""Offline normalization and comparison; opaque reflective deltas are never guessed."""
import argparse
from collections import Counter
import hashlib
import json
import math
import shutil
from pathlib import Path


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def actor(value):
    if not isinstance(value, dict) or "Actor._id" not in value:
        return None
    result = {"id": value["Actor._id"], "class": value.get("class")}
    fields = {"Actor._translation": "position", "Actor._rotation": "rotation",
              "Actor._flags": "flags", "Actor._created": "created", "Actor._destroyed": "destroyed",
              "Active._activity": "activity", "Active._activityStarted": "activity_started",
              "Monster._healthPct": "health", "Dungeoneer._healthPct": "health"}
    for source, target in fields.items():
        if source in value:
            item = value[source]
            if source == "Actor._translation" and isinstance(item, dict):
                item = {k: item.get("Vector2f." + k) for k in ("x", "y")}
            result[target] = item
    reference = value.get("Actor._config")
    if isinstance(reference, dict):
        result["config"] = reference.get("ConfigReference._name")
    return result


def markers(value):
    if isinstance(value, dict):
        yield from (key for key in ("truncated", "cycle") if value.get(key) is True)
        for child in value.values():
            yield from markers(child)
    elif isinstance(value, list):
        for child in value:
            yield from markers(child)


def source_lines(path):
    with path.open(encoding="utf-8-sig") as stream:
        yield from enumerate(stream, 1)


def normalize(source: Path, output: Path):
    counts, flags = Counter(), Counter()
    segments, current, context = [], None, None
    start = end = False
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    output.mkdir(parents=True, exist_ok=True)
    local_source = output / ("source-" + source_hash + ".jsonl")
    if not local_source.exists():
        shutil.copyfile(source, local_source)
    if hashlib.sha256(local_source.read_bytes()).hexdigest() != source_hash:
        raise ValueError("Local capture hash mismatch")
    source_name = source.name
    source = local_source
    previous_seq = previous_time = -1
    for number, line in source_lines(source):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except ValueError as error:
            raise ValueError(f"Invalid JSON at line {number}") from error
        kind, value = row.get("event"), row.get("data")
        counts[kind] += 1
        flags.update(set(markers(row)))  # Counts affected records, not nested markers.
        if kind == "recording_start":
            start = True
        if kind == "recording_end":
            end = True
        if "seq" in row:
            if row["seq"] <= previous_seq or row["t_ns"] < previous_time:
                raise ValueError(f"Non-monotonic recorder at line {number}")
            previous_seq, previous_time = row["seq"], row["t_ns"]
        if kind == "scene_context":
            next_context = value if isinstance(value, dict) else None
            if next_context != context:
                current = None
            context = next_context
        if not isinstance(value, (dict, list)):
            continue
        scene = context.get("scene_oid") if isinstance(context, dict) else None
        if kind == "scene_delta":
            scene = value.get("SceneDeltaEvent._sceneOid", scene)
        elif kind == "input_batch":
            scene = value.get("scene_oid") or scene
        if scene is None or kind not in ("scene_delta", "input_batch", "actor_snapshot", "scene_entries", "level_rpc"):
            continue
        if current is None or current["scene_oid"] != scene:
            current = {"segment": len(segments), "scene_oid": scene, "context": context,
                       "first_t_ns": row["t_ns"], "inputs": [], "observations": [],
                       "opaque_deltas": [], "initial_actors": [], "delta_chain_breaks": [],
                       "markers": Counter(),
                       "server_origin": None, "last_delta": None}
            segments.append(current)
        observed_ms = (row["t_ns"] - current["first_t_ns"]) / 1e6
        current["markers"].update(set(markers(row)))
        if kind == "input_batch":
            for frame in value.get("frames", []):
                required = ["InputFrame._timestamp", "InputFrame._rotation", "InputFrame._direction", "InputFrame._flags"]
                if not isinstance(frame, dict) or any(key not in frame for key in required):
                    raise ValueError(f"Incomplete input at line {number}")
                current["inputs"].append({"timestamp": frame[required[0]], "rotation": frame[required[1]],
                                          "direction": frame[required[2]], "flags": frame[required[3]],
                                          "action_changed": frame.get("ProjectXInputFrame._actionChanged")})
        elif kind == "scene_delta":
            timestamp, reference = value.get("SceneDeltaEvent._timestamp"), value.get("SceneDeltaEvent._reference")
            if current["server_origin"] is None:
                current["server_origin"] = timestamp
            if current["last_delta"] is not None and reference != current["last_delta"]:
                current["delta_chain_breaks"].append({"seq": row["seq"], "expected": current["last_delta"], "actual": reference})
            current["last_delta"] = timestamp
            added = [a for a in (actor(v) for v in value.get("SceneDeltaEvent._addedActors") or []) if a]
            if not current["initial_actors"]:
                current["initial_actors"] = added
            deltas = value.get("SceneDeltaEvent._updatedActorDeltas") or []
            if deltas:
                current["opaque_deltas"].append({"seq": row["seq"], "timestamp": timestamp, "deltas": deltas})
            current["observations"].append({"kind": "delta", "seq": row["seq"], "observed_ms": observed_ms,
                                            "timestamp": timestamp, "reference": reference, "added": added,
                                            "removed": value.get("SceneDeltaEvent._removedActorIds") or [],
                                            "effects": value.get("SceneDeltaEvent._effectsFired") or []})
        elif kind == "actor_snapshot":
            current["observations"].append({"kind": "snapshot", "seq": row["seq"], "observed_ms": observed_ms,
                                            "actors": [a for a in (actor(v) for v in value) if a]})
        else:
            current["observations"].append({"kind": kind, "seq": row["seq"], "observed_ms": observed_ms, "data": value})
    if not start or not segments:
        raise ValueError("Expected a MissionRecorder capture with recording_start and scene events")
    output.mkdir(parents=True, exist_ok=True)
    manifest = {"schema": 1, "source_sha256": source_hash, "source_name": source_name, "local_source": local_source.name,
                "capture_client_version": None, "capture_client_sha256": None,
                "client_target": "20261006114613", "events": dict(counts), "markers": dict(flags),
                "recording_start": start, "recording_end": end, "cases": []}
    for segment in segments:
        raw_count = len(segment["inputs"])
        # Input batches resend history. Deduplicate exact frames, retaining conflicts.
        unique = {digest(frame): frame for frame in segment["inputs"]}
        segment["inputs"] = sorted(unique.values(), key=lambda f: f["timestamp"])
        by_time = Counter(f["timestamp"] for f in segment["inputs"])
        segment["conflicting_input_timestamps"] = [t for t, count in by_time.items() if count > 1]
        segment["raw_input_frames"] = raw_count
        segment["input_sha256"] = digest(segment["inputs"])
        segment["case_id"] = f"{source_hash[:12]}-scene-{segment['segment']:03d}"
        # Preserve clock domains and real IDs until the delta class/field mapping is known.
        segment["clock_policy"] = "relative observation clock; input/server timestamps preserved separately"
        segment["replay_ready"] = False
        segment["blockers"] = ["capture client version/hash unknown", "scene assets and full initial state not bound"]
        if segment["opaque_deltas"]:
            segment["blockers"].append("ReflectiveDelta class identity/field ordering unavailable")
        if segment["delta_chain_breaks"]:
            segment["blockers"].append("delta chain discontinuity")
        if segment["conflicting_input_timestamps"]:
            segment["blockers"].append("conflicting input history")
        if segment["markers"]:
            segment["blockers"].append("truncated or cyclic serialized state")
        name = segment["case_id"] + ".json"
        (output / name).write_text(json.dumps(segment, ensure_ascii=False, indent=2), encoding="utf-8")
        manifest["cases"].append({"file": name, "inputs": len(segment["inputs"]),
                                   "observations": len(segment["observations"]), "blockers": segment["blockers"]})
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def compare(expected, actual, position_tolerance=0.01, time_tolerance=1):
    """Compare explicit authoritative samples emitted by a future case runner."""
    if not all(math.isfinite(value) and value >= 0 for value in (position_tolerance, time_tolerance)):
        raise ValueError("Invalid comparison tolerance")
    for key in ("case_id", "input_sha256", "initial_state_sha256", "client_sha256"):
        if not expected.get(key) or expected.get(key) != actual.get(key):
            raise ValueError(f"Different or missing experiment identity: {key}")
    left, right = expected.get("samples", []), actual.get("samples", [])
    if not left or not right:
        raise ValueError("Missing authoritative samples")
    for samples in (left, right):
        previous = -math.inf
        for sample in samples:
            time = sample.get("time_ms")
            if not isinstance(time, (int, float)) or not math.isfinite(time) or time <= previous:
                raise ValueError("Invalid authoritative clock")
            previous = time
            ids = [str(value["id"]) for value in sample["actors"]]
            if len(ids) != len(set(ids)):
                raise ValueError("Duplicate actor identity")
    mismatches = []
    if len(left) != len(right):
        mismatches.append({"field": "sample_count", "expected": len(left), "actual": len(right)})
    for index, (a, b) in enumerate(zip(left, right)):
        if abs(a["time_ms"] - b["time_ms"]) > time_tolerance:
            mismatches.append({"sample": index, "field": "time_ms", "expected": a["time_ms"], "actual": b["time_ms"]})
        aa, bb = {str(v["id"]): v for v in a["actors"]}, {str(v["id"]): v for v in b["actors"]}
        if aa.keys() != bb.keys():
            mismatches.append({"sample": index, "field": "actor_ids", "expected": sorted(aa), "actual": sorted(bb)})
        for identity in aa.keys() & bb.keys():
            for field in aa[identity].keys() | bb[identity].keys():
                x, y = aa[identity].get(field), bb[identity].get(field)
                present = field in aa[identity] and field in bb[identity]
                equal = present and x == y
                if field == "position" and isinstance(x, dict) and isinstance(y, dict):
                    equal = present and all(isinstance(x.get(axis), (int, float)) and isinstance(y.get(axis), (int, float))
                                and abs(x[axis] - y[axis]) <= position_tolerance for axis in ("x", "y"))
                if not equal:
                    mismatches.append({"sample": index, "actor": identity, "field": field, "expected": x, "actual": y})
    return {"matched": not mismatches, "samples_compared": min(len(left), len(right)), "mismatches": mismatches}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    normal = commands.add_parser("normalize")
    normal.add_argument("source", type=Path); normal.add_argument("output", type=Path)
    comparison = commands.add_parser("compare")
    comparison.add_argument("expected", type=Path); comparison.add_argument("actual", type=Path)
    comparison.add_argument("--position-tolerance", type=float, default=0.01)
    args = parser.parse_args()
    if args.command == "normalize":
        result = normalize(args.source, args.output)
        print(json.dumps({"cases": len(result["cases"]), "events": result["events"], "markers": result["markers"]}))
    else:
        if args.position_tolerance < 0:
            parser.error("Position tolerance must be non-negative")
        result = compare(json.loads(args.expected.read_text()), json.loads(args.actual.read_text()), args.position_tolerance)
        print(json.dumps(result, indent=2))
        raise SystemExit(0 if result["matched"] else 1)
