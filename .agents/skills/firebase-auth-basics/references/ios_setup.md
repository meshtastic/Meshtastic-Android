# Firebase Auth iOS Setup Guide

# ⛔️ CRITICAL RULE: NO INLINE INITIALIZATION ⛔️
NEVER write `let auth = Auth.auth()` as an inline class or struct property if there is ANY chance the object is instantiated before `FirebaseApp.configure()` executes in the app root.
- **FATAL CRASH:** `@Observable class AuthManager { let auth = Auth.auth() }` initialized as a `@State` in the App root.
- **SAFE PATTERN:** Initialize `Auth.auth()` lazily (`lazy var auth = Auth.auth()`) OR explicitly initialize the manager *after* `FirebaseApp.configure()` finishes.

## 1. Import and Initialize
Ensure you have installed the `FirebaseAuth` SDK. Use the `xcode-project-setup` skill to automate adding the SPM dependency to the Xcode project.

> **Note:** Ensure `FirebaseApp.configure()` has been executed in your app's entry point before calling any `Auth.auth()` methods, otherwise your app will crash. Do not initialize Auth objects in SwiftUI `@State` properties at the App root level.

```swift
import FirebaseAuth
```

## 2. Authentication State
To listen for authentication state changes (recommended way to check if a user is signed in):

```swift
var handle: AuthStateDidChangeListenerHandle?

handle = Auth.auth().addStateDidChangeListener { auth, user in
  if let user = user {
    print("User is signed in with uid: \(user.uid)")
  } else {
    print("User is signed out")
  }
}

// To remove the listener when no longer needed:
if let handle = handle {
  Auth.auth().removeStateDidChangeListener(handle)
}
```

## 3. Email and Password Authentication (Modern Concurrency)

Modern Swift projects should prioritize `async/await` for authentication calls to avoid nested completion handlers and improve readability.

### Sign Up
```swift
do {
    let authResult = try await Auth.auth().createUser(withEmail: "user@example.com", password: "password")
    print("User created successfully with uid: \(authResult.user.uid)")
} catch {
    print("Error creating user: \(error.localizedDescription)")
}
```

### Sign In
```swift
do {
    let authResult = try await Auth.auth().signIn(withEmail: "user@example.com", password: "password")
    print("User signed in successfully with uid: \(authResult.user.uid)")
} catch {
    print("Error signing in: \(error.localizedDescription)")
}
```

## 4. Sign Out
```swift
do {
  try Auth.auth().signOut()
  print("Successfully signed out")
} catch let signOutError as NSError {
  print("Error signing out: \(signOutError)")
}
```

