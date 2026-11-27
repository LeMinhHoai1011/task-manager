import { createInterface } from "node:readline";

const versions = new Set(["2024-11-05", "2025-03-26", "2025-06-18"]);
const writes = process.env.SMARTTASK_MCP_ENABLE_WRITES === "true";
const uuid = { type: "string", pattern: "^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$" };
const string = maxLength => ({ type: "string", minLength: 1, maxLength });
const integer = (minimum, maximum) => ({ type: "integer", minimum, maximum });
const choice = (...values) => ({ type: "string", enum: values });
const scopes = choice("personal", "project");
const states = choice("TODO", "IN_PROGRESS", "PENDING_APPROVAL", "COMPLETED", "CANCELLED");
const priorities = choice("LOW", "MEDIUM", "HIGH", "URGENT");
const limit = integer(1, 100);
const page = integer(0, 2147483647);

function tool(name, description, properties, required, run, readOnly = true) {
  return {
    definition: {
      name, description,
      inputSchema: { type: "object", properties, required, additionalProperties: false },
      annotations: { readOnlyHint: readOnly, destructiveHint: false },
    },
    run,
  };
}

const tools = [
  tool("smarttask_list_projects", "List visible projects. Pass next_cursor back as cursor to continue.", {
    q: string(200), workspace_id: uuid, cursor: string(2048), limit,
  }, [], args => api("/projects", { query: args })),
  tool("smarttask_list_tasks", "Find personal or project tasks. Personal results use page. Project results use next_cursor.", {
    scope: scopes, q: string(200), project_id: uuid, state: states, priority: priorities,
    overdue: { type: "boolean" }, page, cursor: string(2048), limit,
  }, ["scope"], ({ scope, limit = 20, page, cursor, project_id, ...filters }) => {
    if (scope === "personal") {
      if (cursor !== undefined || project_id !== undefined) throw new Error("Use page for personal tasks.");
      return api("/me/tasks", { query: { ...filters, page, size: limit } });
    }
    if (page !== undefined) throw new Error("Use cursor for project tasks.");
    return api("/tasks", { query: { ...filters, project_id, cursor, limit } });
  }),
  tool("smarttask_get_task", "Read one task. Personal task details include checklist and tags.", {
    scope: scopes, task_id: uuid,
  }, ["scope", "task_id"], ({ scope, task_id }) => api(taskPath(scope) + "/" + task_id)),
  tool("smarttask_get_schedule", "Read visible appointments and task deadlines for a date in the requested timezone.", {
    date: { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" },
    view: choice("day", "week", "month", "unscheduled"), timezone: string(80),
    scope: choice("all", "personal", "project"), project_id: uuid, page, limit,
  }, ["date", "timezone"], ({ project_id, limit = 20, view = "day", ...filters }) =>
    api("/calendar", { query: { ...filters, view, projectId: project_id, size: limit } })),
];

if (writes) {
  tools.push(tool("smarttask_create_task", "Create a task after the user asks to create it. Reuse the same request_id for retries.", {
    scope: scopes, title: string(200), project_id: uuid, request_id: uuid,
  }, ["scope", "title", "request_id"], ({ scope, title, project_id, request_id }) => {
    if ((scope === "project") !== (project_id !== undefined)) {
      throw new Error("Provide project_id only when creating a project task.");
    }
    return api(taskPath(scope), {
      method: "POST", body: { title, project_id }, idempotencyKey: request_id,
    });
  }, false));
}

function taskPath(scope) {
  return scope === "personal" ? "/me/tasks" : "/tasks";
}

function validated(schema, args) {
  if (args === null || typeof args !== "object" || Array.isArray(args)) {
    throw new Error("Tool arguments must be an object.");
  }
  for (const key of schema.required) {
    if (!Object.hasOwn(args, key)) throw new Error(`Missing argument: ${key}.`);
  }
  for (const [key, value] of Object.entries(args)) {
    const rule = Object.hasOwn(schema.properties, key) ? schema.properties[key] : null;
    if (!rule) throw new Error("Unexpected argument.");
    const valid = rule.type === "integer"
      ? Number.isInteger(value) && value >= rule.minimum && value <= rule.maximum
      : rule.type === "boolean"
        ? typeof value === "boolean"
        : typeof value === "string" && value.trim().length > 0
          && (!rule.maxLength || value.length <= rule.maxLength)
          && (!rule.pattern || new RegExp(rule.pattern).test(value))
          && (!rule.enum || rule.enum.includes(value));
    if (!valid) throw new Error(`Invalid argument: ${key}.`);
  }
  return args;
}

async function api(path, { method = "GET", query = {}, body, idempotencyKey } = {}) {
  const token = process.env.SMARTTASK_MCP_TOKEN;
  if (!token || !/^[A-Za-z0-9._~+\/=-]+$/.test(token)) {
    throw new Error("Set SMARTTASK_MCP_TOKEN to a valid SmartTask access token.");
  }
  let base;
  try {
    base = new URL(process.env.SMARTTASK_MCP_API_URL ?? "http://127.0.0.1:8081/api/v1");
  } catch {
    throw new Error("SMARTTASK_MCP_API_URL must be a valid URL.");
  }
  const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(base.hostname);
  if (base.username || base.password || base.search || base.hash
      || !(base.protocol === "https:" || (base.protocol === "http:" && loopback))) {
    throw new Error("Use HTTPS or loopback HTTP for SMARTTASK_MCP_API_URL.");
  }
  const url = new URL(base);
  url.pathname = base.pathname.replace(/\/$/, "") + path;
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) url.searchParams.set(key, String(value));
  }
  const headers = { Accept: "application/json", Authorization: `Bearer ${token}`, "X-Client-Type": "WEB" };
  if (body) headers["Content-Type"] = "application/json";
  if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;

  let response;
  try {
    response = await fetch(url, {
      method, headers, body: body ? JSON.stringify(body) : undefined,
      redirect: "error", signal: AbortSignal.timeout(15000),
    });
  } catch {
    throw new Error(method === "GET" ? "SmartTask is unreachable or timed out."
      : "SmartTask did not confirm the write. Check the result before retrying with the same request_id.");
  }
  if (!response.ok) {
    if (response.status === 401) throw new Error("SmartTask session expired or invalid. Renew SMARTTASK_MCP_TOKEN.");
    if (response.status === 403 || response.status === 404) {
      throw new Error("Resource unavailable or access denied by SmartTask.");
    }
    const error = await response.json().catch(() => null);
    const reason = error?.error?.reason_code;
    const code = typeof reason === "string" && /^[A-Z0-9_]{1,80}$/.test(reason) ? ` · ${reason}` : "";
    throw new Error(`SmartTask HTTP ${response.status}${code}.`);
  }
  const result = await response.json().catch(() => null);
  if (!result || !Object.hasOwn(result, "data")) throw new Error("Unexpected SmartTask API response.");
  return result.data;
}

const reply = (id, result) => process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id, result }) + "\n");
const rpcError = (id, code, message) => process.stdout.write(JSON.stringify({
  jsonrpc: "2.0", id, error: { code, message },
}) + "\n");
const content = text => [{ type: "text", text }];
let negotiated = false;
let ready = false;

for await (const line of createInterface({ input: process.stdin, crlfDelay: Infinity })) {
  if (!line.trim()) continue;
  let request;
  try {
    request = JSON.parse(line);
  } catch {
    rpcError(null, -32700, "Parse error");
    continue;
  }
  if (!request || request.jsonrpc !== "2.0" || typeof request.method !== "string") {
    rpcError(null, -32600, "Invalid request");
    continue;
  }
  const { id, method, params } = request;
  if (id === undefined) {
    if (method === "notifications/initialized" && negotiated) ready = true;
    continue;
  }
  if (method === "initialize" && !negotiated && typeof params?.protocolVersion === "string") {
    negotiated = true;
    reply(id, {
      protocolVersion: versions.has(params.protocolVersion) ? params.protocolVersion : "2025-06-18",
      capabilities: { tools: {} },
      serverInfo: { name: "smarttask", version: "1.0.0" },
    });
  } else if (method === "ping") {
    reply(id, {});
  } else if (!ready) {
    rpcError(id, -32600, "Initialize the MCP session first");
  } else if (method === "tools/list") {
    reply(id, { tools: tools.map(entry => entry.definition) });
  } else if (method === "tools/call") {
    const selected = tools.find(entry => entry.definition.name === params?.name);
    if (!selected) {
      rpcError(id, -32602, "Unknown tool");
      continue;
    }
    try {
      const result = await selected.run(validated(selected.definition.inputSchema,
        params.arguments === undefined ? {} : params.arguments));
      reply(id, { content: content(JSON.stringify(result)) });
    } catch (error) {
      reply(id, { isError: true, content: content(error.message) });
    }
  } else {
    rpcError(id, -32601, "Method not found");
  }
}
