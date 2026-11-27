import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:http";
import { createInterface } from "node:readline";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(new URL("./smarttask.mjs", import.meta.url));
const taskId = "12345678-1234-1234-1234-123456789abc";
const requestId = "12345678-1234-1234-1234-abcdef012345";
const request = (id, method, params) => ({ jsonrpc: "2.0", id, method, params });
const call = (id, name, args) => request(id, "tools/call", { name: `smarttask_${name}`, arguments: args });
const data = result => JSON.parse(result.content[0].text);

async function exchange(apiUrl, write, calls) {
  const child = spawn(process.execPath, [script], {
    env: {
      ...process.env, SMARTTASK_MCP_API_URL: apiUrl,
      SMARTTASK_MCP_TOKEN: "fake-test-token", SMARTTASK_MCP_ENABLE_WRITES: String(write),
    },
    stdio: ["pipe", "pipe", "inherit"],
  });
  const exited = once(child, "exit");
  const lines = createInterface({ input: child.stdout });
  const messages = [
    request(1, "initialize", { protocolVersion: "2025-06-18" }),
    { jsonrpc: "2.0", method: "notifications/initialized" },
    request(2, "tools/list"),
    ...calls,
  ];
  child.stdin.end(messages.map(message => JSON.stringify(message)).join("\n") + "\n");
  const replies = new Map();
  for await (const line of lines) {
    const message = JSON.parse(line);
    replies.set(message.id, message.result ?? message.error);
  }
  assert.equal((await exited)[0], 0);
  assert.equal(replies.get(1).protocolVersion, "2025-06-18");
  assert.equal(replies.size, 2 + calls.length);
  return replies;
}

test("stdio tools keep API auth, input validation and opt-in writes", async t => {
  const received = [];
  const server = createServer(async (req, res) => {
    let body = "";
    for await (const chunk of req) body += chunk;
    received.push({ url: req.url, method: req.method, headers: req.headers, body });
    const denied = req.url === `/api/v1/tasks/${taskId}`;
    res.writeHead(denied ? 403 : 200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ data: { id: taskId, title: "Lịch triển khai" } }));
  });
  server.listen(0, "127.0.0.1");
  t.after(() => server.close());
  await once(server, "listening");
  const apiUrl = `http://127.0.0.1:${server.address().port}/api/v1`;
  const read = await exchange(apiUrl, false, [
    call(3, "list_tasks", { scope: "personal", q: "hẹn", page: 1, overdue: false }),
    call(4, "get_task", { scope: "project", task_id: taskId }),
    call(5, "get_task", { scope: "project", task_id: "../accounts" }),
    call(6, "create_task", { scope: "personal", title: "Lịch triển khai", request_id: requestId }),
    call(7, "get_schedule", { date: "2026-11-20", timezone: "Asia/Bangkok", project_id: taskId }),
  ]);
  assert.equal(read.get(2).tools.length, 4);
  assert.deepEqual(data(read.get(3)), { id: taskId, title: "Lịch triển khai" });
  assert.equal(read.get(4).isError, true);
  assert.equal(read.get(5).isError, true);
  assert.equal(read.get(6).code, -32602);
  assert.equal(received.length, 3);
  assert.equal(received[0].url, "/api/v1/me/tasks?q=h%E1%BA%B9n&overdue=false&page=1&size=20");
  assert.equal(new URL(received[2].url, apiUrl).searchParams.get("projectId"), taskId);
  assert.ok(received.every(row => row.headers.authorization === "Bearer fake-test-token"));

  const write = await exchange(apiUrl, true, [
    call(3, "create_task", { scope: "project", title: "Lịch triển khai", project_id: taskId, request_id: requestId }),
  ]);
  assert.equal(write.get(2).tools.at(-1).annotations.readOnlyHint, false);
  assert.equal(data(write.get(3)).id, taskId);
  const created = received.at(-1);
  assert.equal(created.method, "POST");
  assert.equal(created.url, "/api/v1/tasks");
  assert.equal(created.headers["idempotency-key"], requestId);
  assert.deepEqual(JSON.parse(created.body), { title: "Lịch triển khai", project_id: taskId });
});
