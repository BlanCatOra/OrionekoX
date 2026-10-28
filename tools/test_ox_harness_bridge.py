#!/usr/bin/env python3
"""Protocol and normalization tests for tools/ox-harness-bridge.py.

Run: python3 -m unittest tools/test_ox_harness_bridge.py -v
No external harness is contacted; fake is in-process and real CLI parsers are
fed recorded event shapes.
"""

import importlib.util
import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "ox_harness_bridge", ROOT / "ox-harness-bridge.py")
bridge = importlib.util.module_from_spec(spec)
sys.modules["ox_harness_bridge"] = bridge
spec.loader.exec_module(bridge)


class BridgeHttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.token = "test-token-0123456789"
        args = bridge.parse_args([
            "--host", "127.0.0.1", "--port", "0",
            "--workdir", cls.tmp.name,
            "--allow-root", cls.tmp.name,
            "--state-dir", cls.tmp.name + "/state",
            "--token", cls.token, "--enable-fake",
            "--turn-timeout", "20", "--compact-timeout", "20",
        ])
        cls.bridge = bridge.Bridge(args)
        cls.server = bridge.BridgeHTTPServer(("127.0.0.1", 0), cls.bridge)
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        bridge.OPENCODE_SERVER.stop_all()
        cls.tmp.cleanup()

    def request(self, method, path, body=None, token=None, raw=False):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(
            f"http://127.0.0.1:{self.port}{path}", data=data, method=method,
            headers={"Content-Type": "application/json",
                     "Authorization": "Bearer " + (token or self.token)})
        with urllib.request.urlopen(req, timeout=20) as resp:
            payload = resp.read()
            return resp.status, (payload if raw else json.loads(payload or b"{}"))

    def create(self, harness="fake", **extra):
        body = {"harness": harness, "cwd": self.tmp.name}
        body.update(extra)
        code, obj = self.request("POST", "/v1/sessions", body)
        self.assertEqual(code, 201, obj)
        return obj["session"]

    def turn(self, sid, text, attachments=None):
        body = {"text": text}
        if attachments:
            body["attachments"] = attachments
        code, raw = self.request("POST", f"/v1/sessions/{sid}/turns", body, raw=True)
        self.assertEqual(code, 200)
        return [json.loads(line) for line in raw.decode().splitlines() if line.strip()]

    def test_health_requires_token(self):
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.request("GET", "/v1/health", token="wrong")
        self.assertEqual(ctx.exception.code, 401)
        code, obj = self.request("GET", "/v1/health")
        self.assertEqual(code, 200)
        self.assertTrue(obj["ok"])
        self.assertEqual(obj["bridge"], "orionekox-harness-bridge")
        self.assertIn("fake", obj["harnesses"])
        self.assertTrue(obj["harnesses"]["fake"]["found"])

    def test_fake_turn_events_and_done(self):
        sess = self.create()
        events = self.turn(sess["session_id"], "hello")
        kinds = [e["type"] for e in events]
        self.assertIn("text", kinds)
        self.assertIn("tool_call", kinds)
        self.assertIn("tool_result", kinds)
        self.assertEqual(kinds[-1], "done")
        self.assertEqual(events[0]["type"], "session")
        done = events[-1]
        self.assertEqual(done["text"], "fake:hello")
        self.assertEqual(done["native_session"], sess["session_id"])
        self.assertIn("context", done)
        self.assertEqual(done["context"]["prompt_tokens"], len("hello"))
        self.assertEqual(done["context"]["context_budget"], 1000)
        # second turn resumes the same fake session
        events2 = self.turn(sess["session_id"], "again")
        self.assertEqual(events2[-1]["session_id"], sess["session_id"])

    def test_context_endpoint_reports_harness_state(self):
        sess = self.create()
        code, obj = self.request("GET", f"/v1/sessions/{sess['session_id']}/context")
        self.assertEqual(code, 200)
        self.assertTrue(obj["ok"])
        self.assertEqual(obj["context"], {})
        self.turn(sess["session_id"], "hello context")
        code, obj = self.request("GET", f"/v1/sessions/{sess['session_id']}/context")
        self.assertEqual(code, 200)
        self.assertEqual(obj["harness"], "fake")
        self.assertEqual(obj["context"]["prompt_tokens"], len("hello context"))
        self.assertEqual(obj["context"]["context_budget"], 1000)
        self.assertFalse(obj["context"]["should_compact"])
        self.assertIn("usage", obj["context"])

    def test_fake_attachment_is_written(self):
        sess = self.create()
        payload = {"name": "../evil name.txt", "mime": "text/plain",
                   "kind": "file",
                   "base64": "aGVsbG8tYXR0YWNobWVudA=="}
        events = self.turn(sess["session_id"], "with file", [payload])
        joined = " ".join(e.get("text", "") for e in events)
        self.assertIn("attachments=", joined)
        files = list((Path(self.tmp.name) / ".ox-attachments").glob("*"))
        self.assertTrue(files)
        self.assertNotIn("evil name", files[0].name)
        self.assertEqual(files[0].read_text(), "hello-attachment")

    def test_fake_error_event(self):
        sess = self.create()
        events = self.turn(sess["session_id"], "please fail")
        self.assertIn("error", [e["type"] for e in events])
        self.assertNotIn("done", [e["type"] for e in events])

    def test_fake_compact(self):
        sess = self.create()
        code, obj = self.request(
            "POST", f"/v1/sessions/{sess['session_id']}/compact", {})
        self.assertEqual(code, 200)
        self.assertTrue(obj["ok"])
        self.assertIn("fake compaction", obj["result"])
        self.assertEqual(obj["session_id"], sess["session_id"])

    def test_session_persistence(self):
        sess = self.create()
        reloaded = bridge.Registry(Path(self.tmp.name) / "state" / "sessions.json")
        found = reloaded.get(sess["session_id"])
        self.assertIsNotNone(found)
        self.assertEqual(found.harness, "fake")

    def test_unknown_session_and_harness(self):
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.request("POST", "/v1/sessions/oxs_0000000000000000/turns",
                         {"text": "x"})
        self.assertEqual(ctx.exception.code, 404)
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.request("POST", "/v1/sessions", {"harness": "nope"})
        self.assertEqual(ctx.exception.code, 400)

    def test_delete_session(self):
        sess = self.create()
        code, obj = self.request("DELETE", f"/v1/sessions/{sess['session_id']}")
        self.assertEqual(code, 200)
        self.assertTrue(obj["ok"])
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.request("GET", f"/v1/sessions/{sess['session_id']}")
        self.assertEqual(ctx.exception.code, 404)


class ParserTest(unittest.TestCase):
    def state(self, sid="oxs_0123456789abcdef", native=""):
        return {"session": sid, "native": native, "text": "", "final": "",
                "usage": {}, "is_error": False, "error_text": "",
                "result_seen": False, "saw_partial": False,
                "tools_seen": set(), "tool_names": {}}

    def test_commandcode_text_tool_and_done(self):
        st = self.state()
        events = []
        fixtures = [
            {"type": "event", "event": {"type": "run_start", "sessionId": "abc"}},
            {"type": "event", "event": {"type": "text_delta", "delta": "O"}},
            {"type": "event", "event": {"type": "text_delta", "delta": "K"}},
            {"type": "event", "event": {"type": "tool_queued",
                                        "toolCallId": "t1", "toolName": "bash",
                                        "input": {"command": "echo hi"}}},
            {"type": "event", "event": {"type": "tool_completed",
                                        "toolCallId": "t1", "toolName": "bash",
                                        "result": "hi"}},
            {"type": "event", "event": {"type": "run_end", "result": {
                "finalText": "OK", "nextState": {"sessionId": "abc"}}}},
            {"type": "result", "subtype": "success", "sessionId": "abc",
             "finalText": "OK"},
        ]
        for fixture in fixtures:
            events.extend(bridge.parse_commandcode_event(fixture, st))
        kinds = [e["type"] for e in events]
        self.assertEqual(st["native"], "abc")
        self.assertEqual(st["final"], "OK")
        self.assertIn("tool_call", kinds)
        self.assertIn("tool_result", kinds)
        self.assertEqual([e["text"] for e in events if e["type"] == "text"], ["O", "K"])

    def test_commandcode_error_result(self):
        st = self.state()
        list(bridge.parse_commandcode_event(
            {"type": "result", "subtype": "error", "sessionId": "abc",
             "error": "Rate limit exceeded."}, st))
        self.assertTrue(st["is_error"])
        self.assertIn("Rate limit", st["error_text"])

    def test_opencode_text_and_tool(self):
        st = self.state()
        events = []
        events.extend(bridge.parse_opencode_event(
            {"type": "step_start", "sessionID": "ses_1", "part": {}}, st))
        events.extend(bridge.parse_opencode_event(
            {"type": "tool_use", "sessionID": "ses_1", "part": {
                "type": "tool", "tool": "shell",
                "state": {"status": "completed", "input": {"command": "printf x"},
                          "output": "x"}}}, st))
        events.extend(bridge.parse_opencode_event(
            {"type": "text", "sessionID": "ses_1",
             "part": {"text": "x"}}, st))
        self.assertEqual(st["native"], "ses_1")
        self.assertIn("session", [e["type"] for e in events])
        call = [e for e in events if e["type"] == "tool_call"][0]
        self.assertEqual(call["name"], "shell")
        result = [e for e in events if e["type"] == "tool_result"][0]
        self.assertEqual(result["content"], "x")

    def test_claude_partial_and_tool(self):
        st = self.state(native="11111111-1111-1111-1111-111111111111")
        events = []
        events.extend(bridge.parse_claude_event(
            {"type": "system", "subtype": "init",
             "session_id": st["native"], "model": "m", "tools": ["Bash"]}, st))
        events.extend(bridge.parse_claude_event(
            {"type": "stream_event", "event": {
                "type": "content_block_delta",
                "delta": {"type": "text_delta", "text": "hi"}}}, st))
        events.extend(bridge.parse_claude_event(
            {"type": "assistant", "message": {"content": [
                {"type": "text", "text": "hi"},
                {"type": "tool_use", "id": "tu1", "name": "Bash",
                 "input": {"command": "true"}}]}}, st))
        events.extend(bridge.parse_claude_event(
            {"type": "user", "message": {"content": [
                {"type": "tool_result", "tool_use_id": "tu1",
                 "content": [{"type": "text", "text": "done"}]}]}}, st))
        events.extend(bridge.parse_claude_event(
            {"type": "result", "session_id": st["native"], "result": "hi",
             "is_error": False, "usage": {"input_tokens": 3}}, st))
        self.assertTrue(st["saw_partial"])
        self.assertEqual(st["final"], "hi")
        # partial delta only once, not duplicated by the assistant message
        self.assertEqual([e["text"] for e in events if e["type"] == "text"], ["hi"])
        result = [e for e in events if e["type"] == "tool_result"][0]
        self.assertEqual(result["name"], "Bash")
        self.assertEqual(result["content"], "done")

    def test_opencode_context_normalization(self):
        ctx = bridge.merge_opencode_session_context({}, {"data": {
            "tokens": {"input": 1197, "output": 73, "reasoning": 320,
                       "cache": {"read": 15296, "write": 5}},
            "model": {"id": "mimo-v2.6-flash-free", "providerID": "opencode"},
            "cost": 0}})
        self.assertEqual(ctx["usage"]["input"], 1197)
        self.assertEqual(ctx["usage"]["cache_read"], 15296)
        self.assertEqual(ctx["model"], "mimo-v2.6-flash-free")
        self.assertEqual(ctx["provider"], "opencode")

    def test_claude_auth_error(self):
        st = self.state()
        list(bridge.parse_claude_event(
            {"type": "assistant", "message": {"content": [
                {"type": "text", "text": "Failed to authenticate."}]}}, st))
        list(bridge.parse_claude_event(
            {"type": "result", "session_id": "x", "result": "Failed to authenticate.",
             "is_error": True}, st))
        self.assertTrue(st["is_error"])
        self.assertIn("authenticate", st["error_text"])


if __name__ == "__main__":
    unittest.main()
