#!/usr/bin/env python3
"""OrionekoX host-side harness bridge.

The Android app cannot spawn Claude Code, CommandCode, OpenCode or OpenClaw
itself. This bridge runs on the machine that owns those CLIs and exposes a
small authenticated HTTP/NDJSON protocol:

    GET    /v1/health
    POST   /v1/sessions                 {harness, cwd, model, agent, system}
    GET    /v1/sessions/<id>
    POST   /v1/sessions/<id>/turns      {text, attachments}  -> NDJSON
    POST   /v1/sessions/<id>/compact
    DELETE /v1/sessions/<id>

Each harness keeps its own native session: Claude/CommandCode use
--session-id/--resume, OpenCode uses --session, OpenClaw uses a Gateway
session id. The bridge never invents a successful turn or compact. Errors
from the CLI/provider are returned as error events with the CLI's own text.

Only the Python standard library is required. Bind to loopback by default;
use --host 0.0.0.0 only on a trusted LAN and always keep a token.
"""

from __future__ import annotations

import argparse
import base64
import hmac
import json
import os
import pty
import re
import select
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time
import traceback
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

BRIDGE_VERSION = "0.58.0"
PROTOCOL_VERSION = 1
HARNESSES = ("claude", "commandcode", "opencode", "openclaw", "fake")
MAX_TEXT = 1_000_000
MAX_ATTACHMENT = 12 * 1024 * 1024
MAX_ATTACHMENTS_TOTAL = 32 * 1024 * 1024
MAX_SYSTEM = 64_000
MAX_BODY = 64 * 1024 * 1024
SESSION_ID_RE = re.compile(r"^oxs_[0-9a-f]{16}$")
NATIVE_ID_RE = re.compile(r"^[A-Za-z0-9_.:@/-]{1,256}$")

STATE_DIR = Path(os.environ.get(
    "OX_BRIDGE_STATE_DIR",
    Path.home() / ".local" / "state" / "orionekox-harness-bridge"))
REGISTRY_FILE = STATE_DIR / "sessions.json"


def log(message: str) -> None:
    stamp = time.strftime("%Y-%m-%d %H:%M:%S")
    sys.stderr.write(f"{stamp} {message}\n")
    sys.stderr.flush()


def clip(value, limit: int) -> str:
    text = "" if value is None else str(value)
    return text if len(text) <= limit else text[:limit] + "…"


def sanitize_name(name: str, fallback: str) -> str:
    base = os.path.basename(name or fallback)
    base = re.sub(r"[^A-Za-z0-9._-]+", "_", base).strip("._")
    return (base or fallback)[:120]


class Session:
    def __init__(self, sid: str, harness: str, cwd: str, model: str = "",
                 agent: str = "", system: str = "", permission_mode: str = ""):
        self.id = sid
        self.harness = harness
        self.cwd = cwd
        self.model = model
        self.agent = agent
        self.system = system
        self.permission_mode = permission_mode
        self.native = ""
        self.native_ready = False
        self.openclaw_key = ""
        self.last_context = {}
        self.created = int(time.time())
        self.last_used = self.created
        self.turn_count = 0
        self.lock = threading.Lock()

    def to_json(self) -> dict:
        return {
            "id": self.id, "harness": self.harness, "cwd": self.cwd,
            "model": self.model, "agent": self.agent, "system": self.system,
            "permission_mode": self.permission_mode, "native": self.native,
            "native_ready": self.native_ready,
            "openclaw_key": self.openclaw_key,
            "last_context": self.last_context, "created": self.created,
            "last_used": self.last_used, "turn_count": self.turn_count,
        }

    @classmethod
    def from_json(cls, obj: dict) -> "Session":
        s = cls(obj["id"], obj["harness"], obj.get("cwd") or str(Path.home()),
                obj.get("model", ""), obj.get("agent", ""),
                obj.get("system", ""), obj.get("permission_mode", ""))
        s.native = obj.get("native", "")
        s.native_ready = bool(obj.get("native_ready", False))
        s.openclaw_key = obj.get("openclaw_key", "")
        s.last_context = obj.get("last_context") or {}
        s.created = int(obj.get("created", time.time()))
        s.last_used = int(obj.get("last_used", s.created))
        s.turn_count = int(obj.get("turn_count", 0))
        return s

    def public(self) -> dict:
        return {
            "session_id": self.id, "harness": self.harness,
            "native_session": self.native, "cwd": self.cwd,
            "model": self.model, "agent": self.agent,
            "turn_count": self.turn_count, "created": self.created,
            "last_used": self.last_used,
        }


class Registry:
    def __init__(self, path: Path):
        self.path = path
        self.lock = threading.RLock()
        self.sessions: dict[str, Session] = {}
        self.load()

    def load(self) -> None:
        try:
            raw = json.loads(self.path.read_text())
            for obj in raw.get("sessions", []):
                try:
                    s = Session.from_json(obj)
                    if SESSION_ID_RE.match(s.id) and s.harness in HARNESSES:
                        self.sessions[s.id] = s
                except Exception as exc:
                    log(f"registry skip malformed session: {exc}")
        except FileNotFoundError:
            pass
        except Exception as exc:
            log(f"registry load failed: {exc}")

    def save(self) -> None:
        with self.lock:
            self._save_locked()

    def _save_locked(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(".tmp")
        data = json.dumps({"version": 1, "sessions":
                           [s.to_json() for s in self.sessions.values()]},
                          ensure_ascii=False, indent=2)
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        try:
            with os.fdopen(fd, "w") as fh:
                fh.write(data)
                fh.flush()
                os.fsync(fh.fileno())
            os.replace(tmp, self.path)
        finally:
            try:
                os.chmod(self.path, 0o600)
            except OSError:
                pass

    def create(self, harness: str, cwd: str, model: str, agent: str,
               system: str, permission_mode: str) -> Session:
        sid = "oxs_" + uuid.uuid4().hex[:16]
        s = Session(sid, harness, cwd, model, agent, system, permission_mode)
        if harness == "claude":
            s.native = str(uuid.uuid4())
        elif harness == "openclaw":
            s.native = "ox-" + uuid.uuid4().hex[:12]
        with self.lock:
            self.sessions[sid] = s
            self.save()
        return s

    def get(self, sid: str) -> Session | None:
        with self.lock:
            return self.sessions.get(sid)

    def delete(self, sid: str) -> bool:
        with self.lock:
            existed = self.sessions.pop(sid, None) is not None
            if existed:
                self.save()
            return existed

    def count(self) -> int:
        with self.lock:
            return len(self.sessions)


# --------------------------------------------------------------------------
# deterministic fake harness (tests only; exposed when --enable-fake)


def fake_turn(sess: Session, prompt: str, attachments: list[dict], timeout: int):
    yield {"type": "session", "session_id": sess.id, "native_session": sess.id}
    yield {"type": "status", "state": "thinking", "harness": "fake"}
    yield {"type": "status", "state": "context", "harness": "fake",
           "prompt_tokens": len(prompt), "context_budget": 1000,
           "should_compact": len(prompt) > 800}
    if "fail" in prompt:
        yield {"type": "error", "message": "fake harness failure requested by prompt"}
        return
    if "slow" in prompt:
        time.sleep(1.0)
    parts = [f"fake:{prompt}"]
    if attachments:
        parts.append("attachments=" + ",".join(a["path"] for a in attachments))
    yield {"type": "text", "text": parts[0]}
    yield {"type": "tool_call", "id": "fake-1", "name": "echo",
           "arguments": json.dumps({"text": prompt[:200]}, ensure_ascii=False)}
    yield {"type": "tool_result", "id": "fake-1", "name": "echo",
           "content": "echo:" + prompt[:200], "is_error": False}
    if len(parts) > 1:
        yield {"type": "text", "text": " " + parts[1]}
    yield {"type": "done", "text": "".join(parts), "native_session": sess.id,
           "usage": {"input_tokens": len(prompt), "output_tokens": len(parts[0])},
           "exit_code": 0, "transport": "in-process"}


def fake_compact(sess: Session) -> dict:
    return {"ok": True, "harness": "fake", "transport": "in-process",
            "result": "fake compaction completed",
            "native_session": sess.id, "duration_ms": 0}


# --------------------------------------------------------------------------
# Claude Code: print mode + native session id, stream-json


def claude_turn(sess: Session, prompt: str, attachments: list[dict], timeout: int):
    claude = shutil.which("claude")
    if not claude:
        yield {"type": "error", "message": "claude CLI not found on bridge host"}
        return
    first = not sess.native or not sess.native_ready
    cmd = [claude, "-p", prompt, "--output-format", "stream-json", "--verbose",
           "--include-partial-messages"]
    if first:
        cmd += ["--session-id", sess.native]
    else:
        cmd += ["--resume", sess.native]
    if sess.model:
        cmd += ["--model", sess.model]
    if sess.permission_mode:
        cmd += ["--permission-mode", sess.permission_mode]
    elif os.environ.get("OX_BRIDGE_CLAUDE_PERMISSION"):
        cmd += ["--permission-mode", os.environ["OX_BRIDGE_CLAUDE_PERMISSION"]]
    yield from run_json_lines(sess, cmd, timeout, "claude", parse_claude_event)


def parse_claude_event(obj: dict, state: dict):
    t = obj.get("type")
    if t == "system":
        if obj.get("subtype") == "init":
            sid = obj.get("session_id")
            if sid:
                state["native"] = sid
                yield {"type": "session", "session_id": state["session"], "native_session": sid}
            yield {"type": "status", "state": "ready", "harness": "claude",
                   "model": obj.get("model", ""), "tools": len(obj.get("tools", []))}
        elif obj.get("subtype") == "status":
            status = obj.get("status")
            if status:
                yield {"type": "status", "state": status, "harness": "claude"}
    elif t == "stream_event":
        event = obj.get("event") or {}
        if event.get("type") == "content_block_delta":
            delta = event.get("delta") or {}
            text = delta.get("text") if delta.get("type") == "text_delta" else None
            if text:
                state["saw_partial"] = True
                state["text"] += text
                yield {"type": "text", "text": text}
    elif t == "assistant":
        msg = obj.get("message") or {}
        for block in msg.get("content", []):
            if not isinstance(block, dict):
                continue
            if block.get("type") == "text":
                text = block.get("text") or ""
                if text and not state["saw_partial"]:
                    state["text"] += text
                    yield {"type": "text", "text": text}
                elif text:
                    # partial stream already delivered this text
                    pass
            elif block.get("type") == "tool_use":
                tid = block.get("id") or ""
                if tid and tid not in state["tools_seen"]:
                    state["tools_seen"].add(tid)
                    state["tool_names"][tid] = block.get("name", "tool")
                    yield {"type": "tool_call", "id": tid,
                           "name": block.get("name", "tool"),
                           "arguments": json.dumps(block.get("input") or {},
                                                   ensure_ascii=False)}
    elif t == "user":
        msg = obj.get("message") or {}
        for block in msg.get("content", []) if isinstance(msg.get("content"), list) else []:
            if isinstance(block, dict) and block.get("type") == "tool_result":
                content = block.get("content")
                if isinstance(content, list):
                    content = "\n".join(
                        c.get("text", "") for c in content if isinstance(c, dict))
                yield {"type": "tool_result", "id": block.get("tool_use_id", ""),
                       "name": state["tool_names"].get(block.get("tool_use_id", ""), "tool"),
                       "content": clip(content, 12_000),
                       "is_error": bool(block.get("is_error"))}
    elif t == "result":
        sid = obj.get("session_id")
        if sid:
            state["native"] = sid
        text = obj.get("result")
        if text is None:
            text = state["text"]
        state["final"] = text or state["text"]
        state["usage"] = obj.get("usage") or {}
        state["is_error"] = bool(obj.get("is_error"))
        state["error_text"] = text if state["is_error"] else ""
        state["result_seen"] = True
        yield {"type": "status", "state": "finished", "harness": "claude",
               "terminal_reason": obj.get("terminal_reason"),
               "duration_ms": obj.get("duration_ms")}


def claude_compact(sess: Session, timeout: int) -> dict:
    claude = shutil.which("claude")
    if not claude:
        return {"ok": False, "harness": "claude", "error": "claude CLI not found"}
    if not sess.native:
        return {"ok": False, "harness": "claude",
                "error": "no native Claude session yet"}
    cmd = [claude, "-p", "/compact", "--resume", sess.native,
           "--output-format", "stream-json", "--verbose"]
    if sess.model:
        cmd += ["--model", sess.model]
    started = time.time()
    try:
        p = subprocess.run(cmd, cwd=sess.cwd, capture_output=True, text=True,
                           timeout=timeout)
    except subprocess.TimeoutExpired:
        return {"ok": False, "harness": "claude", "error": "compact timed out"}
    final = ""
    is_error = False
    for line in p.stdout.splitlines():
        try:
            obj = json.loads(line)
        except Exception:
            continue
        if obj.get("type") == "result":
            final = obj.get("result") or ""
            is_error = bool(obj.get("is_error"))
    if p.returncode != 0 and not final:
        return {"ok": False, "harness": "claude", "transport": "print-session",
                "error": clip(p.stderr or f"exit {p.returncode}", 2000)}
    return {"ok": not is_error, "harness": "claude", "transport": "print-session",
            "result": clip(final, 4000),
            "duration_ms": int((time.time() - started) * 1000),
            "native_session": sess.native}


# --------------------------------------------------------------------------
# CommandCode: -p NDJSON + --resume; native compact through interactive PTY


def commandcode_turn(sess: Session, prompt: str, attachments: list[dict], timeout: int):
    binary = shutil.which("commandcode")
    if not binary:
        yield {"type": "error", "message": "commandcode CLI not found on bridge host"}
        return
    cmd = [binary, "-p", prompt, "--output-format", "json",
           "--skip-onboarding", "--no-auto-update"]
    if sess.native and sess.native_ready:
        cmd += ["--resume", sess.native]
    if sess.model:
        cmd += ["-m", sess.model]
    yield from run_json_lines(sess, cmd, timeout, "commandcode",
                              parse_commandcode_event)


def parse_commandcode_event(obj: dict, state: dict):
    if obj.get("type") == "result":
        sid = obj.get("sessionId")
        if sid:
            state["native"] = sid
        text = obj.get("finalText") or state["text"]
        state["final"] = text
        state["usage"] = obj.get("usage") or {}
        state["result_seen"] = True
        if obj.get("subtype") == "error":
            state["is_error"] = True
            state["error_text"] = obj.get("error") or "CommandCode run failed"
        yield {"type": "status", "state": "finished", "harness": "commandcode",
               "stop_reason": obj.get("stopReason"),
               "duration_ms": obj.get("durationMs")}
        return
    event = obj.get("event") or {}
    et = event.get("type")
    if et == "notice":
        yield {"type": "status", "state": "notice", "harness": "commandcode",
               "message": clip(event.get("message"), 500),
               "level": event.get("level")}
    elif et == "run_start":
        sid = event.get("sessionId")
        if sid:
            state["native"] = sid
            yield {"type": "session", "session_id": state["session"],
                   "native_session": sid}
    elif et == "model_request_start":
        yield {"type": "status", "state": "thinking", "harness": "commandcode",
               "model": event.get("model", "")}
    elif et == "text_delta":
        piece = event.get("delta")
        if piece:
            state["text"] += piece
            yield {"type": "text", "text": piece}
    elif et == "tool_queued":
        tid = event.get("toolCallId") or ""
        name = event.get("toolName") or "tool"
        state["tool_names"][tid] = name
        yield {"type": "tool_call", "id": tid, "name": name,
               "arguments": json.dumps(event.get("input") or {},
                                       ensure_ascii=False)}
    elif et == "tool_running":
        yield {"type": "status", "state": "tool_running", "harness": "commandcode",
               "tool": event.get("toolName"), "description": clip(event.get("description"), 300)}
    elif et == "tool_completed":
        tid = event.get("toolCallId") or ""
        result = event.get("result")
        if not isinstance(result, str):
            result = json.dumps(result, ensure_ascii=False)
        yield {"type": "tool_result", "id": tid,
               "name": event.get("toolName") or state["tool_names"].get(tid, "tool"),
               "content": clip(result, 12_000),
               "is_error": bool(event.get("isError"))}
    elif et == "api_retry":
        yield {"type": "status", "state": "retrying", "harness": "commandcode",
               "attempt": event.get("attempt"),
               "message": clip(event.get("error"), 500)}
    elif et == "run_error":
        err = event.get("error") or {}
        state["is_error"] = True
        state["error_text"] = err.get("message") or "CommandCode run failed"
    elif et == "run_end":
        result = event.get("result") or {}
        if result.get("finalText"):
            state["final"] = result["finalText"]
        state["usage"] = result.get("usage") or state["usage"]
        sid = (result.get("nextState") or {}).get("sessionId")
        if sid:
            state["native"] = sid


def commandcode_compact(sess: Session, timeout: int) -> dict:
    binary = shutil.which("commandcode")
    if not binary:
        return {"ok": False, "harness": "commandcode",
                "error": "commandcode CLI not found"}
    if not sess.native:
        return {"ok": False, "harness": "commandcode",
                "error": "no native CommandCode session yet"}
    cmd = [binary, "--resume", sess.native, "--skip-onboarding", "--no-auto-update"]
    if sess.model:
        cmd += ["-m", sess.model]
    started = time.time()
    master, slave = pty.openpty()
    proc = None
    output = bytearray()
    try:
        proc = subprocess.Popen(cmd, cwd=sess.cwd, stdin=slave, stdout=slave,
                                stderr=slave, close_fds=True, start_new_session=True,
                                env={**os.environ, "TERM": "xterm-256color"})
        os.close(slave)
        slave = -1
        deadline = time.time() + timeout
        sent = False
        while time.time() < deadline:
            ready, _, _ = select.select([master], [], [], 0.5)
            if ready:
                try:
                    chunk = os.read(master, 65536)
                except OSError:
                    break
                if not chunk:
                    break
                output += chunk
            text = strip_ansi(output.decode("utf-8", "replace"))
            idle_marker = ("输入你的问题" in text or "Type your question" in text
                           or "Ask anything" in text or "What can I help" in text
                           or (time.time() - started > 20 and "❯" in text))
            if not sent and idle_marker:
                time.sleep(1.0)
                os.write(master, b"/compact")
                time.sleep(1.0)
                os.write(master, b"\r")
                sent = True
            if sent and ("对话已经过压缩" in text
                         or "Conversation compacted" in text
                         or "compacted the conversation" in text.lower()
                         or "context compacted" in text.lower()):
                return {"ok": True, "harness": "commandcode", "transport": "pty",
                        "result": clip(text[-600:], 2000),
                        "duration_ms": int((time.time() - started) * 1000),
                        "native_session": sess.native}
            if sent and ("压缩失败" in text or "Failed to compact" in text
                         or "compaction failed" in text.lower()):
                return {"ok": False, "harness": "commandcode", "transport": "pty",
                        "error": clip(text[-800:], 2000)}
        if not sent:
            return {"ok": False, "harness": "commandcode", "transport": "pty",
                    "error": "interactive prompt not reached; /compact not sent",
                    "output": clip(strip_ansi(output.decode("utf-8", "replace"))[-600:], 1500)}
        return {"ok": False, "harness": "commandcode", "transport": "pty",
                "error": "compact did not confirm before timeout"}
    except Exception as exc:
        return {"ok": False, "harness": "commandcode", "transport": "pty",
                "error": f"{type(exc).__name__}: {exc}"}
    finally:
        if slave >= 0:
            os.close(slave)
        if proc is not None and proc.poll() is None:
            try:
                os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
            except Exception:
                proc.terminate()
            try:
                proc.wait(timeout=3)
            except Exception:
                proc.kill()
        try:
            os.close(master)
        except OSError:
            pass


# --------------------------------------------------------------------------
# OpenCode: run --format json + --session; compact through its HTTP session API


def opencode_turn(sess: Session, prompt: str, attachments: list[dict], timeout: int):
    binary = shutil.which("opencode")
    if not binary:
        yield {"type": "error", "message": "opencode CLI not found on bridge host"}
        return
    cmd = [binary, "run", "--format", "json", "--auto"]
    if sess.model:
        cmd += ["--model", sess.model]
    if sess.native and sess.native_ready:
        cmd += ["--session", sess.native]
    for att in attachments:
        cmd += ["--file", att["path"]]
    cmd += [prompt]
    yield from run_json_lines(sess, cmd, timeout, "opencode",
                              parse_opencode_event)


def parse_opencode_event(obj: dict, state: dict):
    sid = obj.get("sessionID")
    if sid and sid != state["native"]:
        state["native"] = sid
        state["native_ready"] = True
        yield {"type": "session", "session_id": state["session"],
               "native_session": sid}
    t = obj.get("type")
    part = obj.get("part") or {}
    if t == "step_start":
        yield {"type": "status", "state": "thinking", "harness": "opencode"}
    elif t == "text":
        text = part.get("text") or ""
        if text:
            state["text"] += text
            yield {"type": "text", "text": text}
    elif t == "tool_use":
        name = part.get("tool") or "tool"
        st = part.get("state") or {}
        args = st.get("input") or {}
        tid = st.get("id") or part.get("id") or ""
        yield {"type": "tool_call", "id": tid, "name": name,
               "arguments": json.dumps(args, ensure_ascii=False)}
        if st.get("status") in ("completed", "error"):
            out = st.get("output")
            if out is None:
                out = st.get("metadata") or {}
            if not isinstance(out, str):
                out = json.dumps(out, ensure_ascii=False)
            yield {"type": "tool_result", "id": tid, "name": name,
                   "content": clip(out, 12_000),
                   "is_error": st.get("status") == "error"}
    elif t == "step_finish":
        tokens = part.get("tokens") or {}
        state["usage"] = tokens or state["usage"]
        yield {"type": "status", "state": "step_finished", "harness": "opencode",
               "usage": tokens}
    elif t == "error":
        err = obj.get("error") or {}
        state["is_error"] = True
        state["error_text"] = err.get("message") or json.dumps(err)[:500]


class OpencodeServer:
    """One private server per working directory, used only for native compact."""

    def __init__(self):
        self.lock = threading.Lock()
        self.servers: dict[str, tuple[subprocess.Popen, int, str]] = {}

    def get(self, cwd: str) -> tuple[int, str] | None:
        with self.lock:
            entry = self.servers.get(cwd)
            if entry and entry[0].poll() is None:
                return entry[1], entry[2]
            if entry:
                self._stop(entry[0])
                self.servers.pop(cwd, None)
            binary = shutil.which("opencode")
            if not binary:
                return None
            sock = socket.socket()
            try:
                sock.bind(("127.0.0.1", 0))
                port = sock.getsockname()[1]
            finally:
                sock.close()
            try:
                proc = subprocess.Popen(
                    [binary, "serve", "--hostname", "127.0.0.1", "--port", str(port)],
                    cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    text=True, bufsize=1, start_new_session=True)
            except Exception as exc:
                log(f"opencode serve failed: {exc}")
                return None
            password = ""
            deadline = time.time() + 25
            lines = []
            while time.time() < deadline:
                if proc.stdout is None:
                    break
                line = proc.stdout.readline()
                if not line:
                    if proc.poll() is not None:
                        break
                    continue
                lines.append(line.rstrip())
                m = re.search(r"server password (\S+)", line)
                if m:
                    password = m.group(1)
                    break
            if not password:
                log("opencode serve did not print a password: " + clip(" | ".join(lines[-4:]), 500))
                self._stop(proc)
                return None
            # Drain the rest of the server log so its pipe never fills.
            threading.Thread(target=self._drain, args=(proc,), daemon=True).start()
            self.servers[cwd] = (proc, port, password)
            return port, password

    @staticmethod
    def _drain(proc: subprocess.Popen) -> None:
        try:
            if proc.stdout:
                for _ in proc.stdout:
                    pass
        except Exception:
            pass

    @staticmethod
    def _stop(proc: subprocess.Popen) -> None:
        if proc.poll() is not None:
            return
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        except Exception:
            proc.terminate()
        try:
            proc.wait(timeout=4)
        except Exception:
            proc.kill()

    def stop_all(self) -> None:
        with self.lock:
            for proc, _, _ in self.servers.values():
                self._stop(proc)
            self.servers.clear()


OPENCODE_SERVER = OpencodeServer()


def merge_opencode_session_context(ctx: dict, payload: dict) -> dict:
    """Normalizes GET /api/session/<id> into the bridge context shape."""
    data = payload.get("data") if isinstance(payload, dict) else None
    if not isinstance(data, dict):
        return ctx
    tokens = data.get("tokens") or {}
    cache = tokens.get("cache") or {}
    usage = {
        "input": tokens.get("input", 0),
        "output": tokens.get("output", 0),
        "reasoning": tokens.get("reasoning", 0),
        "cache_read": cache.get("read", 0),
        "cache_write": cache.get("write", 0),
    }
    ctx = dict(ctx)
    ctx["usage"] = usage
    model = data.get("model") or {}
    if model.get("id"):
        ctx["model"] = model.get("id")
    if model.get("providerID"):
        ctx["provider"] = model.get("providerID")
    if data.get("cost") is not None:
        ctx["cost"] = data.get("cost")
    return ctx


def opencode_compact(sess: Session, timeout: int) -> dict:
    if not sess.native:
        return {"ok": False, "harness": "opencode",
                "error": "no native OpenCode session yet"}
    started = time.time()
    target = OPENCODE_SERVER.get(sess.cwd)
    if not target:
        return {"ok": False, "harness": "opencode", "transport": "http-session-api",
                "error": "could not start the private OpenCode server for compact"}
    port, password = target
    url = f"http://127.0.0.1:{port}/api/session/{sess.native}/compact"
    auth = "Basic " + base64.b64encode(f"opencode:{password}".encode()).decode()
    data = json.dumps({"reason": "orionekox requested compact"}).encode()
    req = urllib.request.Request(url, data=data, method="POST",
                                 headers={"Authorization": auth,
                                          "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=max(10, timeout)) as resp:
            body = resp.read(8000).decode("utf-8", "replace")
        parsed = json.loads(body) if body else {}
        event = parsed.get("data") if isinstance(parsed, dict) else None
        ok = isinstance(event, dict) and event.get("type") == "compaction"
        return {"ok": ok, "harness": "opencode", "transport": "http-session-api",
                "result": clip(body, 4000),
                "duration_ms": int((time.time() - started) * 1000),
                "native_session": sess.native}
    except urllib.error.HTTPError as exc:
        body = exc.read(2000).decode("utf-8", "replace")
        return {"ok": False, "harness": "opencode", "transport": "http-session-api",
                "error": f"HTTP {exc.code}: {clip(body, 1500)}"}
    except Exception as exc:
        return {"ok": False, "harness": "opencode", "transport": "http-session-api",
                "error": f"{type(exc).__name__}: {exc}"}


# --------------------------------------------------------------------------
# OpenClaw: Gateway-backed session; compact through sessions.compact RPC


def openclaw_turn(sess: Session, prompt: str, attachments: list[dict], timeout: int):
    binary = shutil.which("openclaw")
    if not binary:
        yield {"type": "error", "message": "openclaw CLI not found on bridge host"}
        return
    if not sess.native:
        sess.native = "ox-" + uuid.uuid4().hex[:12]
    cmd = [binary, "agent", "--session-id", sess.native, "--message", prompt,
           "--json", "--timeout", str(timeout)]
    if sess.agent:
        cmd += ["--agent", sess.agent]
    if sess.model:
        cmd += ["--model", sess.model]
    started = time.time()
    yield {"type": "status", "state": "starting", "harness": "openclaw"}
    try:
        p = subprocess.run(cmd, cwd=sess.cwd, capture_output=True, text=True,
                           timeout=timeout + 15)
    except subprocess.TimeoutExpired:
        yield {"type": "error", "message": "openclaw turn timed out"}
        return
    if not p.stdout.strip():
        yield {"type": "error",
               "message": clip(p.stderr or f"openclaw exit {p.returncode}", 2000)}
        return
    try:
        obj = json.loads(p.stdout)
    except Exception:
        # The CLI can emit a log line before the JSON object.
        brace = p.stdout.find("{")
        try:
            obj = json.loads(p.stdout[brace:])
        except Exception:
            yield {"type": "error", "message": clip(p.stdout + p.stderr, 2000)}
            return
    if not obj.get("ok", True) or obj.get("status") not in (None, "ok"):
        err = obj.get("error") or {}
        yield {"type": "error", "message": clip(
            err.get("message") or obj.get("summary") or "openclaw run failed", 2000)}
        return
    result = obj.get("result") or {}
    meta = result.get("meta") or {}
    agent_meta = meta.get("agentMeta") or {}
    sid = agent_meta.get("sessionId") or sess.native
    key = agent_meta.get("sessionFile")
    if key:
        sess.openclaw_key = key
    if sid:
        sess.native = sid
    yield {"type": "session", "session_id": sess.id, "native_session": sid,
           "gateway_session_key": key or ""}
    texts = []
    for payload in result.get("payloads") or []:
        text = payload.get("text")
        if text:
            texts.append(text)
            yield {"type": "text", "text": text}
        media = payload.get("mediaUrl")
        if media:
            yield {"type": "attachment", "url": media, "kind": "remote"}
    usage = agent_meta.get("usage") or {}
    context = agent_meta.get("contextBudgetStatus") or {}
    if context:
        yield {"type": "status", "state": "context", "harness": "openclaw",
               "should_compact": bool(context.get("shouldCompact")),
               "prompt_tokens": context.get("promptTokens"),
               "context_budget": context.get("contextTokenBudget")}
    text = "\n".join(texts)
    yield {"type": "done", "text": text, "native_session": sid,
           "usage": usage, "exit_code": p.returncode,
           "transport": "gateway-cli",
           "duration_ms": int((time.time() - started) * 1000)}


def openclaw_compact(sess: Session, timeout: int) -> dict:
    binary = shutil.which("openclaw")
    if not binary:
        return {"ok": False, "harness": "openclaw",
                "error": "openclaw CLI not found"}
    key = sess.openclaw_key
    if not key:
        return {"ok": False, "harness": "openclaw",
                "error": "no Gateway session key yet; run one turn first"}
    cmd = [binary, "sessions", "compact", key, "--json"]
    started = time.time()
    try:
        p = subprocess.run(cmd, capture_output=True, text=True,
                           timeout=max(20, timeout))
    except subprocess.TimeoutExpired:
        return {"ok": False, "harness": "openclaw",
                "error": "compact timed out"}
    try:
        obj = json.loads(p.stdout)
    except Exception:
        return {"ok": False, "harness": "openclaw", "transport": "gateway-rpc",
                "error": clip(p.stderr or p.stdout or f"exit {p.returncode}", 2000)}
    if not obj.get("ok") or not obj.get("compacted"):
        return {"ok": False, "harness": "openclaw", "transport": "gateway-rpc",
                "error": clip(obj.get("error") or "Gateway did not confirm compaction", 2000)}
    summary = (obj.get("result") or {}).get("summary") or ""
    return {"ok": True, "harness": "openclaw", "transport": "gateway-rpc",
            "result": clip(summary, 4000),
            "native_session": key,
            "duration_ms": int((time.time() - started) * 1000)}


# --------------------------------------------------------------------------
# shared process/event helpers


def strip_ansi(text: str) -> str:
    text = re.sub(r"\x1b\][^\x07]*\x07", "", text)
    return re.sub(r"\x1b\[[0-9;?]*[a-zA-Z]", "", text)


def run_json_lines(sess: Session, cmd: list[str], timeout: int, harness: str,
                   parser):
    state = {"session": sess.id, "native": sess.native, "text": "",
             "final": "", "usage": {}, "is_error": False, "error_text": "",
             "result_seen": False, "saw_partial": False,
             "tools_seen": set(), "tool_names": {}}
    yield {"type": "status", "state": "starting", "harness": harness}
    try:
        p = subprocess.Popen(cmd, cwd=sess.cwd, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, text=True, bufsize=1)
    except Exception as exc:
        yield {"type": "error", "message": f"{type(exc).__name__}: {exc}"}
        return
    stderr_tail: list[str] = []

    def read_stderr():
        if p.stderr is None:
            return
        for line in p.stderr:
            stderr_tail.append(line.rstrip())
            if len(stderr_tail) > 40:
                del stderr_tail[:20]

    err_thread = threading.Thread(target=read_stderr, daemon=True)
    err_thread.start()
    deadline = time.time() + timeout
    def watchdog():
        remaining = deadline - time.time()
        if remaining > 0:
            time.sleep(remaining)
        if p.poll() is None:
            log(f"turn timeout harness={harness} session={sess.id}; killing pid={p.pid}")
            p.kill()
    threading.Thread(target=watchdog, daemon=True).start()
    try:
        for line in p.stdout or []:
            if time.time() > deadline:
                p.kill()
                break
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except Exception:
                continue
            yield from parser(obj, state)
        try:
            p.wait(timeout=max(1, min(30, deadline - time.time() + 5)))
        except subprocess.TimeoutExpired:
            p.kill()
            p.wait(timeout=5)
    finally:
        err_thread.join(timeout=2)
        for stream in (p.stdout, p.stderr):
            try:
                if stream:
                    stream.close()
            except Exception:
                pass
    if state["native"] and state["native"] != sess.native:
        sess.native = state["native"]
    if state["is_error"] and state["error_text"]:
        yield {"type": "error", "message": state["error_text"]}
        return
    if not state["result_seen"] and not state["final"]:
        state["final"] = state["text"]
    if not state["final"] and p.returncode not in (0, None):
        tail = strip_ansi("\n".join(stderr_tail))[-1500:]
        yield {"type": "error",
               "message": clip(tail or f"{harness} exit {p.returncode}", 2000)}
        return
    yield {"type": "done", "text": state["final"],
           "native_session": sess.native, "usage": state["usage"],
           "exit_code": p.returncode, "transport": "cli-session"}


TURNERS = {
    "fake": fake_turn,
    "claude": claude_turn,
    "commandcode": commandcode_turn,
    "opencode": opencode_turn,
    "openclaw": openclaw_turn,
}
COMPACTERS = {
    "fake": lambda s, t: fake_compact(s),
    "claude": claude_compact,
    "commandcode": commandcode_compact,
    "opencode": opencode_compact,
    "openclaw": openclaw_compact,
}

CAPABILITIES = {
    "claude": {"label": "Claude Code", "transport": "print-session",
               "resume": True, "compact": True, "streaming": True,
               "tools": True, "multimodal": "path-handoff",
               "compact_transport": "print /compact"},
    "commandcode": {"label": "CommandCode", "transport": "ndjson-resume",
                    "resume": True, "compact": True, "streaming": True,
                    "tools": True, "multimodal": "path-handoff",
                    "compact_transport": "interactive PTY /compact"},
    "opencode": {"label": "OpenCode", "transport": "run-session",
                 "resume": True, "compact": True, "streaming": True,
                 "tools": True, "multimodal": "native --file",
                 "compact_transport": "HTTP session compact"},
    "openclaw": {"label": "OpenClaw", "transport": "gateway-cli",
                 "resume": True, "compact": True, "streaming": False,
                 "tools": "final text only", "multimodal": "payload mediaUrl",
                 "compact_transport": "Gateway sessions.compact"},
    "fake": {"label": "Fake (tests)", "transport": "in-process",
             "resume": True, "compact": True, "streaming": True,
             "tools": True, "multimodal": "test-only",
             "compact_transport": "in-process"},
}


def harness_status() -> dict:
    out = {}
    for harness in HARNESSES:
        cap = dict(CAPABILITIES[harness])
        binary = {"fake": None}.get(harness, harness)
        found = binary is None or shutil.which(binary) is not None
        version = ""
        if found and binary:
            try:
                version = subprocess.run([binary, "--version"], capture_output=True,
                                         text=True, timeout=6).stdout.strip().splitlines()[0]
            except Exception:
                version = ""
        cap.update({"found": found, "version": version})
        out[harness] = cap
    return out


# --------------------------------------------------------------------------
# HTTP layer


class Bridge:
    def __init__(self, args):
        self.args = args
        self.registry = Registry(Path(args.state_dir) / "sessions.json")
        self.started = time.time()
        self.token = load_token(args)
        self.allowed_roots = [Path(p).resolve() for p in args.allow_root]

    def check_cwd(self, value: str) -> str:
        path = Path(value or self.args.workdir).expanduser()
        try:
            path = path.resolve()
        except Exception:
            raise ValueError("invalid working directory")
        if not path.is_dir():
            raise ValueError(f"working directory does not exist: {path}")
        for root in self.allowed_roots:
            try:
                path.relative_to(root)
                return str(path)
            except ValueError:
                continue
        raise ValueError(f"working directory is outside allowed roots: {path}")

    def health(self) -> dict:
        return {
            "ok": True, "bridge": "orionekox-harness-bridge",
            "version": BRIDGE_VERSION, "protocol": PROTOCOL_VERSION,
            "started": self.started, "uptime_seconds": int(time.time() - self.started),
            "sessions": self.registry.count(), "harnesses": harness_status(),
            "bind": f"{self.args.host}:{self.args.port}",
        }

    def create_session(self, body: dict) -> Session:
        harness = str(body.get("harness") or "").strip()
        if harness not in HARNESSES:
            raise ValueError("harness must be one of: " + ", ".join(HARNESSES))
        if harness == "fake" and not self.args.enable_fake:
            raise ValueError("fake harness is disabled; start the bridge with --enable-fake")
        cwd = self.check_cwd(str(body.get("cwd") or ""))
        model = clip(body.get("model"), 200)
        agent = clip(body.get("agent"), 120)
        system = clip(body.get("system"), MAX_SYSTEM)
        permission = clip(body.get("permission_mode"), 40)
        return self.registry.create(harness, cwd, model, agent, system, permission)

    def attachments(self, sess: Session, raw) -> list[dict]:
        if not raw:
            return []
        if not isinstance(raw, list):
            raise ValueError("attachments must be an array")
        out = []
        total = 0
        directory = Path(sess.cwd) / ".ox-attachments"
        for index, item in enumerate(raw[:8]):
            if not isinstance(item, dict):
                raise ValueError("attachment must be an object")
            try:
                data = base64.b64decode(item.get("base64") or "", validate=True)
            except Exception:
                raise ValueError(f"attachment {index} is not valid base64")
            if len(data) > MAX_ATTACHMENT:
                raise ValueError(f"attachment {index} exceeds {MAX_ATTACHMENT} bytes")
            total += len(data)
            if total > MAX_ATTACHMENTS_TOTAL:
                raise ValueError("attachments exceed the total size limit")
            name = sanitize_name(item.get("name") or "", f"attachment-{index}")
            directory.mkdir(parents=True, exist_ok=True)
            path = directory / f"{uuid.uuid4().hex[:12]}-{name}"
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as fh:
                fh.write(data)
            out.append({"path": str(path), "name": name,
                        "mime": clip(item.get("mime"), 120),
                        "kind": clip(item.get("kind"), 20)})
        return out

    def run_turn(self, sess: Session, body: dict):
        text = clip(body.get("text"), MAX_TEXT)
        if not text.strip():
            raise ValueError("text is required")
        attachments = self.attachments(sess, body.get("attachments"))
        timeout = max(15, min(1800, int(body.get("timeout") or self.args.turn_timeout)))
        prompt = text
        if sess.turn_count == 0 and sess.system:
            prompt = sess.system.strip() + "\n\n---\n\n" + text
        if attachments:
            lines = []
            for att in attachments:
                kind = att["kind"] or "file"
                lines.append(f"[{kind}] {att['name']}: {att['path']}")
            prompt += "\n\n附件（本机路径，可用文件工具读取）:\n" + "\n".join(lines)
        with sess.lock:
            sess.turn_count += 1
            sess.last_used = int(time.time())
            self.registry.save()
            turner = TURNERS[sess.harness]
            for event in turner(sess, prompt, attachments, timeout):
                event.setdefault("session_id", sess.id)
                if event.get("type") == "session" and event.get("native_session"):
                    sess.native = event["native_session"]
                    sess.native_ready = True
                if event.get("type") == "status" and (
                        event.get("state") == "context" or event.get("usage")):
                    ctx = dict(sess.last_context)
                    for key in ("should_compact", "prompt_tokens",
                                "context_budget", "usage"):
                        if event.get(key) is not None:
                            ctx[key] = event[key]
                    ctx["harness"] = sess.harness
                    ctx["updated_at"] = int(time.time())
                    sess.last_context = ctx
                elif event.get("type") == "done":
                    ctx = dict(sess.last_context)
                    if event.get("usage"):
                        ctx["usage"] = event["usage"]
                    ctx["harness"] = sess.harness
                    ctx["updated_at"] = int(time.time())
                    sess.last_context = ctx
                    event["context"] = ctx
                yield event
            sess.last_used = int(time.time())
            sess.native_ready = bool(sess.native)
            self.registry.save()

    def context(self, sess: Session) -> dict:
        if sess.harness == "opencode" and sess.native:
            target = OPENCODE_SERVER.get(sess.cwd)
            if target:
                port, password = target
                auth = "Basic " + base64.b64encode(
                    f"opencode:{password}".encode()).decode()
                req = urllib.request.Request(
                    f"http://127.0.0.1:{port}/api/session/{sess.native}",
                    headers={"Authorization": auth})
                try:
                    with urllib.request.urlopen(req, timeout=10) as resp:
                        payload = json.loads(resp.read(200_000).decode("utf-8", "replace"))
                    sess.last_context = merge_opencode_session_context(
                        sess.last_context or {}, payload)
                    sess.last_context["harness"] = "opencode"
                    sess.last_context["updated_at"] = int(time.time())
                    self.registry.save()
                except Exception as exc:
                    log(f"opencode context read failed session={sess.id}: {exc}")
        return {"ok": True, "session_id": sess.id, "harness": sess.harness,
                "context": sess.last_context or {},
                "last_turn_at": sess.last_used,
                "native_session": sess.native}

    def compact(self, sess: Session) -> dict:
        with sess.lock:
            started = time.time()
            try:
                result = COMPACTERS[sess.harness](sess, self.args.compact_timeout)
            except Exception as exc:
                traceback.print_exc()
                result = {"ok": False, "harness": sess.harness,
                          "error": f"{type(exc).__name__}: {exc}"}
            result.setdefault("session_id", sess.id)
            result.setdefault("duration_ms", int((time.time() - started) * 1000))
            self.registry.save()
            log(f"compact session={sess.id} harness={sess.harness} "
                f"ok={result.get('ok')} ms={result.get('duration_ms')}")
            return result


def load_token(args) -> str:
    if args.token:
        return args.token.strip()
    env = os.environ.get("OX_BRIDGE_TOKEN", "").strip()
    if env:
        return env
    token_file = Path(args.token_file)
    try:
        token = token_file.read_text().strip()
        if token:
            return token
    except FileNotFoundError:
        pass
    token = base64.urlsafe_b64encode(os.urandom(32)).decode().rstrip("=")
    token_file.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(token_file, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as fh:
        fh.write(token + "\n")
    log(f"generated bridge token in {token_file}")
    return token


class Handler(BaseHTTPRequestHandler):
    server_version = "OrionekoXHarnessBridge/" + BRIDGE_VERSION
    protocol_version = "HTTP/1.1"

    @property
    def bridge(self) -> Bridge:
        return self.server.bridge  # type: ignore[attr-defined]

    def log_message(self, fmt, *args):
        log(f"http {self.address_string()} {fmt % args}")

    def send_json(self, code: int, obj: dict) -> None:
        data = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def authorized(self) -> bool:
        header = self.headers.get("Authorization", "")
        expected = "Bearer " + self.bridge.token
        return hmac.compare_digest(header, expected)

    def read_body(self) -> dict:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise ValueError("invalid Content-Length")
        if length <= 0:
            return {}
        if length > MAX_BODY:
            raise ValueError("request body too large")
        raw = self.rfile.read(length)
        try:
            obj = json.loads(raw)
        except Exception:
            raise ValueError("invalid JSON body")
        if not isinstance(obj, dict):
            raise ValueError("JSON body must be an object")
        return obj

    def path_id(self) -> str | None:
        m = re.match(r"^/v1/sessions/([^/]+)(?:/(turns|compact))?$", self.path)
        if not m:
            return None
        return m.group(1)

    def do_GET(self):
        if self.path == "/v1/health":
            if not self.authorized():
                self.send_json(401, {"ok": False, "error": "unauthorized"})
                return
            self.send_json(200, self.bridge.health())
            return
        m = re.match(r"^/v1/sessions/([^/]+)/context$", self.path)
        if m:
            if not self.authorized():
                self.send_json(401, {"ok": False, "error": "unauthorized"})
                return
            sess = self.bridge.registry.get(m.group(1))
            if not sess:
                self.send_json(404, {"ok": False, "error": "session not found"})
                return
            self.send_json(200, self.bridge.context(sess))
            return
        sid = self.path_id()
        if sid:
            if not self.authorized():
                self.send_json(401, {"ok": False, "error": "unauthorized"})
                return
            sess = self.bridge.registry.get(sid)
            if not sess:
                self.send_json(404, {"ok": False, "error": "session not found"})
                return
            self.send_json(200, {"ok": True, "session": sess.public()})
            return
        self.send_json(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        if not self.authorized():
            self.send_json(401, {"ok": False, "error": "unauthorized"})
            return
        try:
            if self.path == "/v1/sessions":
                body = self.read_body()
                sess = self.bridge.create_session(body)
                log(f"session created {sess.id} harness={sess.harness} cwd={sess.cwd}")
                self.send_json(201, {"ok": True, "session": sess.public()})
                return
            m = re.match(r"^/v1/sessions/([^/]+)/(turns|compact)$", self.path)
            if not m:
                self.send_json(404, {"ok": False, "error": "not found"})
                return
            sid, action = m.group(1), m.group(2)
            sess = self.bridge.registry.get(sid)
            if not sess:
                self.send_json(404, {"ok": False, "error": "session not found"})
                return
            body = self.read_body()
            if action == "compact":
                result = self.bridge.compact(sess)
                result["session"] = sess.public()
                self.send_json(200, result)
                return
            if body.get("text") is None or not str(body.get("text")).strip():
                self.send_json(400, {"ok": False, "error": "text is required"})
                return
            self.stream_turn(sess, body)
            return
        except ValueError as exc:
            self.send_json(400, {"ok": False, "error": str(exc)})
        except BrokenPipeError:
            pass
        except Exception as exc:
            traceback.print_exc()
            try:
                self.send_json(500, {"ok": False, "error": f"{type(exc).__name__}: {exc}"})
            except Exception:
                pass

    def stream_turn(self, sess: Session, body: dict) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Accel-Buffering", "no")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        events = 0
        try:
            for event in self.bridge.run_turn(sess, body):
                events += 1
                line = json.dumps(event, ensure_ascii=False) + "\n"
                self.wfile.write(line.encode())
                self.wfile.flush()
            log(f"turn session={sess.id} harness={sess.harness} events={events}")
        except (BrokenPipeError, ConnectionResetError):
            log(f"turn client disconnected session={sess.id} after {events} events")
        except Exception as exc:
            traceback.print_exc()
            try:
                self.wfile.write((json.dumps(
                    {"type": "error", "message": f"{type(exc).__name__}: {exc}"}) + "\n").encode())
                self.wfile.flush()
            except Exception:
                pass

    def do_DELETE(self):
        if not self.authorized():
            self.send_json(401, {"ok": False, "error": "unauthorized"})
            return
        sid = self.path_id()
        if not sid:
            self.send_json(404, {"ok": False, "error": "not found"})
            return
        existed = self.bridge.registry.delete(sid)
        self.send_json(200 if existed else 404,
                       {"ok": existed, "deleted": sid if existed else None})


class BridgeHTTPServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, addr, bridge: Bridge):
        super().__init__(addr, Handler)
        self.bridge = bridge


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="OrionekoX host-side harness bridge for Claude Code, "
                    "CommandCode, OpenCode and OpenClaw.")
    parser.add_argument("--host", default="127.0.0.1",
                        help="bind address (default loopback; use 0.0.0.0 for emulator/LAN)")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--workdir", default=str(Path.home() / "orionekox-harness"),
                        help="default working directory")
    parser.add_argument("--allow-root", action="append", default=None,
                        help="allowed working-directory root (repeatable; defaults to home)")
    parser.add_argument("--token", default="", help="bearer token (prefer --token-file)")
    parser.add_argument("--token-file",
                        default=str(Path.home() / ".config" / "orionekox" /
                                    "harness-bridge-token"))
    parser.add_argument("--state-dir", default=str(STATE_DIR))
    parser.add_argument("--turn-timeout", type=int, default=600)
    parser.add_argument("--compact-timeout", type=int, default=240)
    parser.add_argument("--enable-fake", action="store_true",
                        help="enable the deterministic fake harness (tests only)")
    parser.add_argument("--print-token", action="store_true",
                        help="print the resolved token to stdout and exit")
    parser.add_argument("--version", action="version", version=BRIDGE_VERSION)
    args = parser.parse_args(argv)
    args.workdir = str(Path(args.workdir).expanduser())
    if not args.allow_root:
        args.allow_root = [str(Path.home())]
    return args


def main(argv=None) -> int:
    args = parse_args(argv)
    try:
        Path(args.workdir).mkdir(parents=True, exist_ok=True)
    except Exception as exc:
        log(f"cannot create workdir: {exc}")
        return 2
    bridge = Bridge(args)
    if args.print_token:
        print(bridge.token)
        return 0
    server = BridgeHTTPServer((args.host, args.port), bridge)
    log(f"bridge {BRIDGE_VERSION} listening on {args.host}:{args.port} "
        f"state={args.state_dir} fake={args.enable_fake}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        OPENCODE_SERVER.stop_all()
        log("bridge stopped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
