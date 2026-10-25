"""Rebuild D:/task/docs/*.md from the session transcript.

Replays every recorded Write/Edit operation in order, then re-applies the
symbol-normalisation and legend transformations that were originally done by
throw-away shell scripts (and which are NOT recorded as Edit operations).

Output goes to D:/task/_recovery/ so nothing is overwritten until reviewed.
"""
import json
import os
import re
import sys

TRANSCRIPT = r"C:/Users/PC/.qoder/projects/D--task/bcfd667e-1859-41cb-b7e5-73d7999a2bad.jsonl"
OUT_DIR = r"D:/task/_recovery"

TARGETS = [
    "00-AUDIT-SPEC.md",
    "01-MODULES-BOUNDARIES.md",
    "02A-DATA-MODEL-CORE.md",
    "02B-DATA-MODEL-PLATFORM.md",
    "04-RBAC-MATRIX.md",
    "05-WORKFLOW-STATE-MACHINES.md",
    "06-IMPLEMENTATION-PLAN.md",
    "08-DECISION-REGISTER.md",
]


def norm_path(p):
    return os.path.basename(str(p).replace("\\", "/"))


def collect_ops(transcript, targets):
    ops = {t: [] for t in targets}
    for line in open(transcript, encoding="utf-8"):
        try:
            d = json.loads(line)
        except Exception:
            continue
        if d.get("type") != "assistant":
            continue
        msg = d.get("message") or {}
        for c in msg.get("content") or []:
            if not isinstance(c, dict) or c.get("type") != "tool_use":
                continue
            name = c.get("name")
            if name not in ("Write", "Edit"):
                continue
            inp = c.get("input") or {}
            base = norm_path(inp.get("file_path", ""))
            if base not in targets:
                continue
            ops[base].append((name, inp))
    return ops


def replay(ops):
    content = None
    applied = 0
    skipped = []
    for i, (name, inp) in enumerate(ops):
        if name == "Write":
            content = inp.get("content") or ""
            applied += 1
            continue
        old = inp.get("old_string") or ""
        new = inp.get("new_string") or ""
        if content is None:
            skipped.append((i, "edit-before-write"))
            continue
        if old not in content:
            skipped.append((i, "old_string-not-found:" + old[:50].replace("\n", "\\n")))
            continue
        if inp.get("allow_multiple"):
            content = content.replace(old, new)
        else:
            content = content.replace(old, new, 1)
        applied += 1
    return content, applied, skipped


# ---------------------------------------------------------------- transforms
EMOJI_MAP = {
    "\u2705": "Y",      # white heavy check mark
    "\u274c": "N",      # cross mark
    "\u26a0": "!",      # warning sign
    "\u2610": "[ ]",    # ballot box
    "\ufe0f": "",       # variation selector-16
    "\U0001f534": "",   # red circle
    "\U0001f7e0": "",   # orange circle
    "\U0001f7e1": "",   # yellow circle
    "\U0001f512": "",   # lock
    "\u23f8": "",       # double vertical bar (pause)
    "\u23f3": "",       # hourglass flowing sand
    "\U0001f501": "",   # clockwise arrows (repeat)
    "\U0001f507": "",   # speaker with cancellation stroke (mute)
}

LEGEND = (
    "**Chú giải ký hiệu dùng trong các bảng:** "
    "`Y` = có / được phép / cơ chế giữ đầy đủ bất biến; "
    "`N` = không / không được phép / không thể dùng cơ chế đó; "
    "`!` = một phần (phần còn lại phải xử lý ở service layer hoặc đang chờ quyết định); "
    "`—` = không áp dụng."
)


def transform_symbols(text):
    n = 0
    for k, v in EMOJI_MAP.items():
        if k in text:
            n += text.count(k)
            text = text.replace(k, v)
    return text, n


def transform_spacing(text):
    """Collapse leftover double space right after a table pipe (same line only)."""
    return re.sub(r"\|[ \t]{2,}(?=\S)", "| ", text)


def transform_marker_prose(text):
    text = text.replace("Y Một phần", "Y (một phần)")
    text = text.replace("N Không thể", "N (không thể)")
    return text


def transform_legend(text):
    if "Chú giải ký hiệu dùng trong các bảng" in text:
        return text
    idx = text.find("\n---\n")
    if idx == -1:
        return text
    return text[:idx] + "\n\n" + LEGEND + text[idx:]


def transform_setext(text):
    return text.replace("`—` = không áp dụng.\n---\n", "`—` = không áp dụng.\n\n---\n")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    ops = collect_ops(TRANSCRIPT, TARGETS)
    report = []
    for t in TARGETS:
        if not ops[t]:
            report.append((t, "NO OPS FOUND", 0, 0, 0))
            continue
        content, applied, skipped = replay(ops[t])
        if content is None:
            report.append((t, "REPLAY FAILED", 0, 0, 0))
            continue
        content, n_emoji = transform_symbols(content)
        content = transform_spacing(content)
        content = transform_marker_prose(content)
        content = transform_legend(content)
        content = transform_setext(content)
        out = os.path.join(OUT_DIR, t)
        with open(out, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(content)
        report.append((t, "ok", applied, len(skipped), n_emoji))
        for s in skipped:
            print("  SKIPPED %s op#%d %s" % (t, s[0], s[1]))
        print("%-34s ops_applied=%-3d skipped=%-2d emoji=%-4d lines=%-5d bytes=%d"
              % (t, applied, len(skipped), n_emoji,
                 content.count("\n") + 1, len(content.encode("utf-8"))))
    print("\nrecovery dir:", OUT_DIR)


if __name__ == "__main__":
    main()
