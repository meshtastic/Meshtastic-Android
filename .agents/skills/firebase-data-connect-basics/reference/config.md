# Configuration Reference

## Contents
- [Project Structure](#project-structure)
- [dataconnect.yaml](#dataconnectyaml)
- [connector.yaml](#connectoryaml)
- [Firebase CLI Commands](#firebase-cli-commands)
- [Emulator](#emulator)
- [Deployment](#deployment)

---

## Project Structure

```
project-root/
├── firebase.json           # Firebase project config
└── dataconnect/
    ├── dataconnect.yaml    # Service configuration
    ├── schema/
    │   └── schema.gql      # Data model (types, relationships)
    └── connector/
        ├── connector.yaml  # Connector config + SDK generation
        ├── queries.gql     # Query operations
        └── mutations.gql   # Mutation operations (optional separate file)
```

---

## dataconnect.yaml

Main SQL Connect service configuration:

```yaml
specVersion: "v1"
serviceId: "my-service"
location: "us-central1"
schemaValidation: "STRICT" # or "COMPATIBLE"
schema:
  source: "./schema"
  datasource:
    postgresql:
      database: "fdcdb"
      cloudSql:
        instanceId: "my-instance"
connectorDirs: ["./connector"]
```

| Field | Description |
|-------|-------------|
| `specVersion` | Always `"v1"` |
| `serviceId` | Unique identifier for the service |
| `location` | GCP region (us-central1, us-east4, europe-west1, etc.) |
| `schemaValidation` | Deployment mode: `"STRICT"` (must match exactly) or `"COMPATIBLE"` (backward compatible) |
| `schema.source` | Path to schema directory |
| `schema.datasource` | PostgreSQL connection config |
| `connectorDirs` | List of connector directories |

### Cloud SQL Configuration

```yaml
schema:
  datasource:
    postgresql:
      database: "my-database"      # Database name
      cloudSql:
        instanceId: "my-instance"  # Cloud SQL instance ID
```

---

## connector.yaml

Connector configuration and SDK generation:

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
```

### SDK Generation Options

| SDK | Fields |
|-----|--------|
| `javascriptSdk` | `outputDir`, `package` |
| `kotlinSdk` | `outputDir`, `package` |
| `swiftSdk` | `outputDir` |
| `nodeAdminSdk` | `outputDir`, `package` (for Admin SDK) |

---

## Firebase CLI Commands

### Initialize SQL Connect

```bash
# Interactive setup
npx -y firebase-tools@latest init dataconnect

# Set project
npx -y firebase-tools@latest use <project-id>
```

### Local Development

```bash
# Start emulator
npx -y firebase-tools@latest emulators:start --only dataconnect

# Start with database seed data
npx -y firebase-tools@latest emulators:start --only dataconnect --import=./seed-data

# Generate SDKs
npx -y firebase-tools@latest dataconnect:sdk:generate

# Watch for schema changes (auto-regenerate)
npx -y firebase-tools@latest dataconnect:sdk:generate --watch
```

### Schema Management

```bash
# Compare local schema to production
npx -y firebase-tools@latest dataconnect:sql:diff


# Apply migration
npx -y firebase-tools@latest dataconnect:sql:migrate
```

### Deployment

```bash
# Deploy SQL Connect service
npx -y firebase-tools@latest deploy --only dataconnect

# Deploy specific connector
npx -y firebase-tools@latest deploy --only dataconnect:connector-id

# Deploy with schema migration
npx -y firebase-tools@latest deploy --only dataconnect --force
```

---

## Emulator

### Start Emulator

```bash
npx -y firebase-tools@latest emulators:start --only dataconnect
```

Default ports:
- SQL Connect: `9399`
- PostgreSQL: `9939` (local PostgreSQL instance)

### Emulator Configuration (firebase.json)

```json
{
  "emulators": {
    "dataconnect": {
      "port": 9399
    }
  }
}
```

### Connect from SDK

```typescript
// Web
import { connectDataConnectEmulator } from 'firebase/data-connect';
connectDataConnectEmulator(dc, 'localhost', 9399);

// Android
connector.dataConnect.useEmulator("10.0.2.2", 9399)

// iOS
connector.useEmulator(host: "localhost", port: 9399)


```

### Seed Data

Create seed data files and import:

```bash
# Export current emulator data
npx -y firebase-tools@latest emulators:export ./seed-data

# Start with seed data
npx -y firebase-tools@latest emulators:start --only dataconnect --import=./seed-data
```

---

## Deployment

### Deploy Workflow

1. **Test locally** with emulator
2. **Generate SQL diff**: `npx -y firebase-tools@latest dataconnect:sql:diff`
3. **Review migration**: Check breaking changes
4. **Deploy**: `npx -y firebase-tools@latest deploy --only dataconnect`

### Schema Migrations

SQL Connect auto-generates PostgreSQL migrations:

```bash
# Preview migration
npx -y firebase-tools@latest dataconnect:sql:diff

# Apply migration (interactive)
npx -y firebase-tools@latest dataconnect:sql:migrate

# Force migration (non-interactive)
npx -y firebase-tools@latest dataconnect:sql:migrate --force
```

### Breaking Changes

Some schema changes require special handling:
- Removing required fields
- Changing field types
- Removing tables

Use `--force` flag to acknowledge breaking changes during deploy.

### CI/CD Integration

```yaml
# GitHub Actions example
- name: Deploy SQL Connect
  run: |
    npx -y firebase-tools@latest deploy --only dataconnect --token ${{ secrets.FIREBASE_TOKEN }} --force
```

---

## VS Code Extension

Install "Firebase SQL Connect" extension for:
- Schema intellisense and validation
- GraphQL operation testing
- Emulator integration
- SDK generation on save

### Extension Settings

```json
{
  "firebase.dataConnect.autoGenerateSdk": true,
  "firebase.dataConnect.emulator.port": 9399
}
```
