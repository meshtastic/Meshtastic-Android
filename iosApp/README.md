# NTsocial MeshLink iOS host

The app's Debug and Release configurations preserve bundle ID `com.ntsocial.meshlink.ios`
and use automatic signing. `Config/Signing.xcconfig` optionally includes the Git-ignored
`Config/Signing.local.xcconfig`; set `NTSOCIAL_DEVELOPMENT_TEAM = D524H699HW` there for
local Xcode builds. CI may pass that build setting explicitly. Keep credentials, private keys,
provisioning profiles, and local signing configuration outside source control.

The MeshLink companion and NTsocial parent (`com.ntsocial.ios`) must use the same organization
Team/App Identifier Prefix. Both require App Group `group.com.ntsocial.gateway` and
Keychain access-group suffix `com.ntsocial.meshlink.gateway`. Source entitlements alone do not
grant access: assign the group to both App IDs, regenerate the profiles, and inspect both Apps'
actual signed entitlements and embedded profiles. Preserve the parent's separate private Keychain
group and its additional Release capabilities in the parent repository.

Only the LiberaNt LLC organization team is supported for product signing. The company App Group
replaces the retired development group as of 2026-09-10 and opens a new shared container. Upgrade both Apps together;
an old-group build cannot exchange Gateway mailbox data with a new-group build. This change does
not migrate the old shared mailbox, change either bundle ID, or change the shared Keychain suffix.

For local builds, use JDK 21 and a valid `ANDROID_HOME`, initialize the existing proto submodule,
and follow the repository bootstrap and validation instructions in `AGENTS.md`. The Xcode build
phase invokes Gradle to build the static `MeshLinkKit` framework.

Before distribution, verify the exported App Store Connect IPA's distribution identity,
provisioning profile, exact bundle ID, shared groups, and `get-task-allow=false`. A successfully
signed archive or local two-App Gateway test does not establish TestFlight installation,
RF receipt, remote parent delivery, or App Store approval.
