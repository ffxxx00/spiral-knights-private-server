import copy
import json
from pathlib import Path
import tempfile
import unittest
from telemetry_cases import compare, normalize
from check_clyde import declarations


class TelemetryTests(unittest.TestCase):
    def test_schema_scanner_nested_fields_multiple_declarations_and_comments(self):
        source = '''public class ActionConfig {
          public static final int UNUSED = 4;
          public int x, y;
          // class Ghost { public int fake; }
          public static class Child {
            public ConfigReference<ActorConfig> actor;
            public String text = "} class Fake {";
            public void execute() { int local = 0; }
          }
        }'''
        fields = declarations(source)
        self.assertEqual(fields["ActionConfig"], {"x": "int", "y": "int"})
        self.assertEqual(fields["ActionConfig$Child"], {"actor": "ConfigReference", "text": "String"})
    def experiment(self):
        return {"case_id": "controlled", "input_sha256": "inputs", "initial_state_sha256": "initial",
                "client_sha256": "client", "samples": [{"time_ms": 100, "actors": [
                    {"id": "enemy-1", "position": {"x": 1.0, "y": 2.0}, "health": 0.8}]}]}

    def test_comparison_detects_position_health_spawn_and_time(self):
        reference = self.experiment(); actual = copy.deepcopy(reference)
        self.assertTrue(compare(reference, actual)["matched"])
        actual["samples"][0]["actors"][0]["position"]["x"] += 0.005
        self.assertTrue(compare(reference, actual)["matched"])
        actual["samples"][0]["actors"][0]["health"] = 0.5
        actual["samples"][0]["actors"].append({"id": "unexpected"})
        actual["samples"][0]["time_ms"] = 120
        fields = {m["field"] for m in compare(reference, actual)["mismatches"]}
        self.assertEqual(fields, {"health", "actor_ids", "time_ms"})
        actual["samples"][0]["actors"][0]["position"]["x"] = 4
        self.assertIn("position", {m["field"] for m in compare(reference, actual)["mismatches"]})

    def test_rejects_different_inputs_missing_evidence_and_duplicate_ids(self):
        reference = self.experiment(); actual = copy.deepcopy(reference)
        actual["input_sha256"] = "different"
        with self.assertRaises(ValueError): compare(reference, actual)
        actual = copy.deepcopy(reference); actual["samples"] = []
        with self.assertRaises(ValueError): compare(reference, actual)
        actual = copy.deepcopy(reference); actual["samples"][0]["actors"] *= 2
        with self.assertRaises(ValueError): compare(reference, actual)

    def test_history_scene_boundaries_and_opaque_delta_blockers(self):
        frame = {"InputFrame._timestamp": 200, "InputFrame._flags": 1,
                 "InputFrame._rotation": 0, "InputFrame._direction": 0}
        earlier = dict(frame, **{"InputFrame._timestamp": 100})
        rows = [{"event": "recording_start", "schema": 1}]
        events = [("scene_context", {"scene_oid": 10}),
                  ("input_batch", {"scene_oid": 10, "frames": [frame, earlier]}),
                  ("input_batch", {"scene_oid": 10, "frames": [frame]}),
                  ("scene_delta", {"SceneDeltaEvent._sceneOid": 10, "SceneDeltaEvent._timestamp": 210,
                                   "SceneDeltaEvent._reference": 110, "SceneDeltaEvent._updatedActorDeltas": [{"truncated": True}]}),
                  ("scene_context", {"scene_oid": 20}),
                  ("input_batch", {"scene_oid": 20, "frames": [frame]})]
        rows += [dict(seq=i, t_ns=i * 1000000, event=kind, data=value) for i, (kind, value) in enumerate(events, 1)]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "capture.jsonl"
            source.write_text("\n".join(json.dumps(row) for row in rows), encoding="utf8")
            manifest = normalize(source, root / "out")
            self.assertEqual(len(manifest["cases"]), 2)
            first = json.loads((root / "out" / manifest["cases"][0]["file"]).read_text())
            self.assertEqual([f["timestamp"] for f in first["inputs"]], [100, 200])
            self.assertEqual(first["raw_input_frames"], 3)
            self.assertFalse(first["replay_ready"])
            self.assertEqual(first["markers"], {"truncated": 1})
            self.assertTrue(first["opaque_deltas"])
            self.assertFalse(manifest["recording_end"])


if __name__ == "__main__":
    unittest.main()
