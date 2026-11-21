import Foundation

// MARK: - Sessions and presets

public enum PresetId: String, CaseIterable {
    case m2 = "2", m5 = "5", m25 = "25", m50 = "50", m90 = "90", h3 = "3h", morning = "morning"
}

public struct Preset: Equatable {
    public let id: PresetId
    public let minutes: Int?

    /// Longer locks and until-morning need Plus. Free access is capped at 25 minutes.
    public var requiresPlus: Bool { (minutes ?? 26) > 25 }

    public static let all: [Preset] = [
        Preset(id: .m2, minutes: 2), Preset(id: .m5, minutes: 5), Preset(id: .m25, minutes: 25),
        Preset(id: .m50, minutes: 50), Preset(id: .m90, minutes: 90), Preset(id: .h3, minutes: 180),
        Preset(id: .morning, minutes: nil),
    ]

    public static let fallback = Preset(id: .m50, minutes: 50)

    public static func fromId(_ id: String?) -> Preset {
        guard let id = id, let presetId = PresetId(rawValue: id),
              let preset = all.first(where: { $0.id == presetId }) else { return fallback }
        return preset
    }
}

public enum SessionClock {
    static let minuteMs: Int64 = 60_000
    static let dayMs: Int64 = 24 * 60 * minuteMs
    static let morningHour: Int64 = 7

    /// When a session started at `startMs` ends. `localOffsetMs` is the device's offset from UTC.
    public static func endAt(_ preset: Preset, startMs: Int64, localOffsetMs: Int64 = 0) -> Int64 {
        if let minutes = preset.minutes {
            return startMs + Int64(minutes) * minuteMs
        }
        let localNow = startMs + localOffsetMs
        let localDayStart = floorDiv(localNow, dayMs) * dayMs
        var target = localDayStart + morningHour * 60 * minuteMs
        if target <= localNow { target += dayMs }
        return target - localOffsetMs
    }

    /// Floor division for positive divisors, correct for negative numerators.
    static func floorDiv(_ a: Int64, _ b: Int64) -> Int64 {
        let q = a / b
        return (a % b != 0 && (a < 0) != (b < 0)) ? q - 1 : q
    }
}

public enum FocusText {
    public static let maxIntentionChars = 80
    public static let defaultIntention = "Do it."

    public static func normaliseIntention(_ raw: String) -> String {
        let trimmed = String(raw.trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxIntentionChars))
        return trimmed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? defaultIntention : trimmed
    }

    /// Countdown as HH:MM:SS, the same format as the Android lock screen.
    public static func formatCountdown(_ ms: Int64) -> String {
        let total = max(0, ms / 1000)
        func pad(_ n: Int64) -> String { n < 10 ? "0\(n)" : "\(n)" }
        return "\(pad(total / 3600)):\(pad((total % 3600) / 60)):\(pad(total % 60))"
    }
}

// MARK: - Entitlements and sync

public enum EntitlementRules {
    public static func canStart(_ preset: Preset, plusUntilMs: Int64?, nowMs: Int64) -> Bool {
        !preset.requiresPlus || (plusUntilMs.map { $0 > nowMs } ?? false)
    }
}

public enum Winner: String { case local = "LOCAL", remote = "REMOTE" }

public enum ConflictResolver {
    /// Last writer wins; equal timestamps are broken by device id, so every device agrees.
    public static func resolve(local: (Int64, String), remote: (Int64, String)) -> Winner {
        if local.0 != remote.0 { return local.0 > remote.0 ? .local : .remote }
        return local.1 >= remote.1 ? .local : .remote
    }
}

// MARK: - AI request and response

public enum AiRules {
    public static let maxContextChars = 12_000
    public static let maxQuestionChars = 1_000

    /// Returns a reason when the request must not be sent, or nil when it is valid.
    public static func validate(task: String, context: String, question: String?) -> String? {
        if context.utf16.count > maxContextChars { return "context too long" }
        if let q = question, q.utf16.count > maxQuestionChars { return "question too long" }
        if task == "ASK", (question ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "question required"
        }
        if task != "ASK", context.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "material required"
        }
        return nil
    }
}

public enum AiResponse: Equatable {
    case success(text: String)
    case failed
    case quota
    case unavailable(reason: String)
    case rejected(message: String)

    /// Maps the Edge Function's status and body. Malformed bodies become .failed, never a crash.
    public static func decode(status: Int, body: String) -> AiResponse {
        let parsed = (try? JSONSerialization.jsonObject(with: Data(body.utf8))) as? [String: Any]
        let fields = parsed ?? [:]
        switch status {
        case 200:
            let text = ((fields["text"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            return text.isEmpty ? .failed : .success(text: text)
        case 400:
            return .rejected(message: (fields["message"] as? String) ?? "That request could not be sent.")
        case 401: return .unavailable(reason: "NOT_SIGNED_IN")
        case 429: return .quota
        case 503: return .unavailable(reason: "SERVICE_DOWN")
        default: return .failed
        }
    }
}

// MARK: - Allowlist (pure rule; the device inputs come from the platform)

public enum AllowRules {
    static let alwaysAllowed: Set<String> = [
        "app.lockedin.phone", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
    ]
    static let knownCallPackages: Set<String> = [
        "com.android.dialer", "com.google.android.dialer", "com.android.incallui",
        "com.google.android.incallui", "com.android.emergency",
    ]
    static let blockedNameParts = ["photo", "gallery", "album"]
    static let systemCallNameParts = ["dialer", "incall", "telecom", "emergency"]

    /// Same decision order as AllowRules.kt on Android.
    public static func isAllowed(packageName: String, defaultDialer: String?, permittedByUser: Bool, isSystemApp: Bool) -> Bool {
        if alwaysAllowed.contains(packageName) { return true }
        let name = packageName.lowercased()
        if blockedNameParts.contains(where: { name.contains($0) }) { return false }
        if permittedByUser { return true }
        if let dialer = defaultDialer, packageName == dialer { return true }
        if knownCallPackages.contains(packageName) { return true }
        return isSystemApp && systemCallNameParts.contains(where: { name.contains($0) })
    }
}
