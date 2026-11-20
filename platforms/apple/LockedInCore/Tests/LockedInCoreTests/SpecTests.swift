import XCTest
@testable import LockedInCore

/// Runs the shared spec (shared/spec/vectors.json) against the Swift implementation.
final class SpecTests: XCTestCase {

    private func loadVectors() throws -> [String: Any] {
        // This file sits six levels below the repo root: Tests/LockedInCoreTests -> ... -> android/.
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { url.deleteLastPathComponent() }
        url.appendPathComponent("shared/spec/vectors.json")
        let data = try Data(contentsOf: url)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private func int64(_ value: Any?) -> Int64? {
        (value as? NSNumber)?.int64Value
    }

    func testPresetsMatchSpec() throws {
        let root = try loadVectors()
        for spec in try XCTUnwrap(root["presets"] as? [[String: Any]]) {
            let id = try XCTUnwrap(spec["id"] as? String)
            let preset = Preset.fromId(id)
            XCTAssertEqual(preset.id.rawValue, id)
            XCTAssertEqual(preset.minutes, spec["minutes"].flatMap { $0 is NSNull ? nil : int64($0).map(Int.init) })
            XCTAssertEqual(preset.requiresPlus, spec["requiresPlus"] as? Bool, "requiresPlus for \(id)")
        }
        XCTAssertEqual(Preset.fromId(nil).id.rawValue, root["fallbackPresetId"] as? String)
    }

    func testEndTimesMatchSpec() throws {
        let root = try loadVectors()
        for c in try XCTUnwrap(root["endAt"] as? [[String: Any]]) {
            let got = SessionClock.endAt(
                Preset.fromId(c["preset"] as? String),
                startMs: try XCTUnwrap(int64(c["startMs"])),
                localOffsetMs: try XCTUnwrap(int64(c["offsetMs"]))
            )
            XCTAssertEqual(got, int64(c["endMs"]), "endAt \(c["preset"] ?? "?") start=\(c["startMs"] ?? "?")")
        }
    }

    func testIntentionMatchesSpec() throws {
        let root = try loadVectors()
        for c in try XCTUnwrap(root["intention"] as? [[String: Any]]) {
            XCTAssertEqual(FocusText.normaliseIntention(try XCTUnwrap(c["raw"] as? String)), c["normalised"] as? String)
        }
    }

    func testCountdownMatchesSpec() throws {
        let root = try loadVectors()
        for c in try XCTUnwrap(root["countdown"] as? [[String: Any]]) {
            XCTAssertEqual(FocusText.formatCountdown(try XCTUnwrap(int64(c["ms"]))), c["text"] as? String)
        }
    }

    func testEntitlementGatingMatchesSpec() throws {
        let root = try loadVectors()
        for c in try XCTUnwrap(root["canStart"] as? [[String: Any]]) {
            let until = int64(c["plusUntilMs"])
            let got = EntitlementRules.canStart(
                Preset.fromId(c["preset"] as? String),
                plusUntilMs: until,
                nowMs: try XCTUnwrap(int64(c["nowMs"]))
            )
            XCTAssertEqual(got, c["expected"] as? Bool)
        }
    }

    func testConflictResolutionMatchesSpec() throws {
        let root = try loadVectors()
        for c in try XCTUnwrap(root["conflict"] as? [[String: Any]]) {
            let l = try XCTUnwrap(c["local"] as? [Any])
            let r = try XCTUnwrap(c["remote"] as? [Any])
            let got = ConflictResolver.resolve(
                local: (try XCTUnwrap(int64(l[0])), try XCTUnwrap(l[1] as? String)),
                remote: (try XCTUnwrap(int64(r[0])), try XCTUnwrap(r[1] as? String))
            )
            XCTAssertEqual(got.rawValue, c["winner"] as? String)
        }
    }

    func testAiRequestValidationMatchesSpec() throws {
        let root = try loadVectors()
        for c in try XCTUnwrap(root["aiRequest"] as? [[String: Any]]) {
            let reason = AiRules.validate(
                task: try XCTUnwrap(c["task"] as? String),
                context: try XCTUnwrap(c["context"] as? String),
                question: c["question"] as? String
            )
            XCTAssertEqual(reason == nil, c["ok"] as? Bool)
        }
    }

    func testAiResponseMappingMatchesSpec() throws {
        let root = try loadVectors()
        for c in try XCTUnwrap(root["aiResponse"] as? [[String: Any]]) {
            let got = AiResponse.decode(
                status: Int(try XCTUnwrap(int64(c["status"]))),
                body: try XCTUnwrap(c["body"] as? String)
            )
            switch (got, c["kind"] as? String) {
            case (.success(let text), "success"): XCTAssertEqual(text, c["text"] as? String)
            case (.failed, "failed"): break
            case (.quota, "quota"): break
            case (.unavailable(let reason), "unavailable"): XCTAssertEqual(reason, c["reason"] as? String)
            case (.rejected(let message), "rejected"): XCTAssertEqual(message, c["message"] as? String)
            default: XCTFail("status \(c["status"] ?? "?") mapped to \(got), spec wants \(c["kind"] ?? "?")")
            }
        }
    }

    func testAllowlistMatchesSpec() throws {
        let root = try loadVectors()
        for c in try XCTUnwrap(root["allow"] as? [[String: Any]]) {
            let got = AllowRules.isAllowed(
                packageName: try XCTUnwrap(c["pkg"] as? String),
                defaultDialer: c["defaultDialer"] as? String,
                permittedByUser: try XCTUnwrap(c["permitted"] as? Bool),
                isSystemApp: try XCTUnwrap(c["systemApp"] as? Bool)
            )
            XCTAssertEqual(got, c["expected"] as? Bool, c["pkg"] as? String ?? "")
        }
    }
}
