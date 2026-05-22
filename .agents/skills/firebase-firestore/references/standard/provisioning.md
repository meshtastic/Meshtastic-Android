# Provisioning Cloud Firestore

## Manual Initialization

Initialize the following firebase configuration files manually. Do not use `npx
-y firebase-tools@latest init`, as it expects interactive inputs.

1.  **Create `firebase.json`**: This file configures the Firebase CLI.
2.  **Create `firestore.rules`**: This file contains your security rules.
3.  **Create `firestore.indexes.json`**: This file contains your index
    definitions.

### 1. Create `firebase.json`

Create a file named `firebase.json` in your project root with the following
content. If this file already exists, instead append to the existing JSON:

```json
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  }
}
```

This will use the default database with the Standard edition. To use a different
database, specify the database ID and location:
1.  Run `npx -y firebase-tools@latest firestore:locations` to get the list of locations.
2.  Ask the user which location to use, suggesting colocation if other parts of the app already have a region selected.

You can check the list of available databases using `npx -y firebase-tools@latest firestore:databases:list`.

If the database does not exist, it will be created when you deploy with the specified configuration:

```json
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json",
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

## Deploy database, rules and indexes

**CRITICAL**: You MUST deploy the firestore configuration for the database to be provisioned in the cloud and for your rules/indexes to take effect. If you don't run this, your database will not exist.
```bash
# To deploy all rules and indexes
npx -y firebase-tools@latest deploy --only firestore

# To deploy just rules
npx -y firebase-tools@latest deploy --only firestore:rules

# To deploy just indexes
npx -y firebase-tools@latest deploy --only firestore:indexes
```

## Local Emulation

To run Firestore locally for development and testing:

```bash
npx -y firebase-tools@latest emulators:start --only firestore
```

This starts the Firestore emulator, typically on port 8080. You can interact
with it using the Emulator UI (usually at http://localhost:4000/firestore).
