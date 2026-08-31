// Web Worker implementing androidx.sqlite's WebWorkerSQLiteDriver message protocol (open/prepare/step/close)
// on top of @sqlite.org/sqlite-wasm, persisting via OPFS (Origin Private File System). The shape of this file
// mirrors the reference implementation validated end-to-end (insert + reload survives) in
// github.com/danysantiago/room-web-demo's sqliteWasmWorker/worker/worker.js — the message protocol itself is
// androidx's public contract, not something this file is free to redesign.
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

let sqlite3 = null;

// databaseId -> sqlite3.oo1.OpfsDb, statementId -> prepared statement.
const databases = new Map();
const statements = new Map();

let nextDatabaseId = 0;
let nextStatementId = 0;

function openRequest(id, requestData) {
  try {
    const newDatabaseId = nextDatabaseId++;
    const newDatabase = new sqlite3.oo1.OpfsDb(requestData.fileName);
    databases.set(newDatabaseId, newDatabase);
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
    const database = databases.get(requestData.databaseId);
    if (!database) {
      postMessage({ id: id, error: "Invalid database ID: " + requestData.databaseId });
      return;
    }
    const statement = database.prepare(requestData.sql);
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

function closeRequest(id, requestData) {
  if (requestData.statementId) {
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

  if (requestData.databaseId) {
    const database = databases.get(requestData.databaseId);
    if (!database) {
      postMessage({ id: id, error: "Invalid database ID: " + requestData.databaseId });
      return;
    }
    try {
      database.close();
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

// sqlite3InitModule() is async; queue any message that arrives before it resolves.
const messageQueue = [];
onmessage = (e) => {
  if (!sqlite3) {
    messageQueue.push(e);
  } else {
    handleMessage(e);
  }
};

sqlite3InitModule().then((instance) => {
  sqlite3 = instance;
  while (messageQueue.length > 0) {
    handleMessage(messageQueue.shift());
  }
});
