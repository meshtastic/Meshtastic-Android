# Legacy bundled TAK client identity (test fixture only)

`client.p12` (password `meshtastic`) and `ca.pem` are the client identity and CA that every
Meshtastic-Android build before per-install certificates shipped in its APK, and that
Meshtastic-Apple still ships. The private key is therefore public. They exist here solely so
`TAKServerJvmTlsTest` can prove the server rejects them; nothing in production loads this directory.
