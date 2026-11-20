// Runs against handler.js compiled from handler.ts by backend/scripts/test-functions.sh.
import { test } from "node:test";
import assert from "node:assert/strict";
import { handleAiRequest, validateBody } from "../build/handler.js";

const ENV = {
  supabaseUrl: "https://project.supabase.test",
  supabaseAnonKey: "anon-key-test",
  supabaseServiceRoleKey: "service-key-test",
  geminiApiKey: "gemini-key-test",
  geminiModel: "gemini-test-model",
};

const USER_ID = "00000000-0000-0000-0000-00000000000a";

// Builds a fake fetch. `handlers` maps a URL predicate to a response factory; every call is recorded.
function fakeNetwork(handlers) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    calls.push({ url, init, body: init.body ? JSON.parse(init.body) : undefined });
    for (const [match, respond] of handlers) {
      if (match(url, init)) return respond(url, init);
    }
    throw new Error(`unexpected call to ${url}`);
  };
  return { fetch, calls };
}

const json = (status, payload) =>
  new Response(JSON.stringify(payload), { status, headers: { "content-type": "application/json" } });

const isUser = (url) => url.endsWith("/auth/v1/user");
const isRpc = (name) => (url) => url.endsWith(`/rest/v1/rpc/${name}`);
const isGemini = (url) => url.startsWith("https://generativelanguage.googleapis.com/");

function standardHandlers({ quota, gemini }) {
  return [
    [isUser, () => json(200, { id: USER_ID })],
    [isRpc("ai_consume_quota"), () => json(200, quota)],
    [isRpc("ai_refund_quota"), () => json(200, null)],
    [isGemini, gemini ?? (() => json(200, { candidates: [{ content: { parts: [{ text: "Answer." }] } }] }))],
  ];
}

const allowed = { allowed: true, remaining: 4, plus: false, max_output_tokens: 512 };

function post(body, { token = "user-jwt" } = {}) {
  const headers = { "content-type": "application/json" };
  if (token) headers.authorization = `Bearer ${token}`;
  return new Request("https://functions.test/ai-assist", {
    method: "POST",
    headers,
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

const run = (req, net) => handleAiRequest(req, { fetch: net.fetch, env: ENV });

test("rejects non-POST methods", async () => {
  const net = fakeNetwork([]);
  const res = await run(new Request("https://functions.test/ai-assist", { method: "GET" }), net);
  assert.equal(res.status, 405);
  assert.equal(net.calls.length, 0);
});

test("requires a bearer token and checks it with Supabase Auth", async () => {
  const net = fakeNetwork([]);
  const res = await run(post({ task: "EXPLAIN", context: "x" }, { token: "" }), net);
  assert.equal(res.status, 401);
  assert.equal(net.calls.length, 0, "no token means no upstream calls at all");

  const rejected = fakeNetwork([[isUser, () => json(401, { msg: "bad jwt" })]]);
  const res2 = await run(post({ task: "EXPLAIN", context: "x" }), rejected);
  assert.equal(res2.status, 401);
  assert.equal(rejected.calls.some((c) => c.url.includes("rpc")), false, "quota is not touched for bad tokens");
});

test("invalid bodies are rejected before any quota is used", async () => {
  const cases = [
    ["not json", "{"],
    ["unknown task", { task: "HACK" }],
    ["context too long", { task: "EXPLAIN", context: "a".repeat(12_001) }],
    ["question too long", { task: "ASK", question: "a".repeat(1_001) }],
    ["ASK without a question", { task: "ASK", question: "   " }],
    ["non-string context", { task: "EXPLAIN", context: 5 }],
  ];
  for (const [label, body] of cases) {
    const net = fakeNetwork(standardHandlers({ quota: allowed }));
    const res = await run(post(body), net);
    assert.equal(res.status, 400, label);
    assert.equal(net.calls.some((c) => c.url.includes("ai_consume_quota")), false, `${label}: no quota charged`);
  }
});

test("validateBody accepts the limits exactly and rejects one over", () => {
  assert.equal(validateBody({ task: "EXPLAIN", context: "a".repeat(12_000) }).ok, true);
  assert.equal(validateBody({ task: "ASK", question: "a".repeat(1_000) }).ok, true);
  assert.equal(validateBody({ task: "EXPLAIN", context: "a".repeat(12_001) }).ok, false);
});

test("a successful request reserves one quota slot and returns the text", async () => {
  const net = fakeNetwork(standardHandlers({ quota: allowed }));
  const res = await run(post({ task: "SUMMARISE", context: "Photosynthesis notes" }), net);
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.text, "Answer.");
  assert.equal(body.remaining, 4);

  const consumes = net.calls.filter((c) => c.url.includes("ai_consume_quota"));
  assert.equal(consumes.length, 1, "exactly one reservation");
  assert.deepEqual(consumes[0].body, { p_user: USER_ID });
  assert.equal(net.calls.some((c) => c.url.includes("ai_refund_quota")), false, "no refund on success");
});

test("Gemini gets the key in a header, the model from config, and the server's output cap", async () => {
  const net = fakeNetwork(standardHandlers({ quota: allowed }));
  await run(post({ task: "QUIZ", context: "Cells" }), net);
  const gemini = net.calls.find((c) => isGemini(c.url));
  assert.equal(
    gemini.url,
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent",
  );
  assert.equal(gemini.init.headers["x-goog-api-key"], "gemini-key-test");
  assert.equal(gemini.body.generationConfig.maxOutputTokens, 512);
  assert.equal(JSON.stringify(gemini.body).includes("gemini-key-test"), false, "key is not in the body");
});

test("the Gemini key and service key never reach the client response", async () => {
  const net = fakeNetwork(standardHandlers({ quota: allowed }));
  const res = await run(post({ task: "EXPLAIN", context: "x" }), net);
  const text = await res.text();
  assert.equal(text.includes("gemini-key-test"), false);
  assert.equal(text.includes("service-key-test"), false);
});

test("a disabled kill switch returns 503 and calls Gemini never", async () => {
  const net = fakeNetwork(standardHandlers({ quota: { allowed: false, reason: "disabled" } }));
  const res = await run(post({ task: "EXPLAIN", context: "x" }), net);
  assert.equal(res.status, 503);
  assert.equal(net.calls.some((c) => isGemini(c.url)), false);
});

test("exhausted quota returns 429 with the reason and calls Gemini never", async () => {
  for (const reason of ["user_limit", "global_limit"]) {
    const net = fakeNetwork(standardHandlers({ quota: { allowed: false, reason } }));
    const res = await run(post({ task: "EXPLAIN", context: "x" }), net);
    assert.equal(res.status, 429, reason);
    assert.equal((await res.json()).reason, reason);
    assert.equal(net.calls.some((c) => isGemini(c.url)), false, `${reason}: no AI call`);
  }
});

for (const [label, gemini] of [
  ["Gemini returns an error status", () => json(500, { error: "boom" })],
  ["Gemini returns no text", () => json(200, { candidates: [] })],
  ["Gemini response has only whitespace", () => json(200, { candidates: [{ content: { parts: [{ text: "  " }] } }] })],
  ["Gemini request throws", () => { throw new Error("network down"); }],
]) {
  test(`refunds the reserved request when ${label}`, async () => {
    const net = fakeNetwork(standardHandlers({ quota: allowed, gemini }));
    const res = await run(post({ task: "EXPLAIN", context: "x" }), net);
    assert.equal(res.status, 502);
    assert.equal((await res.json()).error, "ai_failed");
    const refunds = net.calls.filter((c) => c.url.includes("ai_refund_quota"));
    assert.equal(refunds.length, 1, "exactly one refund");
    assert.deepEqual(refunds[0].body, { p_user: USER_ID });
  });
}

test("a failing refund does not hide the upstream error", async () => {
  const net = fakeNetwork([
    [isUser, () => json(200, { id: USER_ID })],
    [isRpc("ai_consume_quota"), () => json(200, allowed)],
    [isRpc("ai_refund_quota"), () => json(500, {})],
    [isGemini, () => json(500, {})],
  ]);
  const res = await run(post({ task: "EXPLAIN", context: "x" }), net);
  assert.equal(res.status, 502);
});

import { readEnv } from "../build/handler.js";

test("readEnv reports every missing setting by name", () => {
  const result = readEnv(() => undefined);
  assert.equal(result.ok, false);
  assert.deepEqual(result.missing, ["SUPABASE_URL", "LOCKEDIN_PUBLISHABLE_KEY", "LOCKEDIN_SECRET_KEY", "GEMINI_API_KEY"]);
});

test("readEnv prefers LOCKEDIN_* names and falls back to injected legacy names", () => {
  const values = {
    SUPABASE_URL: "https://p.supabase.co",
    SUPABASE_ANON_KEY: "legacy-anon",
    LOCKEDIN_PUBLISHABLE_KEY: "sb_publishable_x",
    SUPABASE_SERVICE_ROLE_KEY: "legacy-service",
    LOCKEDIN_SECRET_KEY: "sb_secret_y",
    GEMINI_API_KEY: "gem",
  };
  const result = readEnv((n) => values[n]);
  assert.equal(result.ok, true);
  assert.equal(result.env.supabaseAnonKey, "sb_publishable_x");
  assert.equal(result.env.supabaseServiceRoleKey, "sb_secret_y");
  assert.equal(result.env.geminiModel, "gemini-2.5-flash");
});

test("readEnv accepts legacy injected keys when the LOCKEDIN_* ones are absent", () => {
  const values = {
    SUPABASE_URL: "https://p.supabase.co",
    SUPABASE_ANON_KEY: "legacy-anon",
    SUPABASE_SERVICE_ROLE_KEY: "legacy-service",
    GEMINI_API_KEY: "gem",
    GEMINI_MODEL: "gemini-test",
  };
  const result = readEnv((n) => values[n]);
  assert.equal(result.ok, true);
  assert.equal(result.env.supabaseAnonKey, "legacy-anon");
  assert.equal(result.env.geminiModel, "gemini-test");
});

test("blank values count as missing", () => {
  const result = readEnv((n) => (n === "GEMINI_API_KEY" ? "   " : "x"));
  assert.equal(result.ok, false);
  assert.deepEqual(result.missing, ["GEMINI_API_KEY"]);
});

test("the secret key is sent only in apikey, never as a bearer token", async () => {
  const net = fakeNetwork(standardHandlers({ quota: allowed }));
  await run(post({ task: "EXPLAIN", context: "x" }), net);
  const rpcs = net.calls.filter((c) => c.url.includes("/rest/v1/rpc/"));
  assert.ok(rpcs.length >= 1, "quota RPC was called");
  for (const c of rpcs) {
    assert.equal(c.init.headers.apikey, "service-key-test");
    assert.equal(c.init.headers.authorization, undefined, `${c.url} must not send a bearer header`);
  }
});
