// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "xcode_spm_setup",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/tuist/XcodeProj.git", .upToNextMajor(from: "8.27.7")),
    ],
    targets: [
        .executableTarget(
            name: "xcode_spm_setup",
            dependencies: ["XcodeProj"],
            path: "Sources"
        )
    ]
)
