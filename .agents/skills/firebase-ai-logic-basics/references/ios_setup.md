# Firebase AI Logic iOS Setup Guide

## 1. Import and Initialize
Ensure you have installed the `FirebaseAILogic` SDK via Swift Package Manager.

```swift
import FirebaseAILogic

// Initialize the Firebase AI service and the generative model.
let ai = FirebaseAI.firebaseAI()

// Specify a model that's appropriate for your use case.
let model = ai.generativeModel(modelName: "gemini-flash-latest")
```

## 2. SwiftUI Integration (Best Practices)
Use the `@Observable` pattern to manage AI state and provide a smooth UX with loading indicators and error handling.

> **⛔️ CRITICAL WARNING:** Do NOT initialize the model inline as a class property if there's any chance the view model is instantiated before `FirebaseApp.configure()` executes in the app root. 
> To be safe, initialize the model lazily or pass it in from a point in the hierarchy where Firebase is guaranteed to be configured.

```swift
import SwiftUI
import FirebaseAILogic

@MainActor
@Observable
final class AIViewModel {
    // Initialize lazily to ensure FirebaseApp is configured first
    private lazy var model = FirebaseAI.firebaseAI().generativeModel(modelName: "gemini-flash-latest")
    
    var responseText: String = ""
    var isFetching: Bool = false
    var errorMessage: String?
    
    func generate(prompt: String) async {
        isFetching = true
        errorMessage = nil
        defer { isFetching = false }
        
        do {
            let response = try await model.generateContent(prompt)
            self.responseText = response.text ?? "No response"
        } catch {
            self.errorMessage = error.localizedDescription
        }
    }
}

struct AIView: View {
    @State private var viewModel = AIViewModel()
    @State private var prompt = "Write a story about a magic backpack."
    
    var body: some View {
        VStack {
            TextField("Enter prompt", text: $prompt)
            
            Button("Generate") {
                Task { await viewModel.generate(prompt: prompt) }
            }
            .disabled(viewModel.isFetching)
            
            if viewModel.isFetching {
                ProgressView()
            } else if let error = viewModel.errorMessage {
                Text(error).foregroundStyle(.red)
            } else {
                ScrollView {
                    Text(viewModel.responseText)
                }
            }
        }
        .padding()
    }
}
```

## 3. Safety Settings
You can configure safety thresholds to prevent the model from generating harmful content.

```swift
let safetySettings = [
  SafetySetting(category: .harassment, threshold: .blockLowAndAbove),
  SafetySetting(category: .hateSpeech, threshold: .blockMediumAndAbove)
]

let model = FirebaseAI.firebaseAI().generativeModel(
  modelName: "gemini-flash-latest",
  safetySettings: safetySettings
)
```

# Advanced Features

### Chat Session (Multi-turn)
Chat sessions persist state across multiple interactions, which is essential for ongoing conversations or when using tools like function calling.

```swift
let chat = model.startChat()

Task {
    do {
        let response1 = try await chat.sendMessage("Hello! I have two dogs in my house.")
        print(response1.text ?? "")

        let response2 = try await chat.sendMessage("How many paws are in my house?")
        print(response2.text ?? "")
    } catch {
        print("Error in chat: \(error)")
    }
}
```

### Function Calling (Tools)
Define functions that the model can request to execute to interact with external systems. *Note: Advanced workflows like function calling generally require a multi-turn Chat Session to handle the back-and-forth execution.*

```swift
let getStockPriceTool = Tool(functionDeclarations: [
  FunctionDeclaration(
    name: "getStockPrice",
    description: "Get the current stock price for a given symbol.",
    parameters: [
      "symbol": Schema(
        type: .string,
        description: "The stock symbol, e.g. AAPL"
      )
    ]
  )
])

let model = FirebaseAI.firebaseAI().generativeModel(
  modelName: "gemini-flash-latest",
  tools: [getStockPriceTool]
)

// In your task (using a chat session):
let chat = model.startChat()
let response = try await chat.sendMessage("What is the stock price of Apple?")
if let functionCall = response.functionCalls.first {
    // Handle the function call (e.g. call a local API and send the result back)
    print("Model requested function: \(functionCall.name) with args: \(functionCall.args)")
}
```
