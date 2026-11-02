import { chromium } from '../../.tools/browser/node_modules/playwright/index.mjs';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
const base = process.env.E2E_BASE || 'http://127.0.0.1:5174';
const api = 'http://127.0.0.1:8181/api/v1';
const suffix = Date.now().toString(36);
const username = `demo_personal_${suffix}`;
const password = `Demo-${crypto.randomUUID()}!`;
const registered = await fetch(`${api}/auth/register`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, email: `${username}@example.test`, password, display_name: 'Demo Personal Tasks' }) });
assert.equal(registered.status, 200);
const browser = await chromium.launch({ channel: 'msedge', headless: true });
await mkdir('../docs/screenshots/v2', { recursive: true });
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, timezoneId: 'Asia/Bangkok',extraHTTPHeaders:{'X-Forwarded-For':`10.232.${Date.now()%240}.1`} });
  const page = await context.newPage();
  await page.goto(`${base}/login`);
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[type="password"]').fill(password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/(dashboard|tasks)$/);
  await page.goto(`${base}/my-tasks`);
  await page.getByLabel('Tiêu đề công việc', { exact: true }).fill('DEMO — nghiệm thu Personal Tasks');
  await page.getByRole('button', { name: 'Thêm việc', exact: true }).click();
  const card = page.locator('li').filter({ hasText: 'DEMO — nghiệm thu Personal Tasks' });
  await card.waitFor();
  await card.getByRole('button', { name: 'Sửa chi tiết' }).click();
  await page.getByLabel('Ghi chú', { exact: true }).fill('Dữ liệu demo dùng kiểm thử trình duyệt.');
  await page.getByLabel('Hạn chỉ có ngày').check();
  await page.getByLabel('Ngày hết hạn', { exact: true }).fill('2026-11-08');
  await page.screenshot({path:'../docs/screenshots/v2/personal-task-edit.png',fullPage:true});
  await page.getByRole('button', { name: 'Lưu', exact: true }).click();
  await page.getByRole('heading', { name: 'Sửa công việc' }).waitFor({ state: 'hidden' });
  await page.getByLabel('Checklist của công việc').selectOption({ label: 'DEMO — nghiệm thu Personal Tasks' });
  await page.getByLabel('Mục checklist', { exact: true }).fill('DEMO checklist');
  await page.getByRole('button', { name: 'Thêm mục', exact: true }).click();
  await page.waitForFunction(() => ![...document.querySelectorAll('button')].some(b => b.disabled && ['Lưu nhãn'].includes(b.textContent.trim())));
  await page.getByLabel('DEMO checklist', { exact: true }).click();
  await page.waitForFunction(() => ![...document.querySelectorAll('button')].some(b => b.disabled && ['Lưu nhãn'].includes(b.textContent.trim())));
  assert.equal(await page.getByLabel('DEMO checklist', { exact: true }).isChecked(), true);
  await page.getByLabel('Nhãn, cách nhau bằng dấu phẩy').fill('demo, personal');
  await page.getByRole('button', { name: 'Lưu nhãn' }).click();
  await page.waitForFunction(() => ![...document.querySelectorAll('button')].some(b => b.disabled && ['Lưu nhãn'].includes(b.textContent.trim())));
  await page.getByRole('button', { name: 'Lưu nhãn' }).waitFor({ state: 'visible' });
  await page.screenshot({ path: '../docs/screenshots/v2/personal-tasks-desktop.png', fullPage: true });
  await card.locator('input[type="checkbox"]').click();
  await page.waitForFunction(() => ![...document.querySelectorAll('button')].some(b => b.disabled && ['Lưu nhãn'].includes(b.textContent.trim())));
  await page.reload();
  await card.locator('input[type="checkbox"]').waitFor();
  assert.equal(await card.locator('input[type="checkbox"]').isChecked(), true);
  await Promise.all([
    page.waitForResponse(r => r.request().method() === 'DELETE' && r.url().includes('/me/tasks/')),
    card.getByRole('button', { name: 'Chuyển vào thùng rác' }).click(),
  ]);
  await card.waitFor({ state: 'hidden' });
  await Promise.all([
    page.waitForResponse(r => r.url().includes('/me/tasks?') && new URL(r.url()).searchParams.get('deleted') === 'true'),
    page.getByLabel('Thùng rác', { exact: true }).check(),
  ]);
  await card.getByRole('button', { name: 'Khôi phục' }).click();
  await card.waitFor({ state: 'hidden' });
  await page.getByLabel('Thùng rác', { exact: true }).uncheck();
  await card.waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: '../docs/screenshots/v2/personal-tasks-mobile.png', fullPage: true });
  console.log('PASS: login/create/edit/date-only/checklist/labels/complete/reload/delete/restore; demo fixture retained.');
} finally { await browser.close(); }
