# Firestore Data Model Reference

Firestore is a NoSQL, document-oriented database. Unlike a SQL database, there
are no tables or rows. Instead, you store data in **documents**, which are
organized into **collections**.

## Document Data Model

Data in Firestore is organized into documents, collections, and subcollections.

### Documents

A **document** is a lightweight record that contains fields, which map to
values. Each document is identified by a name. A document can contain complex
nested objects in addition to basic data types like strings, numbers, and
booleans. Documents are limited to a maximum size of 1 MiB.

Example document (e.g., in a `users` collection): `json { "first": "Ada",
"last": "Lovelace", "born": 1815 }`

### Collections

Documents live in **collections**, which are containers for your documents. For
example, you could have a `users` collection to contain your various users, each
represented by a document. * Collections can only contain documents. They cannot
directly contain raw fields with values, and they cannot contain other
collections. * Documents within a collection can contain different fields. * You
don't need to "create" or "delete" collections explicitly. After you create the
first document in a collection, the collection exists. If you delete all of the
documents in a collection, the collection no longer exists.

### Subcollections

Documents can contain subcollections natively. A subcollection is a collection
associated with a specific document. For example, a user document in the `users`
collection could have a `messages` subcollection containing message documents
exclusively for that user. This creates a powerful hierarchical data structure.

Data path example: `users/user1/messages/message1`

## Collection Group Support

A **collection group** consists of all collections with the same ID. By default,
queries retrieve results from a single collection in your database. Use a
collection group query to retrieve documents from a collection group instead of
from a single collection.

### Use Cases

Collection group queries are useful when you want to query across multiple
subcollections that share the same organizational structure.

For example, imagine an app with a `landmarks` collection where each landmark
has a `reviews` subcollection. If you want to find all 5-star reviews across
*all* landmarks, it would involve checking many separate `reviews`
subcollections. With a collection group, you can perform a single query against
the `reviews` collection group.

### Examples

**Standard Query** (Single Collection): Find all 5-star reviews for a specific
landmark. `javascript
db.collection('landmarks/golden_gate_bridge/reviews').where('rating', '==', 5)`

**Collection Group Query**: Find all 5-star reviews across *all* landmarks.
`javascript db.collectionGroup('reviews').where('rating', '==', 5)`
