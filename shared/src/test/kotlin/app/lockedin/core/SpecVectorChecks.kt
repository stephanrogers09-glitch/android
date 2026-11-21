package app.lockedin.core

import app.lockedin.core.entitlement.EntitlementRules
import app.lockedin.core.entitlement.PlusEntitlement
import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiResult
import app.lockedin.core.intelligence.AiRules
import app.lockedin.core.intelligence.AiTask
import app.lockedin.core.intelligence.AiWire
import app.lockedin.core.intelligence.Json
import app.lockedin.core.session.FocusSession
import app.lockedin.core.session.Preset
import app.lockedin.core.session.SessionClock
import app.lockedin.core.sync.ConflictResolver
import app.lockedin.core.sync.Revision
import app.lockedin.phone.AllowRules
import java.io.File

/**
 * Runs the cross-platform spec (shared/spec/vectors.json) against the Kotlin implementation.
 * Kotlin is the reference. The TypeScript and Swift implementations run the same file.
 * Usage: SpecVectorChecksKt <path to vectors.json>
 */

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean, detail: String = "") {
    if (ok) passed++ else { failed++; println("FAIL: $name $detail".trimEnd()) }
}

@Suppress("UNCHECKED_CAST")
private fun obj(v: Any?): Map<String, Any?> = v as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun arr(v: Any?): List<Any?> = v as List<Any?>

private fun long(v: Any?): Long = (v as Number).toLong()

private fun presetOf(id: String?): Preset = Preset.fromId(id)

fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: error("usage: SpecVectorChecksKt <vectors.json>")
    val root = obj(Json.parseObjectOrNull(File(path).readText()) ?: error("vectors.json is not valid JSON"))

    // Presets: id, minutes and the Plus rule.
    for (p in arr(root["presets"])) {
        val spec = obj(p)
        val id = spec["id"] as String
        val preset = Preset.entries.firstOrNull { it.id == id }
        check("preset $id exists", preset != null)
        if (preset == null) continue
        val minutes = spec["minutes"]?.let { long(it).toInt() }
        check("preset $id minutes", preset.minutes == minutes, "got ${preset.minutes}, want $minutes")
        check("preset $id requiresPlus", preset.requiresPlus == (spec["requiresPlus"] as Boolean))
    }
    check("fallback preset", presetOf(null).id == root["fallbackPresetId"] as String)

    // Session end times, including the until-morning rule.
    for (c in arr(root["endAt"])) {
        val s = obj(c)
        val got = SessionClock.endAt(presetOf(s["preset"] as String), long(s["startMs"]), long(s["offsetMs"]))
        check("endAt ${s["preset"]} start=${s["startMs"]}", got == long(s["endMs"]), "got $got want ${s["endMs"]}")
    }

    // Intention normalisation.
    for (c in arr(root["intention"])) {
        val s = obj(c)
        val got = FocusSession.normaliseIntention(s["raw"] as String)
        check("intention", got == s["normalised"], "got '$got'")
    }

    // Countdown text.
    for (c in arr(root["countdown"])) {
        val s = obj(c)
        val got = FocusSession.formatCountdown(long(s["ms"]))
        check("countdown ${s["ms"]}", got == s["text"], "got $got")
    }

    // Entitlement gating.
    for (c in arr(root["canStart"])) {
        val s = obj(c)
        val until = s["plusUntilMs"]?.let { long(it) }
        val plus = until?.let { PlusEntitlement(untilMs = it, verifiedAtMs = 0L) }
        val got = EntitlementRules.canStart(presetOf(s["preset"] as String), plus, long(s["nowMs"]))
        check("canStart ${s["preset"]} until=$until", got == (s["expected"] as Boolean))
    }

    // Conflict resolution.
    for (c in arr(root["conflict"])) {
        val s = obj(c)
        val l = arr(s["local"])
        val r = arr(s["remote"])
        val got = ConflictResolver.resolve(
            Revision(long(l[0]), l[1] as String),
            Revision(long(r[0]), r[1] as String),
        ).name
        check("conflict $l vs $r", got == s["winner"], "got $got")
    }

    // AI request validation.
    for (c in arr(root["aiRequest"])) {
        val s = obj(c)
        val req = AiRequest(
            task = AiTask.valueOf(s["task"] as String),
            context = s["context"] as String,
            question = s["question"] as String?,
        )
        val ok = AiRules.validate(req) == null
        check("aiRequest ${s["task"]} len=${(s["context"] as String).length}", ok == (s["ok"] as Boolean))
    }

    // AI response mapping.
    for (c in arr(root["aiResponse"])) {
        val s = obj(c)
        val result = AiWire.decodeResponse(long(s["status"]).toInt(), s["body"] as String)
        val kind = when (result) {
            is AiResult.Success -> "success"
            is AiResult.Failed -> "failed"
            is AiResult.QuotaExceeded -> "quota"
            is AiResult.Unavailable -> "unavailable"
            is AiResult.Rejected -> "rejected"
        }
        check("aiResponse ${s["status"]} kind", kind == s["kind"], "got $kind")
        when (result) {
            is AiResult.Success -> check("aiResponse text", result.text == s["text"])
            is AiResult.Rejected -> check("aiResponse message", result.message == s["message"])
            is AiResult.Unavailable -> check("aiResponse reason", result.reason.name == s["reason"])
            else -> Unit
        }
    }

    // Allowlist.
    for (c in arr(root["allow"])) {
        val s = obj(c)
        val got = AllowRules.isAllowed(
            packageName = s["pkg"] as String,
            defaultDialer = s["defaultDialer"] as String?,
            permittedByUser = s["permitted"] as Boolean,
            isSystemApp = s["systemApp"] as Boolean,
        )
        check("allow ${s["pkg"]}", got == (s["expected"] as Boolean), "got $got")
    }

    println("spec vector checks (kotlin): $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed spec check(s) failed")
}
