# -*- coding: utf-8 -*-
"""Dot 2: het placeholder -xx con lai + sua cite sai WS-nn + viet lai TC-W-025 (X-10, X-11)."""
import io, re, sys

P = 'docs/07-TEST-PLAN.md'
lines = io.open(P, encoding='utf-8').read().split('\n')

# ---------- A. 9 placeholder con lai ----------
A = [
    (436, 'ARCH-xx outbox', 'ARCH-04 outbox'),
    (481, 'ARCH-xx isolation', 'ARCH-08 isolation'),
    (485, 'AUD-47; ARCH-xx', 'AUD-47; ARCH-01; ARCH-10'),
    (523, 'MIG-08; MIG-xx rollback', 'MIG-08; MIG-12'),
    (525, 'Nhóm 11; MIG-xx; MIG-10', 'Nhóm 11; MIG-02; MIG-10'),
    (528, '02B §9; ARCH-xx', '02B §9; ARCH-10'),
    (543, 'REC-01..08; NFR-xx', 'REC-01..08; NFR-24'),
    (615, 'RACE-xx; 02A unique', 'RACE-08 (cùng loại: unique index là chốt chặn cuối); 02A unique'),
]

# ---------- B. cite sai WS-nn (X-11) ----------
B = [
    (283, 'Transfer owner U2 → U3 theo WS-04', 'Transfer owner U2 → U3 theo `W-14` (`05 §5` WS-OWNER-TRANSFER)'),
    (283, 'I-03; 02A §9; WS-04; AUD-28', 'I-03; 02A §9; W-14; 05 §5 WS-OWNER-TRANSFER; AUD-28'),
    (314, 'RACE-04; 05 §10; WS-04; AUD-28', 'RACE-04; 05 §10; W-14; W-15; AUD-28'),
    (340, 'Nhóm 02; I-03; WS-01', 'Nhóm 02; I-03; W-01'),
    (342, 'Chuyển Owner workspace theo WS-04', 'Chuyển Owner workspace theo `W-14`'),
    (342, 'Nhóm 02; WS-04; I-03; AUD-28', 'Nhóm 02; W-14; 05 §5 WS-OWNER-TRANSFER; I-03; AUD-28; EP-050'),
    (613, 'Nhóm 04; I-07; I-09; WS-04', 'Nhóm 04; I-07; I-09; W-14'),
]

fail, ok = [], 0
for ln, old, new in A + B:
    i = ln - 1
    if old not in lines[i]:
        fail.append('L%d MISSING %r' % (ln, old[:60])); continue
    lines[i] = lines[i].replace(old, new, 1)
    ok += 1
    if re.search(r'(?<!TC-)(?<!FX-)(?<![\w-])[A-Z]{1,4}-xx\b', lines[i]):
        fail.append('L%d van con -xx' % ln)

# ---------- C. viet lai TC-W-025 (X-10: workspace khong co trang thai SUSPENDED) ----------
NEW = ('| TC-W-025 | Workspace lifecycle `WS-01..WS-04` | `FX-WS-T1` `type=TEAM`, `status=ACTIVE`, '
 'Owner `FX-U2` (WO), Admin `FX-U3`; có Project `FX-PJ-1` ACTIVE và Task dở dang; `FX-WS-P1` là Personal của `FX-U1` | '
 '(1) WO archive **không** kèm reauth (`EP-037`); (2) WO archive kèm reauth + `reason`; (3) WA (`FX-U3`) thử archive; '
 '(4) unarchive (`EP-038`, `WS-02`); (5) archive Personal Workspace `FX-WS-P1`; (6) soft-delete Team khi còn Project chưa '
 '`DELETED` (`EP-039`, `WS-03`); (7) soft-delete sau khi mọi Project đã `DELETED`, rồi restore (`EP-040`, `WS-04`); '
 '(8) gọi transition ngoài danh sách (`DELETED → ARCHIVED`); (9) thử mutation nội dung **và** mutation workflow trên '
 'Project/Task khi Workspace `ARCHIVED` | '
 '(1) **428** `REAUTH_REQUIRED` — `WORKSPACE_ARCHIVE` nằm trong danh sách bắt buộc reauth (`04 §14`); '
 '(2) 200, `status=\'ARCHIVED\'` + `archived_at/by/archive_reason`, `workspace_setting.reminder_paused=true` với '
 '`paused_reason=WORKSPACE_ARCHIVED`, Project con readonly — **được phép dù còn Task dở dang** (Nhóm 02); '
 '(3) 403 — `W-06` chỉ cấp WO; (4) 200, unpause + recompute + **đúng một** digest (`WS-02`, AUD-25), Project đã '
 '`COMPLETED` **không** tự mở lại (`EP-038`); (5) **409** `PERSONAL_WORKSPACE_IMMUTABLE` — Personal không archive/xoá '
 'độc lập (`WS-01`/`WS-03`, Nhóm 02); (6) **409** `WORKSPACE_HAS_ACTIVE_PROJECTS` — kỳ vọng này **TREO** vì `05 §5 WS-03` '
 'mới ở mức "đề xuất, cần xác nhận"; (7) 200, `deleted_at` + `delete_group_id` được ghi; restore **không** hồi sinh '
 'membership (AUD-29) và response liệt kê phần không khôi phục được (`MEMBERSHIP_NOT_RECOVERABLE`); '
 '(8) **409** `WORKFLOW_TRANSITION_NOT_ALLOWED`; (9) bị chặn — phạm vi chặn (chỉ nội dung hay cả workflow) '
 '**TREO:DEC-02**; mọi transition ghi `audit_event` + `workflow_event` + `outbox_event` | I + E2E | '
 'Nhóm 02; `WS-01..WS-04`; `W-06`; `W-07`; `W-14`; `02A §3` CHECK `workspace.status`; `03 §5.3` `EP-037`..`EP-040`; '
 'AUD-25; AUD-29; X-10; TREO:DEC-02 | P1 | CHƯA CHẠY |')
i = 440 - 1
if not lines[i].startswith('| TC-W-025 |'):
    fail.append('L440 khong phai TC-W-025: %s' % lines[i][:60])
else:
    lines[i] = NEW
    ok += 1

io.open(P, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
sys.stdout.write('applied=%d failed=%d\n' % (ok, len(fail)))
for f in fail: sys.stdout.write('  FAIL ' + f + '\n')
rest = [(i + 1, m.group(0)) for i, l in enumerate(lines)
        for m in re.finditer(r'(?<!TC-)(?<!FX-)(?<![\w-])[A-Z]{1,4}-xx\b', l)]
sys.stdout.write('-xx con lai: %d\n' % len(rest))
for ln, tok in rest:
    sys.stdout.write('  L%d %s :: %s\n' % (ln, tok, lines[ln - 1].strip()[:90]))
sus = [(i + 1) for i, l in enumerate(lines) if 'SUSPENDED' in l and 'WORKSPACE' not in l.upper()]
sys.stdout.write('dong con nhac SUSPENDED: %s\n' % sus)
ws = [(i + 1, m.group(0)) for i, l in enumerate(lines) for m in re.finditer(r'WS-0\d', l)]
sys.stdout.write('cite WS-0d con lai: %s\n' % ws)
