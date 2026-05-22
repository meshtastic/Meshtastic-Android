# Firebase Authentication on Android (Kotlin)

This guide walks you through using Firebase Authentication in your Android app using Kotlin DSL (`build.gradle.kts`) and Kotlin code.

### 1, Enable Authentication via CLI

Before adding dependencies in your app, make sure you enable the Auth service in your Firebase Project using the Firebase CLI:

```bash
npx -y firebase-tools@latest init auth
```

 ---

### 2. Add Dependencies

In your module-level `build.gradle.kts` (usually `app/build.gradle.kts`), add the dependency for Firebase Authentication:

```kotlin
dependencies {
    // [AGENT] Fetch the latest available BoM version from https://firebase.google.com/support/release-notes/android before adding this
    implementation(platform("com.google.firebase:firebase-bom:<latest_bom_version>"))

    // Add the dependency for the Firebase Authentication library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-auth")
}
```

---

### 3. Initialize FirebaseAuth

In your Activity or Fragment, initialize the `FirebaseAuth` instance:

```kotlin
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = Firebase.auth
        
        setContent {
            MaterialTheme {
                Text("Auth initialized!")
            }
        }
    }
}
```

#### Jetpack Compose (Modern)

Initialize inside a `ComponentActivity` using `setContent`:

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = Firebase.auth
        
        setContent {
            MaterialTheme {
                Text("Auth initialized!")
            }
        }
    }
}
```

---

### 4. Check Current Auth State

You should check if a user is already signed in when your activity starts:

```kotlin
public override fun onStart() {
    super.onStart()
    // Check if user is signed in (non-null) and update UI accordingly.
    val currentUser = auth.currentUser
    if (currentUser != null) {
        // User is signed in, navigate to main screen or update UI
    } else {
        // No user is signed in, prompt for login
    }
}
```

---

### 5. Sign Up New Users (Email/Password)

Use `createUserWithEmailAndPassword` to register new users:

```kotlin
fun signUpUser(email: String, password: String) {
    auth.createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                // Sign up success, update UI with the signed-in user's information
                val user = auth.currentUser
                // Navigate to main screen
            } else {
                // If sign up fails, display a message to the user.
                Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
            }
        }
}
```

---

### 6. Sign In Existing Users (Email/Password)

Use `signInWithEmailAndPassword` to log in existing users:

```kotlin
fun signInUser(email: String, password: String) {
    auth.signInWithEmailAndPassword(email, password)
        .addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                // Sign in success, update UI with the signed-in user's information
                val user = auth.currentUser
                // Navigate to main screen
            } else {
                // If sign in fails, display a message to the user.
                Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
            }
        }
}
```

---

### 7. Sign Out

To sign out a user, call `signOut()` on the `FirebaseAuth` instance:

```kotlin
auth.signOut()
// Navigate to login screen
```
