// LockedIn shared rules for web and desktop shells (Windows, macOS). Mirrors the Kotlin core in
// shared/src/main/kotlin and must agree with shared/spec/vectors.json. No platform APIs here:
// native enforcement (blocking apps, call handling) stays in each platform's own code.

export type PresetId = "2" | "5" | "25" | "50" | "90" | "3h" | "morning";

export interface PresetInfo {
  id: PresetId;
  minutes: number | null;
  requiresPlus: boolean;
}

const MINUTE_MS = 60_000;
const DAY_MS = 24 * 60 * MINUTE_MS;
export const FREE_MAX_MINUTES = 25;
export const MORNING_HOUR = 7;
export const FALLBACK_PRESET: PresetId = "50";
export const MAX_INTENTION_CHARS = 80;
export const DEFAULT_INTENTION = "Do it.";

export const PRESETS: Record<PresetId, PresetInfo> = {
  "2": { id: "2", minutes: 2, requiresPlus: false },
  "5": { id: "5", minutes: 5, requiresPlus: false },
  "25": { id: "25", minutes: 25, requiresPlus: false },
  "50": { id: "50", minutes: 50, requiresPlus: true },
  "90": { id: "90", minutes: 90, requiresPlus: true },
  "3h": { id: "3h", minutes: 180, requiresPlus: true },
  morning: { id: "morning", minutes: null, requiresPlus: true },
};

export function presetFromId(id: string | null | undefined): PresetInfo {
  return (id !== null && id !== undefined && id in PRESETS ? PRESETS[id as PresetId] : PRESETS[FALLBACK_PRESET]);
}

function floorDiv(a: number, b: number): number {
  return Math.floor(a / b);
}

/** When a session started at startMs ends. localOffsetMs is the device's offset from UTC. */
export function endAt(preset: PresetInfo, startMs: number, localOffsetMs = 0): number {
  if (preset.minutes !== null) return startMs + preset.minutes * MINUTE_MS;
  const localNow = startMs + localOffsetMs;
  const localDayStart = floorDiv(localNow, DAY_MS) * DAY_MS;
  let target = localDayStart + MORNING_HOUR * 60 * MINUTE_MS;
  if (target <= localNow) target += DAY_MS;
  return target - localOffsetMs;
}

export function normaliseIntention(raw: string): string {
  const trimmed = raw.trim().slice(0, MAX_INTENTION_CHARS);
  return trimmed.trim().length === 0 ? DEFAULT_INTENTION : trimmed;
}

export function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(Math.floor(total / 3600))}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`;
}

export function canStart(preset: PresetInfo, plusUntilMs: number | null, nowMs: number): boolean {
  return !preset.requiresPlus || (plusUntilMs !== null && plusUntilMs > nowMs);
}

export type Winner = "LOCAL" | "REMOTE";

/** Last writer wins; equal timestamps are broken by device id, so every device agrees. */
export function resolveConflict(
  local: { updatedAtMs: number; deviceId: string },
  remote: { updatedAtMs: number; deviceId: string },
): Winner {
  if (local.updatedAtMs !== remote.updatedAtMs) {
    return local.updatedAtMs > remote.updatedAtMs ? "LOCAL" : "REMOTE";
  }
  return local.deviceId >= remote.deviceId ? "LOCAL" : "REMOTE";
}

export const AI_MAX_CONTEXT_CHARS = 12_000;
export const AI_MAX_QUESTION_CHARS = 1_000;

/** Returns a reason when the request must not be sent, or null when it is valid. */
export function validateAiRequest(req: { task: string; context: string; question: string | null }): string | null {
  if (req.context.length > AI_MAX_CONTEXT_CHARS) return "context too long";
  if (req.question !== null && req.question.length > AI_MAX_QUESTION_CHARS) return "question too long";
  if (req.task === "ASK" && (req.question === null || req.question.trim().length === 0)) return "question required";
  if (req.task !== "ASK" && req.context.trim().length === 0) return "material required";
  return null;
}

export type AiResponse =
  | { kind: "success"; text: string }
  | { kind: "failed" }
  | { kind: "quota" }
  | { kind: "unavailable"; reason: "NOT_SIGNED_IN" | "SERVICE_DOWN" }
  | { kind: "rejected"; message: string };

/** Maps a function response to a result. Malformed bodies are "failed", never thrown. */
export function decodeAiResponse(status: number, body: string): AiResponse {
  let fields: Record<string, unknown> = {};
  try {
    const parsed: unknown = JSON.parse(body);
    if (typeof parsed === "object" && parsed !== null) fields = parsed as Record<string, unknown>;
  } catch {
    fields = {};
  }
  switch (status) {
    case 200: {
      const text = typeof fields.text === "string" ? fields.text.trim() : "";
      return text.length === 0 ? { kind: "failed" } : { kind: "success", text };
    }
    case 400:
      return { kind: "rejected", message: typeof fields.message === "string" ? fields.message : "That request could not be sent." };
    case 401:
      return { kind: "unavailable", reason: "NOT_SIGNED_IN" };
    case 429:
      return { kind: "quota" };
    case 503:
      return { kind: "unavailable", reason: "SERVICE_DOWN" };
    default:
      return { kind: "failed" };
  }
}

export interface AllowInput {
  packageName: string;
  defaultDialer: string | null;
  permittedByUser: boolean;
  isSystemApp: boolean;
}

const ALWAYS_ALLOWED = new Set([
  "app.lockedin.phone",
  "com.android.systemui",
  "com.android.phone",
  "com.android.server.telecom",
]);
const KNOWN_CALL_PACKAGES = new Set([
  "com.android.dialer",
  "com.google.android.dialer",
  "com.android.incallui",
  "com.google.android.incallui",
  "com.android.emergency",
]);
const BLOCKED_NAME_PARTS = ["photo", "gallery", "album"];
const SYSTEM_CALL_NAME_PARTS = ["dialer", "incall", "telecom", "emergency"];

/** Android-specific allowlist rule, expressed as a pure function. Same order as AllowRules.kt. */
export function isAllowed(input: AllowInput): boolean {
  const { packageName, defaultDialer, permittedByUser, isSystemApp } = input;
  if (ALWAYS_ALLOWED.has(packageName)) return true;
  const name = packageName.toLowerCase();
  if (BLOCKED_NAME_PARTS.some((p) => name.includes(p))) return false;
  if (permittedByUser) return true;
  if (defaultDialer !== null && packageName === defaultDialer) return true;
  if (KNOWN_CALL_PACKAGES.has(packageName)) return true;
  return isSystemApp && SYSTEM_CALL_NAME_PARTS.some((p) => name.includes(p));
}
