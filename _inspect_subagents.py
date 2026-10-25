import json
import glob
import os

SUB = r"C:/Users/PC/.qoder/projects/D--task/bcfd667e-1859-41cb-b7e5-73d7999a2bad/subagents"

for p in sorted(glob.glob(os.path.join(SUB, "agent-*.jsonl"))):
    ops = []
    for line in open(p, encoding="utf-8"):
        try:
            d = json.loads(line)
        except Exception:
            continue
        if d.get("type") != "assistant":
            continue
        for c in (d.get("message") or {}).get("content") or []:
            if not isinstance(c, dict) or c.get("type") != "tool_use":
                continue
            if c.get("name") not in ("Write", "Edit"):
                continue
            inp = c.get("input") or {}
            fp = os.path.basename(str(inp.get("file_path", "")).replace("\\", "/"))
            ops.append((c["name"], fp,
                        len(inp.get("content") or ""),
                        len(inp.get("old_string") or ""),
                        len(inp.get("new_string") or "")))
    print("==", os.path.basename(p), "write/edit ops:", len(ops))
    for o in ops:
        print("   ", o)
