// OPFS through WebWorkerSQLiteDriver needs a cross-origin-isolated page. The dev server sends the headers here; a
// production host must send them too. devServer is undefined in the production build, hence the guard.
if (config.devServer) {
    config.devServer.headers = {
        ...(config.devServer.headers || {}),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
    };
}
