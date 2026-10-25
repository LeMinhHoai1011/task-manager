#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Cross-consistency check for D:/task/docs. Read-only. Ignores _*.md scratch files."""
import re, os, collections

DOCS = 'docs'
FILES = ['00-AUDIT-SPEC.md','01-MODULES-BOUNDARIES.md','02A-DATA-MODEL-CORE.md',
         '02B-DATA-MODEL-PLATFORM.md','03-API-CONTRACT.md','04-RBAC-MATRIX.md',
         '05-WORKFLOW-STATE-MACHINES.md','06-IMPLEMENTATION-PLAN.md',
         '07-TEST-PLAN.md','08-DECISION-REGISTER.md']

txt = {}
for f in FILES:
    p = os.path.join(DOCS, f)
    txt[f] = open(p, encoding='utf-8').read() if os.path.exists(p) else ''
allblob = '\n'.join(txt.values())
issues = []

def ids(pattern, blob):
    return set(int(m) for m in re.findall(pattern, blob))

def expect(name, pattern, declared_in, lo, hi):
    decl = ids(pattern, txt[declared_in])
    used = ids(pattern, allblob)
    missing = sorted(used - decl)
    gaps = sorted(set(range(lo, hi+1)) - decl)
    print('%-6s declared=%-4d used=%-4d range=%d..%d' % (name, len(decl), len(used), lo, hi))
    if missing:
        issues.append('%s: used but NOT declared in %s -> %s' % (name, declared_in, missing[:12]))
    if gaps:
        issues.append('%s: gap in declared sequence -> %s' % (name, gaps[:12]))

expect('AUD',  r'\bAUD-(\d{2})\b',  '00-AUDIT-SPEC.md', 1, 53)
expect('DEC',  r'\bDEC-(\d{2})\b',  '08-DECISION-REGISTER.md', 1, 54)
expect('ASM',  r'\bASM-(\d{2})\b',  '08-DECISION-REGISTER.md', 1, 37)
expect('MOD',  r'\bMOD-(\d{2})\b',  '01-MODULES-BOUNDARIES.md', 1, 15)
expect('DEP',  r'\bDEP-(\d{2})\b',  '01-MODULES-BOUNDARIES.md', 1, 10)
expect('ENF',  r'\bENF-(\d{2})\b',  '01-MODULES-BOUNDARIES.md', 1, 7)
expect('MIG',  r'\bMIG-(\d{2})\b',  '02B-DATA-MODEL-PLATFORM.md', 1, 12)
expect('I',    r'\bI-(\d{2})\b',    '02A-DATA-MODEL-CORE.md', 1, 23)
expect('RACE', r'\bRACE-(\d{2})\b','05-WORKFLOW-STATE-MACHINES.md', 1, 15)
expect('REC',  r'\bREC-(\d{2})\b',  '05-WORKFLOW-STATE-MACHINES.md', 1, 8)
expect('ARCH', r'\bARCH-(\d{2})\b','08-DECISION-REGISTER.md', 1, 10)
expect('NFR',  r'\bNFR-(\d{2})\b', '08-DECISION-REGISTER.md', 1, 24)

# --- RBAC action families declared in 04 --------------------------------
# Lines that explicitly narrate a rename/removal may legitimately mention codes
# that no longer exist.
HIST = ('draft trước', 'đã bỏ', 'AUD-52', 'AUD-53', 'thành **`TK-')
FAMS = ['A','W','P','TK','F','FL','C','N','AI','R','S','CM']
act = collections.defaultdict(set)
for m in re.finditer(r'\b(' + '|'.join(FAMS) + r')-(\d{2})\b', txt['04-RBAC-MATRIX.md']):
    act[m.group(1)].add(int(m.group(2)))
print('\n04 action families: ' + ', '.join('%s=%d' % (k, len(v)) for k, v in sorted(act.items())))
# citations elsewhere must exist in 04 (skip FX-* fixtures and 05's transitions T-nn)
CITE = re.compile(r'(?<![A-Za-z0-9_-])(' + '|'.join(FAMS) + r')-(\d{2})\b')
for f in FILES:
    if f.startswith('04-'):
        continue
    for i, line in enumerate(txt[f].splitlines(), 1):
        if any(h in line for h in HIST):
            continue          # narrative about removed/renamed codes
        for m in CITE.finditer(line):
            fam, n = m.group(1), int(m.group(2))
            if fam in act and n not in act[fam]:
                issues.append('%s:%d cites %s-%02d which 04 does not define' % (f, i, fam, n))
# 05 transitions T-01..T-12 are a separate namespace. A bare T-13..T-16 can only
# be a stale citation of 04's OLD task-action family -> must become TK-nn.
stale = []
for f in FILES:
    for i, line in enumerate(txt[f].splitlines(), 1):
        if any(h in line for h in HIST):
            continue
        for m in re.finditer(r'(?<![A-Za-z0-9_-])T-(1[3-6])\b', line):
            stale.append('%s:%d T-%s' % (f, i, m.group(1)))
print('stale T-13..T-16 citations (should be TK-*): %d' % len(stale))
for s in stale:
    issues.append('STALE-ACTION-ID ' + s)
# informational only: 07 uses bare T-nn (n>=20) as fixture labels, ambiguous in prose
fix = collections.Counter()
for m in re.finditer(r'(?<![A-Za-z0-9_-])T-(\d{2})\b', txt['07-TEST-PLAN.md']):
    if int(m.group(1)) >= 20:
        fix['T-' + m.group(1)] += 1
if fix:
    print('NOTE 07 fixture labels written without FX- prefix: %s' % dict(fix))

# --- EP / TC ------------------------------------------------------------
ep_decl = ids(r'\bEP-(\d{3})\b', txt['03-API-CONTRACT.md'])
ep_used = ids(r'\bEP-(\d{3})\b', allblob)
print('EP     declared=%-4d used=%-4d' % (len(ep_decl), len(ep_used)))
dang = sorted(ep_used - ep_decl)
if dang:
    issues.append('EP: used outside 03 but not declared there -> %s' % dang[:15])
tc_decl = set(re.findall(r'\bTC-[A-Z0-9]+-\d+\b', txt['07-TEST-PLAN.md']))
tc_used = set(re.findall(r'\bTC-[A-Z0-9]+-\d+\b', allblob))
print('TC     declared=%-4d used=%-4d' % (len(tc_decl), len(tc_used)))
dtc = sorted(tc_used - tc_decl)
if dtc:
    issues.append('TC: used outside 07 but not declared there -> %s' % dtc[:15])

# --- table inventory vs ownership matrix --------------------------------
inv = re.findall(r'^\|\s*\d+\s*\|\s*`([a-z_0-9]+)`\s*\|\s*(MOD-\d{2})',
                 txt['02B-DATA-MODEL-PLATFORM.md'], re.M)
ROW = re.compile(r'^\|\s*((?:`[a-z_*0-9]+`(?:,\s*)?)+)\s*\|\s*(MOD-\d{2})\s*\|')
owned = {}
for line in txt['01-MODULES-BOUNDARIES.md'].splitlines():
    m = ROW.match(line)
    if m:
        for t in re.findall(r'`([a-z_*0-9]+)`', m.group(1)):
            owned.setdefault(t, m.group(2))
print('\n02B inventory=%d  01 ownership cells=%d' % (len(inv), len(owned)))
if len(inv) != 65:
    issues.append('02B inventory has %d rows, expected 65' % len(inv))
inv_map = dict(inv)
mismatch = [(t, inv_map[t], owned[t]) for t in inv_map if t in owned and owned[t] != inv_map[t]]
def covered(t):
    return t in owned or any(k.endswith('*') and t.startswith(k[:-1]) for k in owned)
notin01 = [t for t in inv_map if not covered(t)]
orphan = [t for t in owned if not t.endswith('*') and t not in inv_map]
print('owner mismatches=%d  inventory tables missing from 01=%d  01 tables missing from inventory=%d'
      % (len(mismatch), len(notin01), len(orphan)))
if mismatch:
    issues.append('owner mismatch 02B vs 01 -> %s' % mismatch)
if notin01:
    issues.append('in 02B inventory but not in 01 matrix -> %s' % notin01)
if orphan:
    issues.append('in 01 matrix but not in 02B inventory -> %s' % orphan)

# --- forbidden premature-success claims ---------------------------------
BAD = ['đã pass', 'pass ✅', 'kết quả: pass', 'đã chạy test', 'đã deploy', 'đã nghiệm thu đạt']
for f in FILES:
    for i, line in enumerate(txt[f].splitlines(), 1):
        low = line.lower()
        for kw in BAD:
            if kw in low:
                issues.append('PREMATURE-CLAIM %s:%d %r' % (f, i, line.strip()[:100]))

# --- placeholders (TODO is a legitimate task state -> excluded; mermaid {{ }} ok)
PH = re.compile(r'\bFIXME\b|\bTBD\b|<!-- CONT-|\{\{|Lorem ipsum|\bXXX\b')
ph = []
for f in FILES:
    fence = False
    for i, line in enumerate(txt[f].splitlines(), 1):
        if line.lstrip().startswith('```'):
            fence = not fence
            continue
        if fence:
            continue
        if PH.search(line):
            ph.append('%s:%d %s' % (f, i, line.strip()[:90]))
print('placeholder markers: %d' % len(ph))
for p in ph[:10]:
    issues.append('PLACEHOLDER ' + p)

# --- severity counts must agree between 00 §F and 08 Part E -------------
m00 = re.search(r'\*\*Tổng cộng: (\d+) phát hiện audit .*?\(DEC-01\.\.DEC-(\d+)\): (\d+) BLOCKER, (\d+) MAJOR, (\d+) MINOR',
                txt['00-AUDIT-SPEC.md'], re.S)
m08 = re.search(r'\*\*(\d+)\*\* \((\d+) BLOCKER \+ (\d+) MAJOR \+ (\d+) MINOR\)', txt['08-DECISION-REGISTER.md'])
if m00 and m08:
    a = (int(m00.group(3)), int(m00.group(4)), int(m00.group(5)))
    b = (int(m08.group(2)), int(m08.group(3)), int(m08.group(4)))
    print('\nseverity 00=%s 08=%s' % (a, b))
    if a != b:
        issues.append('severity counts disagree: 00=%s 08=%s' % (a, b))
    if sum(a) != int(m00.group(2)):
        issues.append('00 §F: BLOCKER+MAJOR+MINOR=%d != stated DEC total %s' % (sum(a), m00.group(2)))
    if sum(a) != int(m08.group(1)):
        issues.append('08 Part E: parts do not sum to total')
else:
    issues.append('could not parse severity totals (00 or 08)')

print('\n' + '=' * 64)
if issues:
    print('ISSUES (%d):' % len(issues))
    for s in issues:
        print('  - ' + s)
else:
    print('NO ISSUES')
