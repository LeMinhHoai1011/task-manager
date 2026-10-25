# -*- coding: utf-8 -*-
"""Thay moi ky hieu -xx trong 07-TEST-PLAN.md bang ma cu the (catalog lay tu 04/05/02B/dac ta).
Moi thay the assert: dung dong, old ton tai, sau khi thay khong con -xx tren dong do."""
import io, re, sys

P = '07-TEST-PLAN.md'
lines = io.open(P, encoding='utf-8').read().split('\n')

R = [
    (284, '(P-13/W-xx)', '(P-12 tự gỡ / W-12)'),
    (285, 'hệ quả W-xx', 'hệ quả W-11/W-12'),
    (294, 'PM reopen (T-xx)', 'PM reopen (T-08)'),
    (301, '04 §4 A-xx', '04 §4 A-05/A-06/A-07/A-19'),
    (325, 'MIG-xx partition', 'MIG-07 partition'),
    (344, 'W-xx; 02B', 'W-16; 02B'),
    (351, 'theo PJ-xx', 'theo PJ-05'),
    (357, 'theo precondition T-xx', 'theo precondition T-01'),
    (359, 'theo C-xx/P-xx trong 04',
     'gắn/gỡ tag trên Task theo `TK-05`; CRUD tag cấp Workspace **chưa có mã** trong `04` (`03 §11.1 C-09`) → TREO tới khi `04 §6` bổ sung'),
    (360, 'T-xx; FL-xx', 'TK-11; TK-12'),
    (361, 'NFR-xx timezone', 'TK-06; C-05; DEC-52'),
    (366, 'R-xx export', 'R-05 export + R-06 download'),
    (369, 'ARCH-07; NFR-xx;', 'ARCH-07; NFR-07;'),
    (375, '04 §1 P1; NFR-xx', '04 §1 P1; NFR-02'),
    (378, 'theo phạm vi A-xx', 'theo phạm vi A-09'),
    (387, 'chỉ SA có A-xx', 'chỉ SA có A-19'),
    (390, 'Nhóm 11; NFR-xx; đặc tả 19.2', 'Nhóm 11; NFR-17; đặc tả 19.2'),
    (391, 'Nhóm 11; NFR-xx; ARCH-xx', 'Nhóm 11; NFR-02; ARCH-02'),
    (396, '04 §15; NFR-xx', '04 §15; DEC-43'),
    (404, 'Nhóm 11; ARCH-xx; NFR-xx', 'Nhóm 11; ARCH-02; NFR-17'),
    (410, 'Nhóm 11; NFR-xx; đặc tả 19.3', 'Nhóm 11; NFR-18; đặc tả 19.3'),
    (418, 'state theo T-xx đã chốt', 'state theo T-05'),
    (466, 'theo N-xx', 'theo N-01'),
    (488, 'NFR-xx local-only', 'NFR-08 local-only'),
    (505, 'mục 12; ARCH-xx', 'mục 12; ARCH-03'),
    (508, 'Nhóm 11; NFR-xx; PR-06; ARCH-07', 'Nhóm 11; NFR-07; PR-06; ARCH-07'),
    (509, 'Nhóm 11; NFR-xx; PR-06', 'Nhóm 11; NFR-11; NFR-21; PR-06'),
    (510, 'NFR-21; NFR-xx;', 'NFR-21; NFR-09;'),
    (511, 'Nhóm 11; NFR-xx; ARCH-xx; 02A §10', 'Nhóm 11; NFR-09; ARCH-03; 02A §10'),
    (512, 'RACE-10; NFR-xx', 'RACE-10; NFR-11; ARCH-04; ARCH-05'),
    (513, 'Nhóm 11; NFR-xx; mục 20', 'Nhóm 11; NFR-09; NFR-10; mục 20'),
    (514, 'Nhóm 11; NFR-xx; 02B §8; MIG-xx', 'Nhóm 11; NFR-06; 02B §8; MIG-07'),
    (515, 'Nhóm 11; NFR-xx; MIG-10', 'Nhóm 11; NFR-18; MIG-10'),
    (522, 'Nhóm 11; MIG-xx', 'Nhóm 11; MIG-02'),
    (523, 'rollback theo MIG-xx', 'rollback theo MIG-08'),
    (525, 'đã chốt ở MIG-xx', 'đã chốt ở MIG-02/MIG-03'),
    (538, '02B §8; MIG-xx', '02B §8; MIG-07'),
    (540, 'Nhóm 11; NFR-xx; ARCH-xx', 'Nhóm 11; NFR-17; ARCH-05'),
    (541, 'Nhóm 11; NFR-xx; đặc tả 19.2', 'Nhóm 11; NFR-17; đặc tả 19.2'),
    (542, 'Nhóm 11; NFR-xx; mục 18', 'Nhóm 11; NFR-17; mục 18'),
    (543, 'recovery theo REC-xx', 'recovery theo REC-01'),
    (544, 'Nhóm 11; NFR-xx; ARCH-07; REC-xx', 'Nhóm 11; NFR-24; ARCH-07; REC-01'),
    (545, 'Nhóm 11; NFR-xx; 02B §8', 'Nhóm 11; NFR-17; NFR-23; 02B §8'),
    (554, 'Nhóm 11; NFR-xx; đặc tả 19.2', 'Nhóm 11; NFR-02; NFR-22; đặc tả 19.2'),
    (564, 'Nhóm 01; Nhóm 11; NFR-xx', 'Nhóm 01; Nhóm 11; NFR-02'),
    (566, '`timestamptz`; NFR-xx', '`timestamptz`; DEC-52'),
    (585, 'Nhóm 11; ARCH-xx', 'Nhóm 11; ARCH-02'),
    (587, 'DEC-38; NFR-xx', 'DEC-38; NFR-04; DEC-52'),
    (588, 'Nhóm 11; ARCH-xx; NFR-xx; đặc tả 19.3', 'Nhóm 11; ARCH-02; NFR-04; đặc tả 19.3'),
    (594, 'Nhóm 04; Nhóm 10; NFR-xx', 'Nhóm 04; Nhóm 10; 03 §2.2'),
    (596, '`timestamptz`; NFR-xx', '`timestamptz`; DEC-52'),
    (601, 'Nhóm 11; NFR-xx; 04 §12', 'Nhóm 11; 03 §3.1; 04 §12'),
    (603, 'Nhóm 11; ARCH-xx; NFR-xx', 'Nhóm 11; ARCH-02; 03 §2.2'),
    (605, 'Nhóm 11; ARCH-xx', 'Nhóm 11; ARCH-02; 03 §2.2'),
    (609, '02A UUIDv7; NFR-xx', '02A UUIDv7; NFR-06'),
    (610, 'Nhóm 11; NFR-xx; mục 20', 'Nhóm 11; NFR-09; mục 20'),
    (618, '`timestamptz`; NFR-xx', '`timestamptz`; DEC-52'),
    (1021, 'ArchUnit (DEP-xx)', 'ArchUnit (DEP-01..DEP-10)'),
]

fail, ok = [], 0
for ln, old, new in R:
    i = ln - 1
    if i >= len(lines):
        fail.append('L%d out of range' % ln); continue
    if old not in lines[i]:
        fail.append('L%d MISSING %r' % (ln, old[:60])); continue
    lines[i] = lines[i].replace(old, new, 1)
    ok += 1
    if re.search(r'\b[A-Z]{1,4}-xx\b', lines[i]):
        fail.append('L%d still has -xx' % ln)

io.open(P, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
sys.stdout.write('applied=%d failed=%d\n' % (ok, len(fail)))
for f in fail:
    sys.stdout.write('  FAIL ' + f + '\n')
rest = [(i + 1, m.group(0)) for i, l in enumerate(lines) for m in re.finditer(r'\b[A-Z]{1,4}-xx\b', l)]
sys.stdout.write('remaining -xx tokens: %d\n' % len(rest))
for ln, tok in rest:
    sys.stdout.write('  L%d %s :: %s\n' % (ln, tok, lines[ln - 1].strip()[:120]))
