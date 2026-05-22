# Templates

Ready-to-use templates for common Firebase SQL Connect patterns.

---

## Basic CRUD Schema

```graphql
# schema.gql
type Item @table {
  id: UUID! @default(expr: "uuidV4()")
  name: String!
  description: String
  createdAt: Timestamp! @default(expr: "request.time")
  updatedAt: Timestamp! @default(expr: "request.time")
}
```

```graphql
# queries.gql
query ListItems @auth(level: PUBLIC) {
  items(orderBy: [{ createdAt: DESC }]) {
    id name description createdAt
  }
}

query GetItem($id: UUID!) @auth(level: PUBLIC) {
  item(id: $id) { id name description createdAt updatedAt }
}
```

```graphql
# mutations.gql
mutation CreateItem($name: String!, $description: String) @auth(level: USER) {
  item_insert(data: { name: $name, description: $description })
}

mutation UpdateItem($id: UUID!, $name: String, $description: String) @auth(level: USER) {
  item_update(id: $id, data: {
    name: $name,
    description: $description,
    updatedAt_expr: "request.time"
  })
}

mutation DeleteItem($id: UUID!) @auth(level: USER) {
  item_delete(id: $id)
}
```

---

## User-Owned Resources

```graphql
# schema.gql
type User @table(key: "uid") {
  uid: String! @default(expr: "auth.uid")
  email: String! @unique
  displayName: String
}

type Note @table {
  id: UUID! @default(expr: "uuidV4()")
  owner: User!
  title: String!
  content: String
  createdAt: Timestamp! @default(expr: "request.time")
}
```

```graphql
# queries.gql
query MyNotes @auth(level: USER) {
  notes(
    where: { owner: { uid: { eq_expr: "auth.uid" }}},
    orderBy: [{ createdAt: DESC }]
  ) { id title content createdAt }
}

query GetMyNote($id: UUID!) @auth(level: USER) {
  note(
    first: { where: {
      id: { eq: $id },
      owner: { uid: { eq_expr: "auth.uid" }}
    }}
  ) { id title content }
}
```

```graphql
# mutations.gql
mutation CreateNote($title: String!, $content: String) @auth(level: USER) {
  note_insert(data: {
    owner: { uid_expr: "auth.uid" },
    title: $title,
    content: $content
  })
}

mutation UpdateNote($id: UUID!, $title: String, $content: String) @auth(level: USER) {
  note_update(
    first: { where: { id: { eq: $id }, owner: { uid: { eq_expr: "auth.uid" }}}},
    data: { title: $title, content: $content }
  )
}

mutation DeleteNote($id: UUID!) @auth(level: USER) {
  note_delete(
    first: { where: { id: { eq: $id }, owner: { uid: { eq_expr: "auth.uid" }}}}
  )
}
```

---

## Many-to-Many Relationship

```graphql
# schema.gql
type Tag @table {
  id: UUID! @default(expr: "uuidV4()")
  name: String! @unique
}

type Article @table {
  id: UUID! @default(expr: "uuidV4()")
  title: String!
  content: String!
}

type ArticleTag @table(key: ["article", "tag"]) {
  article: Article!
  tag: Tag!
}
```

```graphql
# queries.gql
query ArticlesByTag($tagName: String!) @auth(level: PUBLIC) {
  articles(where: {
    articleTags_on_article: { tag: { name: { eq: $tagName }}}
  }) {
    id title
    tags: tags_via_ArticleTag { name }
  }
}

query ArticleWithTags($id: UUID!) @auth(level: PUBLIC) {
  article(id: $id) {
    id title content
    tags: tags_via_ArticleTag { id name }
  }
}
```

```graphql
# mutations.gql
mutation AddTagToArticle($articleId: UUID!, $tagId: UUID!) @auth(level: USER) {
  articleTag_insert(data: {
    article: { id: $articleId },
    tag: { id: $tagId }
  })
}

mutation RemoveTagFromArticle($articleId: UUID!, $tagId: UUID!) @auth(level: USER) {
  articleTag_delete(key: { articleId: $articleId, tagId: $tagId })
}
```

---

## dataconnect.yaml Template

```yaml
specVersion: "v1"
serviceId: "my-service"
location: "us-central1"
schema:
  source: "./schema"
  datasource:
    postgresql:
      database: "fdcdb"
      cloudSql:
        instanceId: "my-instance"
connectorDirs: ["./connector"]
```

---

## connector.yaml Template

```yaml
connectorId: "default"
generate:
  javascriptSdk:
    outputDir: "../web/src/lib/dataconnect"
    package: "@myapp/dataconnect"
  kotlinSdk:
    outputDir: "../android/app/src/main/kotlin/com/myapp/dataconnect"
    package: "com.myapp.dataconnect"
  swiftSdk:
    outputDir: "../ios/MyApp/DataConnect"
  dartSdk:
    outputDir: "../flutter/lib/dataconnect"
    package: myapp_dataconnect
```

---

## Firebase Init Commands

```bash
# Initialize SQL Connect in project
npx -y firebase-tools@latest init dataconnect

# Initialize with specific project
npx -y firebase-tools@latest use <project-id>
npx -y firebase-tools@latest init dataconnect

# Start emulator for development
npx -y firebase-tools@latest emulators:start --only dataconnect

# Generate SDKs
npx -y firebase-tools@latest dataconnect:sdk:generate

# Deploy to production
npx -y firebase-tools@latest deploy --only dataconnect
```

---

## SDK Initialization (Web)

```typescript
// lib/firebase.ts
import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getDataConnect, connectDataConnectEmulator } from 'firebase/data-connect';
import { connectorConfig } from '@myapp/dataconnect';

const firebaseConfig = {
  apiKey: "...",
  authDomain: "...",
  projectId: "...",
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const dataConnect = getDataConnect(app, connectorConfig);

// Connect to emulator in development
if (import.meta.env.DEV) {
  connectDataConnectEmulator(dataConnect, 'localhost', 9399);
}
```

```typescript
// Example usage
import { listItems, createItem } from '@myapp/dataconnect';

// List items
const { data } = await listItems();
console.log(data.items);

// Create item (requires auth)
await createItem({ name: 'New Item', description: 'Description' });
```

---

## Realtime Query Templates

### Time-Based Polling

```graphql
query LiveDashboard
  @auth(level: PUBLIC)
  @refresh(every: { seconds: 30 }) {
  items(orderBy: [{ updatedAt: DESC }], limit: 20) {
    id name updatedAt
  }
}
```

### Event-Driven Refresh

```graphql
query ItemList($categoryId: UUID!)
  @auth(level: PUBLIC)
  @refresh(onMutationExecuted: {
    operation: "CreateItem",
    condition: "request.variables.categoryId == mutation.variables.categoryId"
  }) {
  items(where: { category: { id: { eq: $categoryId }}}) {
    id name createdAt
  }
}
```

### Client Subscribe (Web)

```typescript
import { liveDashboardRef } from '@myapp/dataconnect';
import { subscribe } from 'firebase/data-connect';

const unsubscribe = subscribe(liveDashboardRef(), {
  onNext: (result) => {
    // Called immediately with current data, then on each refresh
    renderDashboard(result.data.items);
  },
  onError: (error) => console.error('Subscription error:', error)
});

// Cleanup when done
// unsubscribe();
```