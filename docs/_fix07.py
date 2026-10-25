# -*- coding: utf-8 -*-
"""Fix 07-TEST-PLAN.md ID hygiene (AUD-52 follow-up):
  (a) RBAC action citations T-13 / T-01..T-16  -> TK-nn / TK-01..TK-14
  (b) bare fixture labels T-<n> -> FX-T-<n> on the specific lines where they
      denote dataset fixtures (05's transitions T-01..T-12 elsewhere untouched)
  (c) header: AUD range 50 -> 58, action family T-xx -> TK-xx + CM-xx
Every replacement asserts its expected count; nothing else is touched."""
import io, os, re, sys

P = os.path.join(os.path.dirname(os.path.abspath(__file__)), '07-TEST-PLAN.md')
lines = io.open(P, encoding='utf-8').read().split('\n')
before = '\n'.join(lines)
log = []

def sub_line(idx, pattern, repl, expect, label):
    """idx is 1-based line number."""
    i = idx - 1
    new, n = re.subn(pattern, repl, lines[i])
    if n != expect:
        print('FAIL [%s] line %d: expected %d subs, got %d' % (label, idx, expect, n))
        print('   line was: %s' % lines[i][:200])
        sys.exit(1)
    lines[i] = new
    log.append('%-40s line %-5d x%d' % (label, idx, n))

def rep(old, new, count, label):
    global lines
    txt = '\n'.join(lines)
    n = txt.count(old)
    if n != count:
        print('FAIL [%s]: expected %d, found %d :: %s' % (label, count, n, old[:90]))
        sys.exit(1)
    lines = (txt.replace(old, new)).split('\n')
    log.append('%-40s x%d' % (label, count))

# ---------- (c) header ----------
rep('(AUD-01..50, DEC-nn, FACT mục B)', '(AUD-01..58, DEC-nn, FACT mục B)', 1, 'header AUD range')
rep('action A-xx/W-xx/P-xx/T-xx/F-xx/FL-xx/C-xx/N-xx/AI-xx/R-xx/S-xx',
    'action A-xx/W-xx/P-xx/**TK-xx**/F-xx/FL-xx/**CM-xx**/C-xx/N-xx/AI-xx/R-xx/S-xx',
    1, 'header action families')
rep('`05-WORKFLOW-STATE-MACHINES.md` (T-01..T-12, ST-01..05',
    '`05-WORKFLOW-STATE-MACHINES.md` (T-01..T-12 = **transition**, phân biệt với `TK-xx` của 04 — AUD-52; ST-01..05',
    1, 'header transition clarifier')

# ---------- (a) RBAC citations ----------
rep('`P-08`, `T-13`, `F-04`', '`P-08`, `TK-13`, `F-04`', 1, 'trace-legend T-13 -> TK-13')
rep('Nhóm 04; T-01..T-16; I-17', 'Nhóm 04; TK-01..TK-14; I-17', 1, 'TC-F-028 trace range')
rep('(2) theo T-xx trong 04 §7;', '(2) theo `TK-09` trong `04 §7` (PR = Y, PG = N);', 1, 'TC-F-028 step 2')

# ---------- (b) bare fixture labels ----------
FIX = r'(?<!FX-)(?<![\w-])T-(\d{1,3})\b'
for ln, expect, label in [
    (226, 2, 'fixtures dependency'),
    (233, 5, 'fixtures soft-delete group'),
    (292, 4, 'fixtures subtask T-101'),
    (293, 5, 'fixtures T-1/T-2'),
    (298, 12, 'fixtures T-30/31/32'),
    (316, 1, 'fixture T-60 in path'),
    (368, 1, 'fixture T-50 submit'),
    (518, 2, 'fixtures T-20/T-23'),
]:
    sub_line(ln, FIX, r'FX-T-\1', expect, label)

out = '\n'.join(lines)
io.open(P, 'w', encoding='utf-8', newline='\n').write(out)
print('\n'.join(log))

# ---------- verification: remaining bare T-nn must all be transitions ----------
rem = []
for i, ln in enumerate(out.split('\n'), 1):
    for m in re.finditer(r'(?<!FX-)(?<![\w-])T-(\d{1,3})\b', ln):
        s = max(0, m.start() - 30); e = min(len(ln), m.end() + 14)
        rem.append('%4d %-7s ...%s...' % (i, m.group(0), ln[s:e]))
print('\nremaining bare T-<n>: %d' % len(rem))
for r in rem:
    print(r)
print('bytes %d -> %d' % (len(before.encode()), len(out.encode())))
