// core:database's OPFS-backed SQLite driver (WebWorkerSQLiteDriver) needs a cross-origin-isolated page — verified
// empirically in this effort's Step 0b spike (danysantiago/room-web-demo), which required serving with these two
// headers for the OPFS path to work at all. `config.devServer` is undefined during the production webpack build
// (wasmJsBrowserDistribution) — guard it, don't set unconditionally, or that build crashes (the exact bug this
// spike found and fixed upstream).
if (config.devServer) {
    config.devServer.headers = {
        ...(config.devServer.headers || {}),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
    };
}
