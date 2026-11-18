// Web Worker implementing androidx.sqlite's WebWorkerSQLiteDriver message protocol (open/prepare/step/close) on top
// of @sqlite.org/sqlite-wasm, persisting through the `opfs-sahpool` VFS. That VFS needs no SharedArrayBuffer, so the
// page needs no cross-origin isolation, but only one tab at a time can hold its files. The androidx protocol is
// androidx's public contract (reference: github.com/danysantiago/room-web-demo); the control port below is ours.
//
// Control port: the Kotlin side transfers a MessagePort in `{ controlPort }`. It receives `{ event: "state", state }`
// pushes ("starting" | "busy" | "ready" | "failed") and answers `{ id, cmd, ... }` file requests with `{ id, data }`
// or `{ id, error }`, kept apart from androidx's message ids.
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

// One holder per origin; a second tab waits behind it instead of fighting over the pool's access handles.
const LOCK_NAME = "meshtastic-sqlite";
// A reload starts this worker before the previous page's worker has let go of the lock and its handles.
const RELOAD_GRACE_MS = 3000;
const INSTALL_ATTEMPTS = 10;
const INSTALL_RETRY_MS = 300;
// Room's database and its journal, a staged restore, a restore being validated and an export snapshot.
const MIN_POOL_CAPACITY = 10;
// Must equal DatabaseBackup.STAGED_SUFFIX.
const STAGED_SUFFIX = ".restore";
const MEMORY = ":memory:";

let sqlite3 = null;
let pool = null;
let state = "starting";
let failure = null;
let controlPort = null;

// databaseId -> { db, path }, statementId -> prepared statement.
const databases = new Map();
const statements = new Map();

let nextDatabaseId = 0;
let nextStatementId = 0;

let resolvePoolReady;
let rejectPoolReady;
const poolReady = new Promise((resolve, reject) => {
  resolvePoolReady = resolve;
  rejectPoolReady = reject;
});
// Opens that never run still must not surface as unhandled rejections.
poolReady.catch(() => {});

function setState(next, error) {
  state = next;
  failure = error ? String(error.message || error) : null;
  if (controlPort) controlPort.postMessage({ event: "state", state, error: failure });
}

// sqlite-wasm wants absolute names in the pool; Room hands over a bare file name.
function poolPath(fileName) {
  return fileName.startsWith("/") ? fileName : "/" + fileName;
}

function isOpen(path) {
  for (const entry of databases.values()) if (entry.path === path) return true;
  return false;
}

function poolHas(path) {
  return pool.getFileNames().includes(path);
}

/**
 * Moves the database the old `opfs` VFS kept at the OPFS root into the pool, once. A non-empty hot journal beside it
 * means a write was cut off and the main file alone is torn; nothing is imported then, and the legacy files stay in
 * place so nothing is lost for good.
 */
async function migrateLegacyOpfs(path) {
  if (poolHas(path)) return;
  const segments = path.split("/").filter((s) => s.length > 0);
  const name = segments.pop();
  let dir = await navigator.storage.getDirectory();
  let handle;
  try {
    for (const segment of segments) dir = await dir.getDirectoryHandle(segment);
    handle = await dir.getFileHandle(name);
  } catch (error) {
    if (error.name === "NotFoundError" || error.name === "TypeMismatchError") return;
    throw error;
  }
  let hotJournal = false;
  try {
    const journal = await (await dir.getFileHandle(name + "-journal")).getFile();
    hotJournal = journal.size > 0;
  } catch {
    // No journal.
  }
  if (hotJournal) {
    console.warn(`Kept the legacy ${path} and its journal unimported: the old VFS left a write unfinished`);
    return;
  }
  const bytes = new Uint8Array(await (await handle.getFile()).arrayBuffer());
  if (bytes.byteLength > 0) {
    pool.importDb(path, bytes);
    console.info(`Migrated ${bytes.byteLength} bytes of ${path} from the opfs VFS into opfs-sahpool`);
  }
  for (const legacy of [name, name + "-journal"]) await dir.removeEntry(legacy).catch(() => {});
}

/** Replaces the database at [path] with the restore staged beside it. Runs only while [path] is closed. */
function applyStagedRestore(path) {
  const staged = path + STAGED_SUFFIX;
  if (!poolHas(staged)) return;
  const bytes = pool.exportFile(staged);
  // The replaced database's hot journal would otherwise roll its pages back into the restore.
  pool.unlink(path + "-journal");
  pool.importDb(path, bytes);
  pool.unlink(staged);
  console.info(`Applied the staged restore to ${path}`);
}

async function openDatabase(fileName) {
  if (fileName === MEMORY) return { db: new sqlite3.oo1.DB(MEMORY, "c"), path: MEMORY };
  await poolReady;
  const path = poolPath(fileName);
  if (isOpen(path)) throw new Error(`${path} is already open; opfs-sahpool allows one connection per database`);
  await migrateLegacyOpfs(path);
  applyStagedRestore(path);
  return { db: new pool.OpfsSAHPoolDb(path), path };
}

async function openRequest(id, requestData) {
  try {
    const entry = await openDatabase(requestData.fileName);
    const newDatabaseId = nextDatabaseId++;
    databases.set(newDatabaseId, entry);
    postMessage({ id: id, data: { databaseId: newDatabaseId } });
  } catch (error) {
    postMessage({ id: id, error: error.message });
  }
}

function prepareRequest(id, requestData) {
  try {
    const newStatementId = nextStatementId++;
    const resultData = {
      statementId: newStatementId,
      parameterCount: 0,
      columnNames: [],
    };
    const entry = databases.get(requestData.databaseId);
    if (!entry) {
      postMessage({ id: id, error: "Invalid database ID: " + requestData.databaseId });
      return;
    }
    const statement = entry.db.prepare(requestData.sql);
    statements.set(newStatementId, statement);
    resultData.parameterCount = sqlite3.capi.sqlite3_bind_parameter_count(statement);
    for (let i = 0; i < statement.columnCount; i++) {
      resultData.columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
    }
    postMessage({ id: id, data: resultData });
  } catch (error) {
    postMessage({ id: id, error: error.message });
  }
}

function stepRequest(id, requestData) {
  const statement = statements.get(requestData.statementId);
  if (!statement) {
    postMessage({ id: id, error: "Invalid statement ID: " + requestData.statementId });
    return;
  }
  try {
    const resultData = { rows: [], columnTypes: [] };
    statement.reset();
    statement.clearBindings();
    for (let i = 0; i < requestData.bindings.length; i++) {
      statement.bind(i + 1, requestData.bindings[i]);
    }
    while (statement.step()) {
      if (!resultData.columnTypes.length) {
        for (let i = 0; i < statement.columnCount; i++) {
          resultData.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
        }
      }
      resultData.rows.push(statement.get([]));
    }
    postMessage({ id: id, data: resultData });
  } catch (error) {
    postMessage({ id: id, error: error.message });
  }
}

// The driver does not wait for a close reply, so only failures are posted. Ids start at 0, hence the null checks.
function closeRequest(id, requestData) {
  if (requestData.statementId != null) {
    const statement = statements.get(requestData.statementId);
    if (!statement) {
      postMessage({ id: id, error: "Invalid statement ID: " + requestData.statementId });
      return;
    }
    try {
      statement.finalize();
      statements.delete(requestData.statementId);
    } catch (error) {
      postMessage({ id: id, error: error.message });
    }
  }

  if (requestData.databaseId != null) {
    const entry = databases.get(requestData.databaseId);
    if (!entry) {
      postMessage({ id: id, error: "Invalid database ID: " + requestData.databaseId });
      return;
    }
    try {
      entry.db.close();
      databases.delete(requestData.databaseId);
    } catch (error) {
      postMessage({ id: id, error: error.message });
    }
  }
}

const commandMap = {
  open: openRequest,
  prepare: prepareRequest,
  step: stepRequest,
  close: closeRequest,
};

function handleMessage(e) {
  const requestMsg = e.data;
  if (!Object.hasOwn(requestMsg, "data") && requestMsg.data == null) {
    postMessage({ id: requestMsg.id, error: "Invalid request, missing 'data'." });
    return;
  }
  if (!Object.hasOwn(requestMsg.data, "cmd") && requestMsg.data.cmd == null) {
    postMessage({ id: requestMsg.id, error: "Invalid request, missing 'cmd'." });
    return;
  }
  const command = requestMsg.data.cmd;
  const requestHandler = commandMap[command];
  if (requestHandler) {
    requestHandler(requestMsg.id, requestMsg.data);
  } else {
    postMessage({ id: requestMsg.id, error: "Invalid request, unknown command: '" + command + "'." });
  }
}

// File requests over the control port. Each one acts on closed pool files only.
function requireClosed(path) {
  if (isOpen(path)) throw new Error(`${path} is open`);
}

const controlCommands = {
  async exportFile({ path }) {
    await poolReady;
    return poolHas(path) ? pool.exportFile(path) : null;
  },
  async importFile({ path, bytes }) {
    await poolReady;
    requireClosed(path);
    return pool.importDb(path, bytes);
  },
  async moveFile({ from, to }) {
    await poolReady;
    requireClosed(from);
    requireClosed(to);
    pool.importDb(to, pool.exportFile(from));
    pool.unlink(from);
    return null;
  },
  async inspectDatabase({ path }) {
    await poolReady;
    requireClosed(path);
    const db = new pool.OpfsSAHPoolDb(path);
    try {
      return {
        userVersion: db.selectValue("PRAGMA user_version"),
        integrity: db.selectValue("PRAGMA integrity_check"),
      };
    } finally {
      db.close();
    }
  },
  async deleteFile({ path }) {
    await poolReady;
    requireClosed(path);
    pool.unlink(path + "-journal");
    return pool.unlink(path);
  },
};

async function handleControl(message) {
  const handler = controlCommands[message.cmd];
  try {
    if (!handler) throw new Error("Unknown control command: " + message.cmd);
    const data = await handler(message);
    const transfer = data instanceof Uint8Array ? [data.buffer] : [];
    controlPort.postMessage({ id: message.id, data }, transfer);
  } catch (error) {
    controlPort.postMessage({ id: message.id, error: String(error.message || error) });
  }
}

function attachControlPort(port) {
  controlPort = port;
  controlPort.onmessage = (e) => handleControl(e.data);
  controlPort.postMessage({ event: "state", state, error: failure });
}

/** Resolves true once this worker holds [LOCK_NAME], false when [options] gave up; the lock lives as long as the worker. */
function holdLock(options) {
  return new Promise((resolve, reject) => {
    navigator.locks
      .request(LOCK_NAME, options, (lock) => {
        resolve(lock != null);
        return lock ? new Promise(() => {}) : undefined;
      })
      .catch(reject);
  });
}

async function acquireLock() {
  // Without Web Locks the pool's own access-handle locking is the only guard, and a second tab fails to install.
  if (!navigator.locks) return;
  if (await holdLock({ ifAvailable: true })) return;
  try {
    if (await holdLock({ signal: AbortSignal.timeout(RELOAD_GRACE_MS) })) return;
  } catch (error) {
    if (error.name !== "TimeoutError" && error.name !== "AbortError") throw error;
  }
  setState("busy");
  await holdLock({});
}

async function installPool() {
  for (let attempt = 1; ; attempt++) {
    try {
      // preserveOnInitFailure: a failed install must never delete the pool another context may still be using.
      return await sqlite3.installOpfsSAHPoolVfs({
        preserveOnInitFailure: true,
        forceReinitIfPreviouslyFailed: true,
      });
    } catch (error) {
      if (attempt >= INSTALL_ATTEMPTS) throw error;
      await new Promise((resolve) => setTimeout(resolve, INSTALL_RETRY_MS));
    }
  }
}

async function start() {
  await acquireLock();
  pool = await installPool();
  await pool.reserveMinimumCapacity(MIN_POOL_CAPACITY);
}

// sqlite3InitModule() is async; queue any message that arrives before it resolves.
const messageQueue = [];
onmessage = (e) => {
  if (e.data && e.data.controlPort) {
    attachControlPort(e.data.controlPort);
  } else if (!sqlite3) {
    messageQueue.push(e);
  } else {
    handleMessage(e);
  }
};

sqlite3InitModule()
  .then((instance) => {
    sqlite3 = instance;
    while (messageQueue.length > 0) {
      handleMessage(messageQueue.shift());
    }
    return start();
  })
  .then(
    () => {
      resolvePoolReady();
      setState("ready");
    },
    (error) => {
      console.error("The opfs-sahpool database is unavailable", error);
      rejectPoolReady(error);
      setState("failed", error);
    },
  );
