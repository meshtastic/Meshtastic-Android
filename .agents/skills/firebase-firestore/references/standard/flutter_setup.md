# Cloud Firestore in Flutter

This guide covers basic CRUD operations, type-safe data modeling, and real-time streams when using Cloud Firestore in a Flutter application via the `cloud_firestore` package.

## 1. Setup

Ensure you have added the required dependency:
```bash
flutter pub add cloud_firestore
```
Also, ensure FlutterFire is configured properly for your target platforms.

---

## 2. Best Practices: Type-Safe Models

Instead of passing raw `Map<String, dynamic>` maps throughout your UI layer, define a domain model class with `fromFirestore` and `toFirestore` converters to maintain type safety.

```dart
import 'package:cloud_firestore/cloud_firestore.dart';

class Item {
  final String id;
  final String name;
  final String ownerId;
  final DateTime createdAt;

  Item({
    required this.id,
    required this.name,
    required this.ownerId,
    required this.createdAt,
  });

  factory Item.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>? ?? {};
    return Item(
      id: doc.id,
      name: data['name'] as String? ?? '',
      ownerId: data['ownerId'] as String? ?? '',
      createdAt: data['createdAt'] is Timestamp 
          ? (data['createdAt'] as Timestamp).toDate() 
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toFirestore() {
    return {
      'name': name,
      'ownerId': ownerId,
      'createdAt': Timestamp.fromDate(createdAt),
    };
  }
}
```

---

## 3. The Service Layer

Encapsulate all database interactions within a dedicated service class to keep your UI code clean and testable.

### Initialization & References

```dart
class ItemService {
  final FirebaseFirestore _db = FirebaseFirestore.instance;

  // Define your collection reference
  CollectionReference get _itemsRef => _db.collection('items');

  // 1. Create Data
  Future<void> createItem(Item item) async {
    try {
      await _itemsRef.add(item.toFirestore());
    } catch (e) {
      print("Error creating document: \$e");
    }
  }

  // 2. Read Data (One-Time Fetch)
  Future<List<Item>> fetchItems(String ownerId) async {
    try {
      final querySnapshot = await _itemsRef
          .where('ownerId', isEqualTo: ownerId)
          .orderBy('createdAt', descending: true)
          .get();

      return querySnapshot.docs.map((doc) => Item.fromFirestore(doc)).toList();
    } catch (e) {
      print("Error fetching documents: \$e");
      return [];
    }
  }

  // 3. Read Data (Real-Time Stream)
  Stream<List<Item>> streamItems(String ownerId) {
    return _itemsRef
        .where('ownerId', isEqualTo: ownerId)
        .snapshots()
        .map((snapshot) {
          // If a custom composite index is missing during prototyping, apply sorting client-side:
          final items = snapshot.docs.map((doc) => Item.fromFirestore(doc)).toList();
          items.sort((a, b) => b.createdAt.compareTo(a.createdAt));
          return items;
        });
  }

  // 4. Update Data
  Future<void> updateItemName(String id, String newName) async {
    try {
      await _itemsRef.doc(id).update({'name': newName});
    } catch (e) {
      print("Error updating document: \$e");
    }
  }

  // 5. Delete Data
  Future<void> deleteItem(String id) async {
    try {
      await _itemsRef.doc(id).delete();
    } catch (e) {
      print("Error deleting document: \$e");
    }
  }
}
```

---

## 4. Listening to Streams in the UI (`StreamBuilder`)

Use Flutter's `StreamBuilder` to rebuild the interface reactively whenever data changes in your database collection.

```dart
StreamBuilder<List<Item>>(
  stream: itemService.streamItems(currentUser.uid),
  builder: (context, snapshot) {
    if (snapshot.hasError) {
      return const Center(child: Text('Failed to load data'));
    }

    if (snapshot.connectionState == ConnectionState.waiting) {
      return const Center(child: CircularProgressIndicator());
    }

    final items = snapshot.data ?? [];

    if (items.isEmpty) {
      return const Center(child: Text('No items found.'));
    }

    return ListView.builder(
      itemCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        return ListTile(
          title: Text(item.name),
          trailing: IconButton(
            icon: const Icon(Icons.delete),
            onPressed: () => itemService.deleteItem(item.id),
          ),
        );
      },
    );
  },
);
```
