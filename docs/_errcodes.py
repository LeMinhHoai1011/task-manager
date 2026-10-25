# -*- coding: utf-8 -*-
"""Verify 03-API-CONTRACT.md error-code bookkeeping:
 - declared per-domain counts vs actual codes listed
 - declared totals vs sums
 - codes referenced by EP rows but not registered in §2.3/§2.4
 - codes registered but never referenced
Read-only."""
import io, os, re, collections

P = os.path.join(os.path.dirname(os.path.abspath(__file__)), '03-API-CONTRACT.md')
lines = io.open(P, encoding='utf-8').read().split('\n')

CODE = re.compile(r'`([A-Z][A-Z0-9_]{3,})`')

# --- collect §2.3 (cross-cutting) and §2.4 (per-domain) registries --------
sec23, sec24 = [], []          # list of (lineno, domain, declared, [codes])
mode = None
for i, ln in enumerate(lines, 1):
    if ln.startswith('### 2.3'):
        mode = 23; continue
    if ln.startswith('### 2.4'):
        mode = 24; continue
    if ln.startswith('## 3.'):
        mode = None
    if mode == 23 and ln.startswith('|'):
        cells = [c.strip() for c in ln.strip('|').split('|')]
        if len(cells) >= 1 and cells[0]:
            sec23.append((i, cells[0], [m for m in CODE.findall(ln)]))
    if mode == 24 and ln.startswith('|'):
        cells = [c.strip() for c in ln.strip('|').split('|')]
        if len(cells) >= 3 and cells[1].isdigit():
            sec24.append((i, cells[0], int(cells[1]), CODE.findall(ln)))

registered = set()
for _, _, codes in sec23:
    registered |= set(c for c in codes if '_' in c)
for _, _, _, codes in sec24:
    registered |= set(c for c in codes if '_' in c)

print('== §2.4 per-domain declared vs actual ==')
tot_declared = tot_actual = 0
bad = 0
for ln, dom, declared, codes in sec24:
    real = [c for c in codes if '_' in c]
    tot_declared += declared
    tot_actual += len(real)
    flag = 'OK ' if declared == len(real) else 'MISMATCH'
    if declared != len(real):
        bad += 1
    print('  %-46s declared=%3d actual=%3d %s' % (dom[:46], declared, len(real), flag))
print('  --> sum declared=%d  sum actual=%d  mismatches=%d' % (tot_declared, tot_actual, bad))

# duplicates inside §2.4
allcodes = [c for _, _, _, codes in sec24 for c in codes if '_' in c]
dup = [c for c, n in collections.Counter(allcodes).items() if n > 1]
print('  duplicate codes across §2.4 rows:', dup if dup else 'none')

# --- totals line ---------------------------------------------------------
for i, ln in enumerate(lines, 1):
    if 'Tổng cộng toàn tài liệu' in ln or ln.startswith('**Tổng: 81'):
        print('line %d: %s' % (i, ln[:200]))

# --- codes referenced by EP rows ----------------------------------------
used = collections.Counter()
ep_lines = {}
for i, ln in enumerate(lines, 1):
    m = re.match(r'\| EP-(\d{3}) \|', ln)
    if m:
        cells = ln.strip('|').split('|')
        if len(cells) >= 10:
            for c in CODE.findall(cells[-1]):
                if '_' in c:
                    used[c] += 1
                    ep_lines.setdefault(c, []).append('EP-' + m.group(1))

unregistered = {c: v for c, v in used.items() if c not in registered}
print('\n== EP error codes NOT registered in §2.3/§2.4 ==')
for c, v in sorted(unregistered.items()):
    print('  %-46s used by %s' % (c, ','.join(sorted(set(ep_lines[c]))[:6])))
if not unregistered:
    print('  none')

unused = sorted(c for c in registered if c not in used and '_' in c)
print('\n== registered but never referenced by an EP row (%d) ==' % len(unused))
print('  ' + ', '.join(unused[:60]))
print('\nEP rows scanned:', len(ep_lines) and sum(len(v) for v in ep_lines.values()))
