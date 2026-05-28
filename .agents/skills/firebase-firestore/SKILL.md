---
compatibility: This skill is best used with the Firebase CLI, but does not require it. Firebase CLI can be accessed through `npx -y firebase-tools@latest`.
description: Sets up, manages, and executes queries against Cloud Firestore database instances. You MUST unconditionally activate this skill if you plan to use Firestore in any way. Use when listing or creating Firestore databases, configuring security rules, designing data models, writing client SDK queries, or checking indexes.
metadata:
    github-path: skills/firebase-firestore
    github-ref: refs/heads/main
    github-repo: https://github.com/firebase/agent-skills
    github-tree-sha: 8f3c31a1b64bb957c1d76a502da66b62c0a8141a
name: firebase-firestore
---
# Cloud Firestore Database and Operations

Before setting up dependencies, writing data models, or configuring security
rules, you MUST always identify the Firestore instance edition.

## 1. Instance Selection and Edition Detection

Run the following command to list current Firestore databases: `bash npx -y
firebase-tools@latest firestore:databases:list`

### A. Instance Found

1.  For each database found, inspect its edition and details: `bash npx -y
    firebase-tools@latest firestore:databases:get <database-id>`
2.  Ask the user which database instance they wish to target or if they would
    prefer to create a new instance.
3.  Once the target instance is established:
    -   If the **`edition`** is `STANDARD`, follow the guides under
        `references/standard/`.
    -   If the **`edition`** is `ENTERPRISE` or native mode, follow the guides
        under `references/enterprise/`.

### B. No Instance Found (or New Requested)

If no databases exist or the user requests a new one, default to provisioning an **Enterprise** edition database
and ask the user what location to use.
Run `npx -y firebase-tools@latest firestore:locations` to get the list of options.
Suggest colocating with other resources if applicable.

Once the location is determined, create the database:
`bash npx -y firebase-tools@latest firestore:databases:create <database-id> --edition="enterprise" --location="<selected-location>"`

Proceed with using the guides under `references/enterprise/`.

--------------------------------------------------------------------------------

## 2. Specialized Guides

Based on the identified or created instance edition, open and read the
corresponding reference guides:

### Standard Edition (`references/standard/`)

-   **Provisioning**: Read [provisioning.md](references/standard/provisioning.md)
-   **Security Rules**: Read [security_rules.md](references/standard/security_rules.md)
-   **SDK Usage**: Read [web_sdk_usage.md](references/standard/web_sdk_usage.md), [android_sdk_usage.md](references/standard/android_sdk_usage.md), [ios_setup.md](references/standard/ios_setup.md), or [flutter_setup.md](references/standard/flutter_setup.md)
-   **Indexes**: Read [indexes.md](references/standard/indexes.md)

### Enterprise Edition / Native Mode (`references/enterprise/`)

-   **Provisioning**: Read [provisioning.md](references/enterprise/provisioning.md)
-   **Data Model**: Read [data_model.md](references/enterprise/data_model.md)
-   **Security Rules**: Read [security_rules.md](references/enterprise/security_rules.md)
-   **SDK Usage**: Read [web_sdk_usage.md](references/enterprise/web_sdk_usage.md), [python_sdk_usage.md](references/enterprise/python_sdk_usage.md), [android_sdk_usage.md](references/enterprise/android_sdk_usage.md), [ios_setup.md](references/enterprise/ios_setup.md), or [flutter_setup.md](references/enterprise/flutter_setup.md)
-   **Indexes**: Read [indexes.md](references/enterprise/indexes.md)
