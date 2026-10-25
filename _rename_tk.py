#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AUD-52 fix: rename 04's RBAC task-action family T-01..T-16 -> TK-01..TK-16
so that bare `T-nn` unambiguously means a 05 state transition.
Stable files only: 04, 05 (the 3 'RBAC T-nn' citations), 08 (the qualified range).
03 and 07 are being written by subagents -> handled separately later."""
import re, sys

TARGETS = {
    'docs/04-RBAC-MATRIX.md': 'all',
    'docs/05-WORKFLOW-STATE-MACHINES.md': 'rbac-only',
    'docs/08-DECISION-REGISTER.md': 'qualified-only',
}

# bare T-nn not preceded by a letter or hyphen (so ST-01, FX-T-20, PR-T-1 are untouched)
BARE = re.compile(r'(?<![A-Za-z0-9_-])T-(\d{2})\b')

def repl(m):
    n = int(m.group(1))
    return 'TK-%02d' % n if 1 <= n <= 16 else m.group(0)

report = []
for path, mode in TARGETS.items():
    with open(path, encoding='utf-8') as f:
        src = f.read()
    if mode == 'all':
        out, n = BARE.subn(repl, src)
        report.append('%-38s mode=%-16s replaced=%d' % (path, mode, n))
    elif mode == 'rbac-only':
        # Only citations explicitly attributed to the RBAC matrix. These lines
        # ALSO contain 05's own transitions (e.g. "**T-11** ... (RBAC T-13)"),
        # so a blanket per-line sub would corrupt them -> use literal pairs.
        pairs = [
            ('(RBAC T-13)', '(RBAC TK-13)'),
            ('(RBAC T-14)', '(RBAC TK-14)'),
            ('RBAC T-04/T-07/T-09/T-11/FL-01/FL-03',
             'RBAC TK-04/TK-07/TK-09/TK-11/FL-01/FL-03'),
        ]
        out = src
        cnt = 0
        for a, b in pairs:
            if a in out:
                cnt += 1
                out = out.replace(a, b)
        report.append('%-38s mode=%-16s replaced=%d/%d pairs' % (path, mode, cnt, len(pairs)))
    else:  # qualified-only: the single "`04 §7` (T-01..T-16)" range
        out = src.replace('`04 §7` (T-01..T-16)', '`04 §7` (TK-01..TK-16)')
        report.append('%-38s mode=%-16s replaced=%d' % (path, mode,
                      1 if out != src else 0))
    if out != src:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(out)

print('\n'.join(report))
print('\n--- residual bare T-nn per file (should be 05-transitions only) ---')
for path in TARGETS:
    with open(path, encoding='utf-8') as f:
        s = f.read()
    left = sorted(set('T-' + m for m in BARE.findall(s)))
    print('%-38s %s' % (path, left if left else 'none'))
