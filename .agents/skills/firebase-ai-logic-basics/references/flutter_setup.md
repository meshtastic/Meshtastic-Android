# Flutter Setup for Firebase AI Logic

This guide covers how to integrate Firebase AI Logic (Gemini API) into your Flutter applications.

> [!IMPORTANT]
> **Foundational Workflows & CLI-First Approach:**
> 1. **Review Foundation:** Before implementing platform-specific code, ALWAYS review the foundational `firebase-basics` skill to ensure familiarity with core workflows.
> 2. **Backend Provisioning via CLI:** Use the Firebase CLI for backend setup. Running `npx firebase-tools init ailogic` is MANDATORY to provision the service. `flutterfire configure` does NOT enable the AI service and will result in `PERMISSION_DENIED` if skipped.
> 3. **Client Configuration:** Use `flutterfire configure` strictly for generating `firebase_options.dart`. Avoid manual Console configuration.

> [!NOTE]
> `firebase_vertexai` has been replaced by `firebase_ai`. Always use `firebase_ai` for new projects.

## Installation

Add the necessary Firebase dependencies to your `pubspec.yaml`:

```yaml
dependencies:
  flutter:
    sdk: flutter
  firebase_core: ^4.0.0
  firebase_auth: ^6.0.0
  firebase_ai: ^3.0.0
```

Run `flutter pub get` to install the packages.

## Initialization

Initialize Firebase and sign in (anonymously or via authenticated user) before using AI Logic.

```dart
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_ai/firebase_ai.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  await FirebaseAuth.instance.signInAnonymously();
  runApp(const MyApp());
}
```

## Usage

Use `FirebaseAI.googleAI` for the **Gemini Developer API**.

> [!IMPORTANT]
> **Model Selection:** Always use **`gemini-flash-latest`**. DO NOT USE `gemini-1.5-flash`.

> [!IMPORTANT]
> **Choose the Right API Provider:** Always use `FirebaseAI.googleAI` (Gemini Developer API) as the default for prototyping and standard use. Avoid using the Vertex AI Gemini API unless your application specifically requires enterprise-grade scalability and is configured for the Blaze plan.

### Text Generation

```dart
import 'package:firebase_ai/firebase_ai.dart';
import 'package:firebase_auth/firebase_auth.dart';

Future<String> generateText(String prompt) async {
  final googleAI = FirebaseAI.googleAI(auth: FirebaseAuth.instance);
  
  // Use the latest Gemini Flash model
  final model = googleAI.generativeModel(model: 'gemini-flash-latest');

  final response = await model.generateContent([Content.text(prompt)]);
  return response.text ?? 'No response';
}
```

### Chat Session

```dart
final chat = model.startChat(history: [
  Content.text('Hello, I am a user.'),
  Content.model([TextPart('Hello! How can I help you today?')]),
]);

final response = await chat.sendMessage(Content.text('What is CBT?'));
```
