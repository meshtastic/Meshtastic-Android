// Every web module's wasmJs browser tests (meshtastic.kmp.web). Mocha's 2 s default is shorter than some coroutine
// tests take in Chrome, and a large test bundle keeps the browser silent past Karma's 30 s while it loads.
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
config.browserNoActivityTimeout = 300000;
config.browserDisconnectTimeout = 300000;
