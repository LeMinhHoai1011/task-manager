# -*- coding: utf-8 -*-
"""Reconcile 03-API-CONTRACT.md with 04-RBAC-MATRIX.md:
   (a) T-nn (RBAC action) -> TK-nn, per AUD-52;
   (b) drop deleted codes T-15/T-16 -> TK-03 / C-06, per AUD-53b;
   (d) fix NFR range and a few ambiguous T-01/T-02 citations.
   Ambiguous citations are fixed BEFORE the blanket rename.
   Every replacement asserts its expected occurrence count."""
import io, os, re, sys

P = os.path.join(os.path.dirname(os.path.abspath(__file__)), '03-API-CONTRACT.md')
with io.open(P, 'r', encoding='utf-8') as f:
    txt = f.read()
orig = txt
report = []

def rep(old, new, count=1, label=''):
    global txt
    n = txt.count(old)
    if n != count:
        print('FAIL [%s]: expected %d, found %d for: %s' % (label, count, n, old[:100]))
        sys.exit(1)
    txt = txt.replace(old, new)
    report.append('%-44s x%d' % (label, count))

# --- (d) NFR range -------------------------------------------------------
rep('NFR-01..NFR-25', 'NFR-01..NFR-24', 1, 'NFR range 25->24')

# --- line 8: action family list + transition clarifier -------------------
rep('`04-RBAC-MATRIX.md` (mã action `A-*`, `W-*`, `P-*`, `T-*`, `F-*`, `FL-*`, `C-*`, `N-*`, `AI-*`, `R-*`, `S-*`; pipeline `P1..P9`)',
    '`04-RBAC-MATRIX.md` (mã action `A-*`, `W-*`, `P-*`, `TK-*`, `F-*`, `FL-*`, `CM-*`, `C-*`, `N-*`, `AI-*`, `R-*`, `S-*`; pipeline `P1..P9`)',
    1, 'header: 04 action families')
rep('`05-WORKFLOW-STATE-MACHINES.md` (`T-01..T-12`, `ST-01..ST-05`',
    '`05-WORKFLOW-STATE-MACHINES.md` (`T-01..T-12` = **transition**, không được nhầm với `TK-*` là mã action của 04 — AUD-52; `ST-01..ST-05`',
    1, 'header: 05 transition clarifier')

# --- (d2) ambiguous citations (must run BEFORE the blanket rename) -------
rep('`has_unmet_blocker` là cờ để gate `T-02` ở UI',
    '`has_unmet_blocker` là cờ để gate **start** ở UI (`F-01` / transition `05 T-01` / `EP-125`)',
    1, 'ambiguous T-02 -> start (DTO note)')
rep('`EP-125` (`T-02` start)', '`EP-125` (`F-01` start; transition `05 T-01`)',
    1, 'ambiguous T-02 -> start (C-14)')
rep('Project phải tồn tại và actor có `T-01` |',
    'Project phải tồn tại và actor có `TK-01` (với `EP-092`) hoặc `TK-02` (với `EP-093`) |',
    1, 'EP-092/EP-093 create code')

# --- (b) deleted codes (before blanket rename; T-15/T-16 are out of range anyway)
rep('`T-15`', '`TK-03`', 4, 'T-15 -> TK-03 (AUD-53b)')
rep('`C-06`, `T-16`', '`C-06`', 1, 'T-16 dropped, keep C-06 (AUD-53b)')

# --- (a) backticked `T-01`..`T-14` -> `TK-nn` ---------------------------
pat = re.compile(r'`T-(0[1-9]|1[0-4])`')
txt, n = pat.subn(lambda m: '`TK-%s`' % m.group(1), txt)
report.append('%-44s x%d' % ('backticked `T-01..T-14` -> `TK-nn`', n))

# --- (a2) bare T-nn in prose --------------------------------------------
rep('(T-14, FL-04, P-10, W-07)', '(TK-14, FL-04, P-10, W-07)', 1, 'bare T-14 (include_deleted)')
rep('`title`/`description` theo T-04; `priority`/`milestone_id` theo T-05',
    '`title`/`description` theo TK-04; `priority`/`milestone_id` theo TK-05', 1, 'bare T-04/T-05 (EP-097)')
rep('`due_timezone` theo T-06.', '`due_timezone` theo TK-06.', 1, 'bare T-06 (EP-097)')
rep('(423; T-05 cho MANAGER vẫn được', '(423; TK-05 cho MANAGER vẫn được', 1, 'bare T-05 (EP-097)')
rep('ASG tự gỡ mình (T-07 cho phép ASG tự gỡ)', 'ASG tự gỡ mình (TK-07 cho phép ASG tự gỡ)', 1, 'bare T-07 (EP-106)')
rep('kèm `reason` (T-12);', 'kèm `reason` (TK-12);', 1, 'bare T-12 (EP-119)')

with io.open(P, 'w', encoding='utf-8', newline='\n') as f:
    f.write(txt)

print('\n'.join(report))
print('--- leftovers: any remaining `T-nn` that is NOT a 05 transition citation ---')
for i, ln in enumerate(txt.split('\n')):
    if re.search(r'`T-\d\d`|(?<!05 )\bT-1[3-6]\b', ln):
        print(i + 1, ln[:170])
print('bytes %d -> %d' % (len(orig.encode('utf-8')), len(txt.encode('utf-8'))))
