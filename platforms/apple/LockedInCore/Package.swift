// swift-tools-version:5.9
// LockedIn shared rules for iOS and macOS. Mirrors the Kotlin core and must agree with
// shared/spec/vectors.json. Native enforcement (Family Controls, ManagedSettings, DeviceActivity)
// is NOT here: it needs entitlements that require a paid Apple Developer membership.
import PackageDescription

let package = Package(
    name: "LockedInCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "LockedInCore", targets: ["LockedInCore"]),
    ],
    targets: [
        .target(name: "LockedInCore"),
        .testTarget(name: "LockedInCoreTests", dependencies: ["LockedInCore"]),
    ]
)
