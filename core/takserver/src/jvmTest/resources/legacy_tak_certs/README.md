# Earlier-build TAK client identity (test fixture)

`client.p12` (password `meshtastic`) and `ca.pem` are the client identity and CA that builds before
per-install certificates used. `TAKServerJvmTlsTest` checks the server rejects them; nothing in
production loads this directory.
