// Every web module's wasmJs browser tests (meshtastic.kmp.web). Mocha's 2 s default is shorter than some coroutine
// tests take in Chrome, and a large test bundle keeps the browser silent past Karma's 30 s while it loads.
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
config.browserNoActivityTimeout = 300000;
config.browserDisconnectTimeout = 300000;

// A run whose reporting stops never exits, and those timeouts cover only a silent browser. After ten minutes, about
// four times the slowest module, this stops Karma's own children, Chrome among them, and exits non-zero so the Gradle
// task fails with a log. A SIGTERM would close Chrome too but exits 0, which the task reads as a pass with no tests.
// Gradle's own task timeout is the backstop for a Karma that cannot stop; it kills only Node and leaves Chrome running.
const KARMA_RUN_LIMIT_MS = 10 * 60 * 1000;
setTimeout(() => {
    // Karma's own logger, since the Kotlin plugin drops a plain console line from this process. Its path is Karma's
    // internals, so a move there must not stop the cleanup below.
    try {
        require("karma/lib/logger")
            .create("meshtastic")
            .error(`Karma ran past ${KARMA_RUN_LIMIT_MS / 60000} minutes; stopping it and its browser`);
    } catch (e) {
        console.error(e);
    }
    try {
        require("child_process").execFileSync("pkill", ["-TERM", "-P", String(process.pid)]);
    } catch (e) {
        // pkill exits non-zero when no child is left to stop.
    }
    process.exit(1);
}, KARMA_RUN_LIMIT_MS).unref();
