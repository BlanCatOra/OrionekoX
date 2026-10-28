#!/usr/bin/env python3
"""Writes a deterministic memory-benchmark agent and 60-message history to /tmp.

Usage: python3 tools/memory_bench_fixture.py [output-dir]
Then push agent.json/chat.json/AGENTS.md into the app's oxagents/<id>/ folder.
"""
import json
import os
import sys
import time

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/ox-memory-bench"
os.makedirs(OUT, exist_ok=True)
ID = "qa_mem_bench"
AGENTS_MD = "# QA Memory Bench\n\nDeterministic fixture for local memory retrieval."
NOW = int(time.time() * 1000) - 3_600_000
FACTS = {
    1: "My name is Lin Zhou. My birthday is March 14. I like black coffee.",
    5: "My favorite color is blue.",
    10: "I live in Hangzhou and work as a backend engineer.",
    15: "My birthday is March 15 now, please use the new date.",
    20: "Actually I do not like blue anymore. My favorite color is green.",
}
messages = []
for i in range(30):
    user = FACTS.get(i, f"Small talk message {i}: the weather is fine and the project is going well.")
    messages.append({"role": "user", "content": user, "ts": NOW + i * 120000})
    messages.append({"role": "assistant", "content": f"Noted. Reply number {i}.",
                     "ts": NOW + i * 120000 + 30000})

agent = {
    "spec": "1", "id": ID, "name": "QA Memory Bench", "emoji": "🧪",
    "agents_md": AGENTS_MD, "base_url": "http://10.0.2.2:18999/v1", "api_key": "",
    "model": "mock", "built_in": False, "pinned": False, "tg_id": 0, "remark": "",
    "first_mes": "", "avatar_path": "", "provider_id": "custom", "headers": [],
    "skill_ids": [], "temperature": 0.7, "top_p": 1.0, "max_tokens": 0,
    "context_length": 0, "presence_penalty": 0, "frequency_penalty": 0, "top_k": 0,
    "seed": -1, "stop": "", "reasoning_effort": "auto", "tool_choice": "auto",
    "parallel_tool_calls": True, "extra_body": "", "tools_policy": {},
    "memory_mode": "normal", "memory_threshold": 45, "memory_recall": 6,
}
with open(os.path.join(OUT, "agent.json"), "w", encoding="utf-8") as f:
    json.dump(agent, f, ensure_ascii=False)
with open(os.path.join(OUT, "chat.json"), "w", encoding="utf-8") as f:
    json.dump(messages, f, ensure_ascii=False)
with open(os.path.join(OUT, "AGENTS.md"), "w", encoding="utf-8") as f:
    f.write(AGENTS_MD)
print(f"wrote {len(messages)} messages and agent {ID} to {OUT}")
