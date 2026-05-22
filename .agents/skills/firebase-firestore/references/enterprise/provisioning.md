# Provisioning Firestore Enterprise Native Mode

## Manual Initialization

Initialize the following firebase configuration files manually. Do not use `npx
-y firebase-tools@latest init`, as it expects interactive inputs.

1.  **Create a Firestore Enterprise Database**: Create a Firestore Enterprise
    database using the Firebase CLI.
2.  **Create `firebase.json`**: This file contains database configuration for
    the Firebase CLI.
3.  **Create `firestore.rules`**: This file contains your security rules.
4.  **Create `firestore.indexes.json`**: This file contains your index
    definitions.

### 1. Create a Firestore Enterprise Database

If the user needs to create a new database, ask the user what location to use.
Run `npx -y firebase-tools@latest firestore:locations` to get the list of options.
Suggest colocating with other resources if applicable.

Use the following command to create a Firestore Enterprise database:

```bash
firebase firestore:databases:create my-database-id \
  --location="<selected-location>" \
  --edition="enterprise" \
  --firestore-data-access="ENABLED" \
  --mongodb-compatible-data-access="DISABLED"
```

This will create an enterprise database in the selected location with native mode enabled. A
database id is required to create an enterprise database and the database id
must not be `(default)`. To enable realtime-updates feature, use
`--realtime-updates` flag.

```bash
firebase firestore:databases:create my-database-id \
  --location="<selected-location>" \
  --edition="enterprise" \
  --firestore-data-access="ENABLED" \
  --mongodb-compatible-data-access="DISABLED" \
  --realtime-updates="ENABLED"
```

### 2. Create `firebase.json`

Create a file named `firebase.json` in your project root with the following
content (edit `database` and `location` to match the ones you created above). If this file already exists, instead append to the existing JSON:

```json
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json",
    "edition": "enterprise",
    "database": "my-database-id",
    "location": "<selected-location>"
  }
}
```

### 2. Create `firestore.rules`

Create a file named `firestore.rules`. A good starting point (locking down the
database) is:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

*See [security_rules.md](security_rules.md) for how to write actual rules.*

### 3. Create `firestore.indexes.json`

Create a file named `firestore.indexes.json` with an empty configuration to
start:

```json
{
  "indexes": [],
  "fieldOverrides": []
}
```

*See [indexes.md](indexes.md) for how to configure indexes.*

## Deploy rules and indexes

```bash
# To deploy all rules and indexes
firebase deploy --only firestore

# To deploy just rules
firebase deploy --only firestore:rules

# To deploy just indexes
firebase deploy --only firestore:indexes
```

## Local Emulation

To run Firestore locally for development and testing:

```bash
firebase emulators:start --only firestore
```

This starts the Firestore emulator, typically on port 8080. You can interact
with it using the Emulator UI (usually at http://localhost:4000/firestore).
