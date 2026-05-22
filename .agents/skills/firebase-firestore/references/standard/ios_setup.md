# Firebase Firestore iOS Setup Guide

# ⛔️ CRITICAL RULE: NO FirebaseFirestoreSwift ⛔️

UNDER NO CIRCUMSTANCES should you import, link against, or configure a project to use `FirebaseFirestoreSwift`. 

As of Firebase SDK v11+, all Swift-specific features (including `@DocumentID`, `@ServerTimestamp`, and `Codable` support) have been fully merged into the main `FirebaseFirestore` module.

- NEVER add `.external(name: "FirebaseFirestoreSwift")` or similar to SPM or Xcode configurations.
- NEVER write `import FirebaseFirestoreSwift` in any Swift file. 
- ONLY use `import FirebaseFirestore`.

This is a zero-tolerance constraint. Using `FirebaseFirestoreSwift` is fundamentally incorrect and unacceptable.


# ⛔️ CRITICAL RULE: NO INLINE INITIALIZATION ⛔️
NEVER write `let db = Firestore.firestore()` as an inline class or struct property if there is ANY chance the object is instantiated before `FirebaseApp.configure()` executes in the app root.
- **FATAL CRASH:** `@Observable class DataManager { let db = Firestore.firestore() }` initialized as a `@State` in the App root.
- **SAFE PATTERN:** Initialize `Firestore.firestore()` lazily (`lazy var db = Firestore.firestore()`) OR explicitly initialize the manager *after* `FirebaseApp.configure()` finishes.

## 1. Import and Initialize
Ensure you have installed the `FirebaseFirestore` SDK. Use the `xcode-project-setup` skill to automate adding the SPM dependency to the Xcode project.

```swift
import FirebaseFirestore
```

Initialize an instance of Cloud Firestore:
```swift
let db = Firestore.firestore()
```

## 2. Type-Safe Data Models (Codable)
To leverage modern Swift data modeling, define your data as `Codable` structs. The main `FirebaseFirestore` module automatically supports mapping these types.

```swift
struct User: Codable {
    @DocumentID var id: String?
    var firstName: String
    var lastName: String
    var born: Int
}
```

## 3. Writing Data (Modern Concurrency & Codable)
Using `async/await` and `Codable` ensures type safety and avoids callback hell.

```swift
let user = User(firstName: "Ada", lastName: "Lovelace", born: 1815)

do {
    // Add a new document with a generated ID using Codable
    let ref = try db.collection("users").addDocument(from: user)
    print("Document added with ID: \(ref.documentID)")
} catch {
    print("Error adding document: \(error)")
}
```

## 4. Reading Data (Modern Concurrency & Codable)
```swift
do {
    let querySnapshot = try await db.collection("users").getDocuments()
    
    // Map documents to the User struct automatically
    let users = querySnapshot.documents.compactMap { document in
        try? document.data(as: User.self)
    }
    
    for user in users {
        print("Found user: \(user.firstName) \(user.lastName)")
    }
} catch {
    print("Error getting documents: \(error)")
}
```

## 5. Realtime Listeners in SwiftUI (Lifecycle Best Practices)

When implementing Firestore realtime listeners (`addSnapshotListener`) within a SwiftUI application, you **MUST** tie the listener lifecycle to the view's identity using `.task(id:)`, NOT `.onDisappear`.

### ⛔️ UNSAFE PATTERN (.onDisappear)
Presenting a `.sheet` or `.fullScreenCover` can trigger the underlying view's `onDisappear` method. If you stop your listener here, the feed will stop updating while the sheet is open, and won't resume when it's dismissed.

### ✅ SAFE PATTERN (.task with deinit)

Because `addSnapshotListener` is a synchronous call, placing it inside a `.task` means the task completes immediately. This breaks SwiftUI's automatic cancellation mechanism. 

To safely manage traditional Firebase listeners in SwiftUI, you must use **`deinit`** to handle memory cleanup when the view is destroyed, and **`.task(id:)`** to handle data identity changes while the view is active.

```swift
import SwiftUI
import FirebaseFirestore

@MainActor
@Observable 
final class DataManager {
    private var listenerHandle: ListenerRegistration?
    var data: [String] = []
    
    func startListening(for userId: String) {
        // 1. Clean up any existing listener to prevent duplicates if the ID changes
        stopListening()
        
        // 2. Start the regular listener and capture the handle
        listenerHandle = Firestore.firestore().collection("users").document(userId).addSnapshotListener { snapshot, error in
            // Handle updates
        }
    }
    
    func stopListening() {
        listenerHandle?.remove()
        listenerHandle = nil
    }
    
    // 3. Guarantee cleanup when the View is destroyed and this object is deallocated
    isolated deinit {
        stopListening()
    }
}
```

Then, in your SwiftUI View, trigger the listener using `.task(id:)`. 

```swift
struct MyView: View {
    @State private var manager = DataManager()
    @Environment(AuthManager.self) var authManager
    
    var body: some View {
        List(manager.data, id: \.self) { item in
            Text(item)
        }
        // .task(id:) automatically re-runs if the userId changes.
        // The view model handles stopping the old listener and starting the new one.
        .task(id: authManager.userId) {
            if let userId = authManager.userId {
                manager.startListening(for: userId)
            } else {
                manager.stopListening()
            }
        }
    }
}
```

