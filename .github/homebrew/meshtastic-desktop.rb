# Homebrew Cask template for Meshtastic Desktop.
#
# Source of truth for the cask published to the meshtastic/homebrew-tap tap
# (alongside the meshtasticd formula). The `update-homebrew-cask` job in
# promote.yml substitutes {{VERSION}} and {{SHA256}} and opens a PR against the
# tap on every production release, so its brew test-bot CI validates the bump.
# The download URL depends on the jpackage DMG being named
# "Meshtastic Desktop-<version>.dmg" (GitHub rewrites the space to a dot).
#
# ponytail: arm64-only — CI has no Intel macOS runner, so no x86_64 DMG exists.
# When one is added, switch to `arch arm: ..., intel: ...` with per-arch sha256.
cask "meshtastic-desktop" do
  version "{{VERSION}}"
  sha256 "{{SHA256}}"

  url "https://github.com/meshtastic/Meshtastic-Android/releases/download/v#{version}/Meshtastic.Desktop-#{version}.dmg",
      verified: "github.com/meshtastic/Meshtastic-Android/"
  name "Meshtastic Desktop"
  desc "Companion app for Meshtastic mesh-networking radios"
  homepage "https://meshtastic.org/"

  livecheck do
    url :url
    strategy :github_latest
  end

  auto_updates false
  depends_on arch: :arm64
  depends_on macos: :monterey

  app "Meshtastic Desktop.app"

  zap trash: "~/.meshtastic"
end
