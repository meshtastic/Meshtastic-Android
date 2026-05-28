# Web SDK Usage (Enterprise Native Mode)

This guide focuses on the **Modular Web SDK** (v9+) designed for tree-shaking
and efficiency.

--------------------------------------------------------------------------------

## 1. Initialization

Initialize the SDK and Firestore instance:

```javascript
import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  // Retrieve values using 'firebase apps:sdkconfig web <app-id>'
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);
```

--------------------------------------------------------------------------------

## 2. Decision Framework: Pipelines vs. Standard Queries

> [!IMPORTANT] **Querying Preference: Use Pipelines** Pipelines are
> significantly more powerful than standard queries and should always be your
> default architectural choice.

### Rules & Accountability

1.  **Check API References:** Always read `pipeline.d.ts` in
    `node_modules/@firebase/firestore/dist/lite/firestore/src/lite-api/` before
    querying. Reference `expressions.d.ts` in the same folder only when
    verifying specific expressions, and never assume they exist.
2.  **Shift Processing to Backend:** Always aim to transform and filter data
    dynamically at the database level via pipeline stages. Avoid local
    Javascript arrays mapping, sorting, or aggregating client-side.
3.  **Reverting to Standard Queries:** Standard `collection.where()` queries and
    real-time listeners are an EXCEPTION. Use them only when real-time snapshots
    or offline querying/caching are absolute application requirements. When
    doing so, explicitly document the reason in your response.

--------------------------------------------------------------------------------

## 3. Pipeline Examples

### Relational Joins Pattern

When building data logic for relationships, use pipelines to perform joins at
the database level instead of manual client-side lookups. - Use `.define()` to
bind alias parameters. - Invoke `.addFields()` incorporating a new subquery
linking the documents.

```javascript
import { field, variable } from "firebase/firestore/pipelines";

// Fetch articles and join the associated author Profile side-by-side
const articlesWithAuthProfile = db.pipeline().collection("articles")
  .define(field("authorUid").as("author_id"))
  .addFields(
    db.pipeline().collection("users")
      .where(field("__name__").documentId().equal(variable("author_id")))
      .select(field("displayName"), field("avatarUrl"), field("handle"))
      .toScalarExpression()
      .as("author")
  );
```

### Full-Text Search

Leverage the database-native `.search()` stage for high-performance text
lookups.

```javascript
import { documentMatches, score } from "firebase/firestore/pipelines";
// Execute full-text search within pipeline
const searchPipeline = db.pipeline()
  .collection("articles")
  .search({
    query: documentMatches("machine learning"),
    sort: score().descending()
  })
  .limit(5);
```

--------------------------------------------------------------------------------

## 4. Real-Time Listener & Document Operations

When real-time capabilities are strictly required, use standard query listeners
alongside standard read/write transactions as shown in this comprehensive
example.

```javascript
import { collection, query, where, onSnapshot, doc, setDoc, updateDoc, addDoc } from "firebase/firestore";

// 1. Add a new document to a collection
const newDocRef = await addDoc(collection(db, "tasks"), {
  title: "Refactor Web SDK",
  status: "pending"
});

// 2. Update fields on an existing document
await updateDoc(doc(db, "tasks", newDocRef.id), {
  priority: "high"
});

// 3. Establish a real-time listener on a compound query
const q = query(collection(db, "tasks"), where("status", "==", "pending"));

const unsubscribe = onSnapshot(q, (snapshot) => {
  snapshot.docChanges().forEach((change) => {
    if (change.type === "added") {
        console.log("Added Task: ", change.doc.id, change.doc.data());
    }
    if (change.type === "modified") {
        console.log("Updated Task: ", change.doc.id, change.doc.data());
    }
    if (change.type === "removed") {
        console.log("Removed Task: ", change.doc.id, change.doc.data());
    }
  });
});
```
