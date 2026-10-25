"""Apply the remaining content fixes to the recovered docs (idempotent)."""
import os
import re

D = r"D:/task/_recovery"


def load(name):
    return open(os.path.join(D, name), encoding="utf-8").read()


def save(name, s):
    with open(os.path.join(D, name), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(s)


def sub_once(s, old, new, label, log):
    if new in s and old not in s:
        log.append("SKIP (already applied): " + label)
        return s
    if old not in s:
        log.append("MISS (needle absent): " + label)
        return s
    log.append("OK: " + label)
    return s.replace(old, new, 1)


log = []

# ---------------------------------------------------------------- 00-AUDIT
s = load("00-AUDIT-SPEC.md")

s = sub_once(
    s,
    "| MAJOR | DEC-50 | Định nghĩa participation + special grant | Phase 1 |\n"
    "| MINOR | DEC-17 | Mật khẩu tạm vs mã khôi phục offline | Phase 1 |",
    "| MAJOR | DEC-50 | Định nghĩa participation + special grant | Phase 1 |\n"
    "| MAJOR | DEC-51 | Stream file đang chạy khi quyền bị thu hồi: cắt ngay hay cho hoàn tất | Phase 3 |\n"
    "| MINOR | DEC-17 | Mật khẩu tạm vs mã khôi phục offline | Phase 1 |",
    "00: add DEC-51 row", log)

s = sub_once(
    s,
    "**Tổng cộng: 50 phát hiện audit → 13 BLOCKER, 29 MAJOR, 8 MINOR.**",
    "**Tổng cộng: 50 phát hiện audit (AUD-01..AUD-50) sinh ra 51 quyết định cần xác nhận "
    "(DEC-01..DEC-51): 13 BLOCKER, 29 MAJOR, 9 MINOR.**\n\n"
    "Dòng `DEC-30/31` gộp hai quyết định liên quan (Personal Workspace và giữ email/username) "
    "nên số dòng bảng là 50 trong khi số ID là 51. Nguồn tổng hợp đầy đủ kèm phương án A/B/C "
    "và khuyến nghị nằm ở `08-DECISION-REGISTER.md` Phần C.",
    "00: totals line", log)

s = sub_once(
    s,
    "## E. Bất biến mục 15.2 — khả năng hiện thực hoá bằng schema\n\n| Bất biến (trích đặc tả) |",
    "## E. Bất biến mục 15.2 — khả năng hiện thực hoá bằng schema\n\n"
    "**Chú giải ký hiệu:** `Y` = ép được bằng constraint/index; `Y (một phần)` = constraint giữ "
    "được một nửa điều kiện, phần còn lại phải ép ở service layer; `N` = không thể ép bằng "
    "constraint thuần, bắt buộc transaction + test âm.\n\n| Bất biến (trích đặc tả) |",
    "00: section E legend", log)

s = sub_once(
    s,
    "| N (không thể) bằng constraint thuần |",
    "| N |",
    "00: section E row marker", log)

s = sub_once(
    s,
    "→ **Kết luận audit quan trọng:** 6/13 bất biến **không thể** ép bằng constraint.",
    "→ **Kết luận audit quan trọng:** trong 13 bất biến gốc của đặc tả, chỉ **6** ép được hoàn "
    "toàn bằng constraint, **3** ép được một phần và **4 không thể** ép bằng constraint thuần "
    "(≥1 Manager ACTIVE, cấm tự duyệt, parent completion, dependency không chu trình). Ở mức thiết "
    "kế chi tiết, `02A §9` mở rộng thành 23 bất biến (I-01..I-23): 9 ép hoàn toàn bằng "
    "constraint/index, 3 một phần, 11 không thể bằng constraint thuần (trong đó 16 có trigger giữ "
    "đầy đủ, 2 dùng constraint trigger DEFERRED) và **18/23 vẫn cần transaction/service logic** vì "
    "điều kiện phụ thuộc trạng thái runtime.",
    "00: section E conclusion", log)

s = sub_once(
    s,
    "4. **6/13 bất biến cốt lõi không thể ép bằng DB constraint** →",
    "4. **4/13 bất biến cốt lõi không thể ép bằng DB constraint thuần** (≥1 Manager ACTIVE, cấm tự "
    "duyệt, parent completion, dependency không chu trình); ở mức mô hình chi tiết 23 bất biến thì "
    "**18/23 vẫn cần transaction/service logic** →",
    "00: conclusion G.4", log)

s = s.replace("`02-DATA-MODEL-ERD.md`",
              "`02A-DATA-MODEL-CORE.md` + `02B-DATA-MODEL-PLATFORM.md`")
save("00-AUDIT-SPEC.md", s)

# ---------------------------------------------------------------- 01-MODULES
s = load("01-MODULES-BOUNDARIES.md")
n = s.count("`02-DATA-MODEL-ERD.md`")
s = s.replace("sẽ được chốt trong `02-DATA-MODEL-ERD.md`",
              "sẽ được chốt trong `02A-DATA-MODEL-CORE.md` và `02B-DATA-MODEL-PLATFORM.md`")
s = s.replace("`02-DATA-MODEL-ERD.md`", "`02A-DATA-MODEL-CORE.md`")
log.append("01: ERD filename refs replaced (%d found)" % n)
save("01-MODULES-BOUNDARIES.md", s)

# ---------------------------------------------------------------- 06-PLAN
s = load("06-IMPLEMENTATION-PLAN.md")
s = sub_once(s, "(3) 50 DEC được phân loại", "(3) 51 DEC được phân loại",
             "06: DEC count in PH-0 exit", log)
save("06-IMPLEMENTATION-PLAN.md", s)

# ---------------------------------------------------------------- 08-REGISTER
s = load("08-DECISION-REGISTER.md")
s = sub_once(
    s,
    "| Bất biến dữ liệu I-nn (`02A §9`) | **23** (9 ép được hoàn toàn bằng constraint, 8 cần trigger, **6 không thể**) | 6 bất biến không thể ép bằng DB → bắt buộc test âm |",
    "| Bất biến dữ liệu I-nn (`02A §9`) | **23** (9 ép hoàn toàn bằng constraint/index, 3 một phần, "
    "11 không thể bằng constraint thuần; 16 có trigger đầy đủ, 2 trigger DEFERRED) | "
    "**18/23 vẫn cần transaction/service logic** → bắt buộc test âm |",
    "08: Part E invariant statistics", log)
save("08-DECISION-REGISTER.md", s)

print("\n".join(log))
