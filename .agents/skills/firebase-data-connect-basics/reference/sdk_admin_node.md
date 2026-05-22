# Admin Node SDK

Consult this file when writing server-side code (e.g., Cloud Functions) that needs elevated privileges or needs to impersonate specific users.

### Best Practices for Agents
- **Understand Operation Storage**: SQL Connect queries and mutations are stored on the server like Cloud Functions. Clients do not submit the raw operations. Therefore, **whenever you update operations, you must regenerate the SDK and redeploy services** that use it.
- **Follow Least Privilege**: Admin SDKs have unrestricted access by default. Always use impersonation when possible to limit access.
- **Impersonation**: Use the `impersonate` parameter to run operations as a specific user or as an unauthenticated user.
- **Impersonation Variables**: If you call an operation with optional variables and want to pass impersonation options but without variables, you **MUST** pass `undefined` as the first argument (variables) to clearly indicate no variables are being provided.
- **Admin Operations**: If you create operations intended only for administration, define them with `@auth(level: NO_ACCESS)`. This ensures they can only be called via the Admin SDK with unrestricted access.
- **Resilient Enum Handling**: JavaScript/TypeScript does not enforce exhaustive checks on enums. Always add a `default` branch to `switch` statements or an `else` branch to handle unknown values gracefully when schemas evolve.

### Configuration in `connector.yaml`

To generate an Admin SDK, add the `adminNodeSdk` block to your `connector.yaml`:

```yaml
connectorId: my-connector
generate:
  adminNodeSdk:
    outputDir: "./admin-sdk"
    package: "@dataconnect/admin-generated"
    packageJsonDir: "." # Directory containing package.json
```

### Generation

Run the generation command:

```bash
npx -y firebase-tools@latest dataconnect:sdk:generate
```

### Usage Examples

#### 1. Impersonating an Unauthenticated User
Unauthenticated users can only run operations marked as `PUBLIC`.

```typescript
import { initializeApp } from "firebase-admin/app";
import { getDataConnect } from "firebase-admin/data-connect";
import { connectorConfig, getSongs } from "@dataconnect/admin-generated";

const adminApp = initializeApp();
const adminDc = getDataConnect(connectorConfig);

const songs = await getSongs(
  adminDc,
  { limit: 4 },
  { impersonate: { unauthenticated: true } }
);
```

#### 2. Impersonating a Specific User (Cloud Functions)
When using callable Cloud Functions, the authentication token is automatically verified.

```typescript
import { HttpsError, onCall } from "firebase-functions/https";
import { getMyFavoriteSongs } from "@dataconnect/admin-generated";

export const callableExample = onCall(async (req) => {
    const authClaims = req.auth?.token;
    if (!authClaims) {
        throw new HttpsError("unauthenticated", "Unauthorized");
    }

    const favoriteSongs = await getMyFavoriteSongs(
        adminDc,
        undefined,
        { impersonate: { authClaims } }
    );

    return favoriteSongs;
});
```

#### 3. Impersonating a Specific User (Plain HTTP)
For non-callable endpoints, you must verify the token yourself.

```typescript
import { getAuth } from "firebase-admin/auth";
import { onRequest } from "firebase-functions/https";
import { getMyFavoriteSongs } from "@dataconnect/admin-generated";

const auth = getAuth();

export const httpExample = onRequest(async (req, res) => {
    const token = req.header("authorization")?.replace(/^bearer\s+/i, "");
    if (!token) {
        res.sendStatus(401);
        return;
    }
    let authClaims;
    try {
        authClaims = await auth.verifyIdToken(token);
    } catch {
        res.sendStatus(401);
        return;
    }

    const favoriteSongs = await getMyFavoriteSongs(
        adminDc,
        undefined,
        { impersonate: { authClaims } }
    );

    res.send(favoriteSongs);
});
```

#### 4. Running with Unrestricted Access
Omit the `impersonate` parameter to run with full admin access. Only do this for true administrative tasks.

```typescript
import { upsertSong } from "@dataconnect/admin-generated";

await upsertSong(adminDc, {
  title: "New Song",
  genre: "Rock"
});
```
