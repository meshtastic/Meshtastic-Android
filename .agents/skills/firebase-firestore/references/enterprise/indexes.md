# Firestore Indexes Reference

Indexes helps to improve query performance. Firestore Enterprise edition does
not create any indexes by default. By default, Firestore Enterprise performs a
full collection scan to find documents that match a query, which can be slow and
expensive for large collections. To avoid this, you can create indexes to
optimize your queries.

## Index Structure

An index consists of the following:

*   a collection ID.
*   a list of fields in the given collection.
*   an order, either ascending or descending, for each field.

### Index Ordering

The order and sort direction of each field uniquely defines the index. For
example, the following indexes are two distinct indexes and not interchangeable:

*   Field name `name` (ascending) and `population` (descending)
*   Field name `name` (descending) and `population` (ascending)

### Index Density

Dense indexes: By default, Firestore indexes store data from all documents in a
collection. An index entry will be added for a document regardless of whether
the document contains any of the fields specified in the index. Non-existent
fields are treated as having a NULL value when generating index entries.

Sparse indexes: To change this behavior, you can define the index as a sparse
index. A sparse index indexes only the documents in the collection that contain
a value (including null) for at least one of the indexed fields. A sparse index
reduces storage costs and can improve performance.

### Unique Indexes

You can use unique index option to enforce unique values for the indexed fields.
For indexes on multiple fields, each combination of values must be unique across
the index. The database rejects any update and insert operations that attempt to
create index entries with duplicate values.

## Query Support Examples

| Query Type                           | Index Required                       |
| :----------------------------------- | :----------------------------------- |
| **Simple Equality**<br>`where("a",   | Single-Field Index on field `a`      |
: "==", 1)`                            :                                      :
| **Simple Range/Sort**<br>`where("a", | Single-Field Index on field `a`      |
: ">", 1).orderBy("a")`                :                                      :
| **Multiple Equality**<br>`where("a", | Single-Field Index on field `a` and  |
: "==", 1).where("b", "==", 2)`        : `b`                                  :
| **Equality +                         | **Composite Index** on field `a` and |
: Range/Sort**<br>`where("a", "==",    : `b`                                  :
: 1).where("b", ">", 2)`               :                                      :
| **Multiple Ranges**<br>`where("a",   | **Composite Index** on field `a` and |
: ">", 1).where("b", ">", 2)`          : `b`                                  :
| **Array Contains +                   | **Composite Index** on field `tags`  |
: Equality**<br>`where("tags",         : and `active`                         :
: "array-contains",                    :                                      :
: "news").where("active", "==", true)` :                                      :

If no indexes is present, Firestore Enterprise will perform a full collection
scan to find documents that match a query.

## Management

### Config files

Your indexes should be defined in `firestore.indexes.json` (pointed to by
`firebase.json`).

Define a dense index:

```json
{
  "indexes": [
    {
      "collectionGroup": "cities",
      "queryScope": "COLLECTION",
      "density": "DENSE",
      "fields": [
        { "fieldPath": "country", "order": "ASCENDING" },
        { "fieldPath": "population", "order": "DESCENDING" }
      ]
    }
  ],
  "fieldOverrides": []
}
```

Define a sparse-any index:

```json
{
  "indexes": [
    {
      "collectionGroup": "cities",
      "queryScope": "COLLECTION",
      "density": "SPARSE_ANY",
      "fields": [
        { "fieldPath": "country", "order": "ASCENDING" },
        { "fieldPath": "population", "order": "DESCENDING" }
      ]
    }
  ],
  "fieldOverrides": []
}
```

Define a unique index:

```json
{
  "indexes": [
    {
      "collectionGroup": "cities",
      "queryScope": "COLLECTION",
      "density": "SPARSE_ANY",
      "unique": true,
      "fields": [
        { "fieldPath": "country", "order": "ASCENDING" },
        { "fieldPath": "population", "order": "DESCENDING" }
      ]
    }
  ],
  "fieldOverrides": []
}
```

### CLI Commands

Deploy indexes only: `bash npx firebase-tools@latest -y deploy --only
firestore:indexes`
