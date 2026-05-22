# Firestore Enterprise Native Mode on Android (Kotlin)

This guide walks you through using the Cloud Firestore SDK in your Android app using Kotlin. The SDK for Firestore Enterprise Native Mode is the same as the standard Cloud Firestore SDK.

### Enable Firestore via CLI

Before adding dependencies in your app, make sure you enable the Firestore service in your Firebase Project using the Firebase CLI:

```bash
npx -y firebase-tools@latest init firestore
```

 ---
 
### 1. Add Dependencies

In your module-level `build.gradle.kts` (usually `app/build.gradle.kts`), add the dependency for Cloud Firestore:

```kotlin
dependencies {
    // [AGENT] Fetch the latest available BoM version from https://firebase.google.com/support/release-notes/android before adding this
    implementation(platform("com.google.firebase:firebase-bom:<latest_bom_version>"))

    // Add the dependency for the Cloud Firestore library
    implementation("com.google.firebase:firebase-firestore")
}
```

---

### 2. Initialize Firestore

In your Activity or Fragment, initialize the `FirebaseFirestore` instance:

```kotlin
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Firebase.firestore
        
        setContent {
            MaterialTheme {
                Text("Firestore initialized!")
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
import com.google.firebase.firestore.firestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Firebase.firestore
        
        setContent {
            MaterialTheme {
                Text("Firestore initialized!")
            }
        }
    }
}
```

---

### 3. Basic CRUD Operations

The operations are identical to standard Firestore.

#### Add Data

```kotlin
val user = hashMapOf(
    "first" to "Alan",
    "last" to "Turing",
    "born" to 1912
)

db.collection("users")
    .add(user)
    .addOnSuccessListener { documentReference ->
        Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
    }
    .addOnFailureListener { e ->
        Log.w(TAG, "Error adding document", e)
    }
```

#### Read Data

```kotlin
db.collection("users")
    .get()
    .addOnSuccessListener { result ->
        for (document in result) {
            Log.d(TAG, "${document.id} => ${document.data}")
        }
    }
    .addOnFailureListener { exception ->
        Log.w(TAG, "Error getting documents.", exception)
    }
```

#### Update Data

```kotlin
val userRef = db.collection("users").document("your-document-id")

userRef
    .update("born", 1913)
    .addOnSuccessListener { Log.d(TAG, "DocumentSnapshot successfully updated!") }
    .addOnFailureListener { e -> Log.w(TAG, "Error updating document", e) }
```

#### Delete Data

```kotlin
db.collection("users").document("your-document-id")
    .delete()
    .addOnSuccessListener { Log.d(TAG, "DocumentSnapshot successfully deleted!") }
    .addOnFailureListener { e -> Log.w(TAG, "Error deleting document", e) }
```
