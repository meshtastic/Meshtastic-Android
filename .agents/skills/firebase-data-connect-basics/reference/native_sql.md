# Native SQL Operations

Always default to Native GraphQL. Use Native SQL **only** when you need database-specific features not available in GraphQL (e.g., PostGIS, Window Functions, Complex Aggregations, or specific DML CTEs).

## Core Agent Constraints

When generating Native SQL operations, you are bypassing GraphQL and talking directly to PostgreSQL. You **MUST** adhere to these strict constraints:

1.  **Operation Syntax Isolation:** Never mix Native SQL positional parameters (`$1`) with standard GraphQL named variables (`$id`). The `sql:` argument MUST be a hardcoded string literal block (`"""SELECT..."""`), not a GraphQL variable.
2.  **Table & Column Mapping (Case Sensitivity):**
    *   **Default `snake_case` Conversion:** By default, SQL Connect converts `PascalCase` types and `camelCase` fields to `snake_case` in the database.
        *   *Schema:* `type UserProfile { releaseYear: Int }` -> *Native SQL:* `SELECT release_year FROM user_profile`
    *   **Explicit Overrides (Requires Double Quotes):** If the schema uses `@table(name: "ExactName")` or `@col(name: "ExactCol")`, you **MUST wrap the identifier in double quotes** if it contains capital letters (e.g., `SELECT * FROM "ExactName"`). Without quotes, Postgres folds it to lowercase and fails validation.

## Syntax rules & limitations

Native SQL enforces strict parsing rules to ensure security and prevent SQL injection:

*   **String Literals Only:** The `sql` argument must be a hardcoded string literal block (`"""SELECT..."""`) directly in the `.gql` file. It **cannot** be a GraphQL variable.
*   **Validation:** Do **NOT** use DDL in any operations (modify the `schema.gql` file instead for table/column changes). Furthermore, `query` operations cannot contain DML and must start with `SELECT`, `TABLE`, or `WITH`.
*   **Parameters:** Use strict positional parameters (`$1`, `$2`) that match the `params` array order. Named parameters (`$id`, `:name`) are **forbidden**.
*   **Comments:** Use block comments (`/* ... */`). Line comments (`--`) are **forbidden** because they can truncate subsequent clauses during query compilation. If you comment out a line containing a parameter (e.g., `/* WHERE id = $1 */`), you must also remove that parameter from the `params` list, or it will fail with `unused parameter: $1`.
*   **Strings:** Extended string literals (`E'...'`) and dollar-quoted strings (`$$...$$`) are supported.
*   **Context Maps (`_expr`):** Variables **cannot** be used inside `_expr` fields; to ensure security, `_expr` must be a static string (e.g., `{_expr: "auth.uid"}`, not `{_expr: $uidVar}`).

## Native SQL Root Fields

Operations are executed using the permissions granted to the SQL Connect service account. You can alias the root field (e.g., `movies: _select`) to make the client response cleaner (`data.movies` instead of `data._select`).

> **Note on `Any` Return Types:** Because Native SQL completely bypasses GraphQL's strong typing, queries like `_select` and `_executeReturning` return the generic `Any` scalar type. The generated client SDKs (TypeScript, Swift, Kotlin, Dart) will type this as `any` (or equivalent). **AGENT INSTRUCTION**: When you generate client-side code that consumes these operations, you MUST manually cast or validate the shape of the data, as the typical type safety of SQL Connect will not be present.

Use these root fields in `query` or `mutation` operations:

### Query Fields (Read-Only)

*   `_select`: Executes a SQL query returning zero or more rows. Returns `[Any]`.
    ```graphql
    query GetMovies($genre: String!) @auth(level: PUBLIC) {
      movies: _select(
        sql: "SELECT id, title FROM movie WHERE genre = $1",
        params: [$genre]
      )
    }
    ```
*   `_selectFirst`: Executes a SQL query expected to return zero or one row. Returns `Any` or `null`.
    ```graphql
    query GetTotalReviews @auth(level: PUBLIC) {
      stats: _selectFirst(
        sql: "SELECT COUNT(*) as total_reviews FROM review"
      ) # params can be omitted if empty
    }
    ```

### Mutation Fields (DML)

*   `_execute`: Executes DML (`INSERT`, `UPDATE`, `DELETE`). Returns `Int` (number of rows affected).
    *   *Note 1:* `RETURNING` clauses are ignored in the result.
    *   *Note 2:* Only `_execute` supports Data-Modifying Common Table Expressions (e.g., `WITH new_row AS (INSERT...)`).
    ```graphql
    mutation UpdateRating($id: UUID!, $rating: Float!) @auth(level: USER) {
      _execute(
        sql: "UPDATE movie SET rating = $2 WHERE id = $1",
        params: [$id, $rating]
      )
    }
    ```
*   `_executeReturning`: Executes DML with a `RETURNING` clause. Returns `[Any]`. Data-Modifying CTEs are **not** supported.
    ```graphql
    mutation DeleteUserReviews($uid: String!) @auth(level: USER) {
      deletedReviews: _executeReturning(
        sql: "DELETE FROM review WHERE user_id = $1 RETURNING id, rating",
        params: [{_expr: "auth.uid"}]
      )
    }
    ```
*   `_executeReturningFirst`: Executes DML with `RETURNING`, expecting zero or one row. Returns `Any` or `null`. Data-Modifying CTEs are **not** supported.
    ```graphql
    mutation UpdateMyReview($movieId: UUID!, $text: String!) @auth(level: USER) {
      updatedReview: _executeReturningFirst(
        sql: """
          UPDATE review SET text = $2 
          WHERE movie_id = $1 AND user_id = $3 
          RETURNING id, text
        """,
        params: [$movieId, $text, {_expr: "auth.uid"}]
      )
    }
    ```
    
### PostgreSQL Extensions

Native SQL allows you to directly query and utilize PostgreSQL extensions, such as `PostGIS`, without needing to map complex geometry types into your GraphQL schema or alter your underlying tables (e.g., using JSON operators to extract values and pass them into `ST_MakePoint`). 

*Note: You must enable the extension on your underlying Cloud SQL instance by connecting as the `postgres` user and running `CREATE EXTENSION IF NOT EXISTS ...;`*

*(See `examples.md` for a full `GetNearbyActiveRestaurants` implementation).*

## ⚠️ Security: Stored Procedures & Dynamic SQL

SQL Connect parameterizes inputs at the GraphQL boundary automatically. However, if your Native SQL calls **custom PL/pgSQL stored procedures**, you must manually prevent 2nd-order SQL injection:

*   **NEVER** concatenate user input into an `EXECUTE` string (`EXECUTE 'UPDATE ' || table || ' SET x=' || val;`).
*   **DO** use the `USING` clause to bind data values safely.
*   **DO** use `format('%I')` for safe database identifier injection.
*   **DO** validate dynamic table/column names against a strict hardcoded allowlist.

**Secure PL/pgSQL Pattern:**
```sql
CREATE OR REPLACE PROCEDURE secure_update(target_table TEXT, new_value TEXT, row_id INT)
LANGUAGE plpgsql AS $$
BEGIN
    -- 1. Strict Allowlist for Identifiers
    IF target_table NOT IN ('orders', 'users', 'inventory') THEN
        RAISE EXCEPTION 'Invalid table name';
    END IF;

    -- 2. format(%I) for Identifiers, USING for Data
    EXECUTE format('UPDATE %I SET status = $1 WHERE id = $2', target_table)
    USING new_value, row_id;
END;
$$;
```
