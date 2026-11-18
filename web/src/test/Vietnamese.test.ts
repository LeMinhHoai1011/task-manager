import {expect,test} from "vitest";
import {getLocale,t} from "../i18n";
import {formatDate,statusLabel} from "../i18n/labels";

test("lựa chọn ngôn ngữ cũ không làm giao diện trở về tiếng Anh",()=>{
  localStorage.setItem("tm.locale","en");
  expect(getLocale()).toBe("vi");expect(t("auth.login")).toBe("Đăng nhập");
  localStorage.removeItem("tm.locale");
});

test("ngày và trạng thái hệ thống có nhãn tiếng Việt",()=>{
  expect(formatDate("2026-11-18")).toBe("18/11/2026");
  expect(formatDate("không phải ngày")).toBe("Ngày không hợp lệ");
  expect(formatDate(null)).toBe("Chưa đặt");
  expect(statusLabel("PENDING_APPROVAL")).toBe("Chờ duyệt");
});
