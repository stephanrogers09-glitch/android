// Runs the shared spec (shared/spec/vectors.json) against the compiled TypeScript implementation.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import * as core from "../build/index.js";

const here = dirname(fileURLToPath(import.meta.url));
const vectors = JSON.parse(readFileSync(join(here, "../../../shared/spec/vectors.json"), "utf8"));

test("presets match the spec", () => {
  for (const spec of vectors.presets) {
    const p = core.PRESETS[spec.id];
    assert.ok(p, `preset ${spec.id} exists`);
    assert.equal(p.minutes, spec.minutes, `minutes for ${spec.id}`);
    assert.equal(p.requiresPlus, spec.requiresPlus, `requiresPlus for ${spec.id}`);
  }
  assert.equal(core.presetFromId(null).id, vectors.fallbackPresetId);
});

test("session end times match the spec, including until-morning", () => {
  for (const c of vectors.endAt) {
    const got = core.endAt(core.presetFromId(c.preset), c.startMs, c.offsetMs);
    assert.equal(got, c.endMs, `endAt ${c.preset} start=${c.startMs}`);
  }
});

test("intention normalisation matches the spec", () => {
  for (const c of vectors.intention) {
    assert.equal(core.normaliseIntention(c.raw), c.normalised);
  }
});

test("countdown format matches the spec", () => {
  for (const c of vectors.countdown) {
    assert.equal(core.formatCountdown(c.ms), c.text);
  }
});

test("entitlement gating matches the spec", () => {
  for (const c of vectors.canStart) {
    assert.equal(core.canStart(core.presetFromId(c.preset), c.plusUntilMs, c.nowMs), c.expected);
  }
});

test("conflict resolution matches the spec", () => {
  for (const c of vectors.conflict) {
    const got = core.resolveConflict(
      { updatedAtMs: c.local[0], deviceId: c.local[1] },
      { updatedAtMs: c.remote[0], deviceId: c.remote[1] },
    );
    assert.equal(got, c.winner);
  }
});

test("AI request validation matches the spec", () => {
  for (const c of vectors.aiRequest) {
    const ok = core.validateAiRequest({ task: c.task, context: c.context, question: c.question }) === null;
    assert.equal(ok, c.ok, `${c.task} context=${c.context.length} question=${c.question?.length ?? "null"}`);
  }
});

test("AI response mapping matches the spec", () => {
  for (const c of vectors.aiResponse) {
    const got = core.decodeAiResponse(c.status, c.body);
    assert.equal(got.kind, c.kind, `status ${c.status}`);
    if (c.text !== undefined) assert.equal(got.text, c.text);
    if (c.message !== undefined) assert.equal(got.message, c.message);
    if (c.reason !== undefined) assert.equal(got.reason, c.reason);
  }
});

test("allowlist matches the spec", () => {
  for (const c of vectors.allow) {
    const got = core.isAllowed({
      packageName: c.pkg,
      defaultDialer: c.defaultDialer,
      permittedByUser: c.permitted,
      isSystemApp: c.systemApp,
    });
    assert.equal(got, c.expected, c.pkg);
  }
});
