# -*- coding: utf-8 -*-
"""Quick completeness scan of D:\\task docs. Read-only."""
import io, os, re, sys, glob

D = 'docs'
FILES = ['00-AUDIT-SPEC.md','01-MODULES-BOUNDARIES.md','02A-DATA-MODEL-CORE.md','02B-DATA-MODEL-PLATFORM.md',
         '03-API-CONTRACT.md','04-RBAC-MATRIX.md','05-WORKFLOW-STATE-MACHINES.md','06-IMPLEMENTATION-PLAN.md',
         '07-TEST-PLAN.md','08-DECISION-REGISTER.md']
out = []
def w(s=''): out.append(s)

txt = {}
for f in FILES:
    p = os.path.join(D, f)
    txt[f] = io.open(p, encoding='utf-8').read() if os.path.exists(p) else None

w('== 1. File thieu / trong')
for f in FILES:
    if txt[f] is None: w('  MISSING ' + f)
    elif len(txt[f].strip()) < 500: w('  TOO SMALL %s (%d bytes)' % (f, len(txt[f])))
w('  README.md o D:\\task: %s' % ('CO' if os.path.exists('README.md') else 'CHUA CO'))

w()
w('== 2. Placeholder -xx con sot (ky hieu khong hop le)')
for f in FILES:
    if not txt[f]: continue
    for i, l in enumerate(txt[f].split('\n'), 1):
        for m in re.finditer(r'\b[A-Z]{1,4}-xx\b', l):
            w('  %s:L%d %s' % (f, i, m.group(0)))

w()
w('== 3. ID ngoai dai hop le (loai tru TC-*/FX-* de khoi nham)')
RANGES = [('AUD',60),('DEC',54),('MOD',15),('DEP',10),('ENF',7),('I',23),('MIG',12),('EP',231),
          ('TK',14),('CM',5),('FL',9),('F',10),('A',20),('W',16),('P',23),('N',3),('AI',5),('R',6),('S',8),
          ('T',12),('ST',5),('PJ',10),('WS',4),('RACE',15),('REC',8),('PR',13),('NFR',24),('ARCH',10)]
for fam, mx in RANGES:
    pat = r'(?<!TC-)(?<!FX-)(?<![\w-])%s-(\d{1,3})\b' % fam
    for f in FILES:
        if not txt[f]: continue
        for i, l in enumerate(txt[f].split('\n'), 1):
            for m in re.finditer(pat, l):
                n = int(m.group(1))
                if n > mx or n == 0:
                    w('  %s:L%d %s-%02d (max hop le %d) :: %s' % (f, i, fam, n, mx, l.strip()[:90]))
                    break

w()
w('== 4. Ky hieu cu/da khai tu con xuat hien')
STALE = [r'\bTK-1[56]\b', r'\bT-1[3-9]\b', r'\bT-16\b', r'TK-01\.\.TK-16', r'\bR-07\b(?!\s*\|)', r'\bS-09\b']
for pat in STALE:
    hits = []
    for f in FILES:
        if not txt[f]: continue
        for i, l in enumerate(txt[f].split('\n'), 1):
            if re.search(pat, l): hits.append('%s:L%d' % (f, i))
    w('  %-18s -> %d hit %s' % (pat, len(hits), ', '.join(hits[:8])))

w()
w('== 5. Dau hieu noi dung chua viet xong')
for f in FILES:
    if not txt[f]: continue
    pats = [r'\bTODO\b', r'\bTBD\b', r'\bXXX\b', r'\bFIXME\b', r'\.\.\.\s*\|', r'\bchua viet\b', r'CHUA VIET']
    for i, l in enumerate(txt[f].split('\n'), 1):
        for p in pats:
            if re.search(p, l, re.I):
                w('  %s:L%d [%s] %s' % (f, i, p, l.strip()[:100]))
                break

w()
w('== 6. 07-TEST-PLAN: dem dong test va trang thai')
t7 = txt['07-TEST-PLAN.md'] or ''
rows = re.findall(r'^\|\s*(TC-[A-Z0-9]+-[0-9a-z]{2,4}|TC-I\d\d|TC-R\d\d)\s*\|', t7, re.M)
w('  so dong test trong matrix: %d (khai bao o muc 2.1: 276)' % len(rows))
dup = [x for x in set(rows) if rows.count(x) > 1]
w('  ID test trung: %s' % (dup if dup else 'khong'))
w('  so dong "CHUA CHAY": %d' % len(re.findall(r'CHƯA CHẠY', t7)))
bad = [i for i, l in enumerate(t7.split('\n'), 1)
       if re.match(r'^\|\s*TC-', l) and 'CHƯA CHẠY' not in l]
w('  dong test khong co "CHUA CHAY": %s' % (bad[:10] if bad else 'khong'))
w('  dong test co cot EP- (trace endpoint): %d' % len([l for l in t7.split('\n') if re.match(r'^\|\s*TC-', l) and 'EP-' in l]))

w()
w('== 7. 03-API-CONTRACT: dem endpoint va ma loi')
t3 = txt['03-API-CONTRACT.md'] or ''
eps = re.findall(r'^\|\s*(EP-\d{3})\s*\|', t3, re.M)
w('  so dong EP: %d, unique: %d' % (len(eps), len(set(eps))))
nums = sorted(int(e[3:]) for e in set(eps))
gaps = [n for n in range(nums[0], nums[-1] + 1) if n not in nums]
w('  khoang trong EP: %s' % (gaps if gaps else 'khong'))

w()
w('== 8. File tam / scratch can don')
for p in sorted(glob.glob('_*.py') + glob.glob(os.path.join(D, '_*.py')) + glob.glob(os.path.join(D, '_*.md'))):
    w('  %s (%d B)' % (p, os.path.getsize(p)))
if os.path.isdir('_recovery'):
    w('  _recovery/ (%d file)' % len(os.listdir('_recovery')))

w()
w('== 9. Cac diem da biet con chua sua (kiem tra lai bang chuoi)')
checks = [
    ('07 L9 van noi 03/06 "chua duoc dung lam dich trace"', '07-TEST-PLAN.md', r'chưa được dùng làm đích trace'),
    ('07 §8.2 van noi "Khong file nao khac bi sua"', '07-TEST-PLAN.md', r'Không có file nào khác trong'),
    ('07 §8.2 chua co dong X-10', '07-TEST-PLAN.md', r'\*\*X-10\*\*'),
    ('07 TC-W-025 con dung workspace SUSPENDED (khong ton tai)', '07-TEST-PLAN.md', r'ACTIVE→SUSPENDED'),
    ('07 con 5 cho cite WS-04 sai (owner transfer = W-14)', '07-TEST-PLAN.md', r'WS-04; AUD-28|theo WS-04|WS-04 \|'),
    ('03 EP-037 cite (WS-04) sai, dung la WS-01', '03-API-CONTRACT.md', r'ARCHIVED \(WS-04\)'),
    ('08 Part A con ghi TK-01..TK-16', '08-DECISION-REGISTER.md', r'TK-01\.\.TK-16'),
    ('00 chua co AUD-60', '00-AUDIT-SPEC.md', r'AUD-60'),
    ('07 da co muc 2.7 bang tra cuu ID', '07-TEST-PLAN.md', r'### 2\.7 Bảng tra cứu toàn cục'),
]
for label, f, pat in checks:
    hit = bool(re.search(pat, txt[f] or ''))
    w('  [%s] %s' % ('CON' if hit else 'OK ', label))

sys.stdout.write('\n'.join(out) + '\n')
