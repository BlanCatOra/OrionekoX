#!/usr/bin/env python3
"""Mock OpenAI-compatible chat endpoint for deterministic memory QA.

Run on the host; point the fixture agent at http://10.0.2.2:18999/v1.
It logs each request to /tmp/ox_memory_requests.jsonl and triggers
memory_search / memory_verify so the on-device tool loop can be verified
without a random real model.
"""
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

LOG = "/tmp/ox_memory_requests.jsonl"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        messages = body.get("messages") or []
        system = "\n".join(str(m.get("content", "")) for m in messages if m.get("role") == "system")
        tools = [m for m in messages if m.get("role") == "tool"]
        user = ""
        for m in reversed(messages):
            if m.get("role") == "user":
                content = m.get("content")
                user = content if isinstance(content, str) else " ".join(
                    str(p.get("text", "")) for p in content if isinstance(p, dict))
                break
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({
                "ts": int(time.time() * 1000), "path": self.path,
                "messages": len(messages), "user": user,
                "has_compression": "# 上下文压缩" in system,
                "has_digest": "# 记忆库摘要" in system,
                "has_recall": "# 与当前消息相关的记忆" in system,
                "tool_count": len(tools),
                "tool_result": (str(tools[-1].get("content", ""))[:1200] if tools else ""),
                "system_tail": system[-1800:],
            }, ensure_ascii=False) + "\n")

        if tools:
            result = str(tools[-1].get("content", ""))[:800]
            return self.reply({"choices": [{"index": 0, "finish_reason": "stop",
                                            "message": {"role": "assistant",
                                                        "content": "MOCK_TOOL_RESULT\n" + result}}]})
        low = user.lower()
        if "verify" in low:
            query = "favorite color" if "color" in low else "birthday"
            return self.tool_call("memory_verify", json.dumps({"query": query}, ensure_ascii=False))
        if "birthday" in low:
            return self.tool_call("memory_search", '{"query":"birthday","limit":5}')
        if "coffee" in low:
            return self.tool_call("memory_search", '{"query":"black coffee","limit":5}')
        flags = [name for name, marker in (
            ("COMPRESSED", "# 上下文压缩"), ("DIGEST", "# 记忆库摘要"),
            ("RECALL", "# 与当前消息相关的记忆")) if marker in system]
        return self.reply({"choices": [{"index": 0, "finish_reason": "stop",
                                        "message": {"role": "assistant",
                                                    "content": "MOCK_OK " + ",".join(flags)}}]})

    def tool_call(self, name, args):
        return self.reply({"choices": [{"index": 0, "finish_reason": "tool_calls",
                                        "message": {"role": "assistant", "content": None,
                                                    "tool_calls": [{"id": "call_1", "type": "function",
                                                                    "function": {"name": name,
                                                                                 "arguments": args}}]}}]})

    def reply(self, obj):
        data = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 18999), Handler).serve_forever()
