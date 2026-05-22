# Tools

## DefineTool

Define a tool the model can call during generation.

```go
type WeatherInput struct {
	Location string `json:"location" jsonschema:"description=City name"`
}

type WeatherOutput struct {
	Temperature float64 `json:"temperature"`
	Conditions  string  `json:"conditions"`
}

weatherTool := genkit.DefineTool(g, "getWeather",
	"Gets the current weather for a location.",
	func(ctx *ai.ToolContext, input WeatherInput) (WeatherOutput, error) {
		// Call your weather API
		return WeatherOutput{Temperature: 72, Conditions: "sunny"}, nil
	},
)
```

## Using Tools in Generation

Pass tools to `Generate`, `GenerateText`, or prompts:

```go
resp, err := genkit.Generate(ctx, g,
	ai.WithModelName("googleai/gemini-flash-latest"),
	ai.WithPrompt("What's the weather in San Francisco?"),
	ai.WithTools(weatherTool),
)
// The model calls the tool automatically and incorporates the result
fmt.Println(resp.Text())
```

### Tool Choice

```go
ai.WithToolChoice(ai.ToolChoiceAuto)     // model decides (default)
ai.WithToolChoice(ai.ToolChoiceRequired) // model must use a tool
ai.WithToolChoice(ai.ToolChoiceNone)     // model cannot use tools
```

### Max Turns

Limit how many tool-call round trips the model can make:

```go
ai.WithMaxTurns(3) // default is 5
```

## DefineMultipartTool

Tools that return both structured output and media content:

```go
screenshotTool := genkit.DefineMultipartTool(g, "screenshot",
	"Takes a screenshot of the current page",
	func(ctx *ai.ToolContext, input any) (*ai.MultipartToolResponse, error) {
		return &ai.MultipartToolResponse{
			Output:  map[string]any{"success": true},
			Content: []*ai.Part{ai.NewMediaPart("image/png", base64Data)},
		}, nil
	},
)
```

## Tool Interrupts

Pause tool execution to request human input before continuing.

### Interrupting

```go
type TransferInput struct {
	ToAccount string  `json:"toAccount"`
	Amount    float64 `json:"amount"`
}

type TransferOutput struct {
	Status  string  `json:"status"`
	Message string  `json:"message"`
	Balance float64 `json:"balance"`
}

type TransferInterrupt struct {
	Reason    string  `json:"reason"`
	ToAccount string  `json:"toAccount"`
	Amount    float64 `json:"amount"`
	Balance   float64 `json:"balance"`
}

transferTool := genkit.DefineTool(g, "transferMoney",
	"Transfers money to another account.",
	func(ctx *ai.ToolContext, input TransferInput) (TransferOutput, error) {
		if input.Amount > accountBalance {
			return TransferOutput{}, ai.InterruptWith(ctx, TransferInterrupt{
				Reason:    "insufficient_balance",
				ToAccount: input.ToAccount,
				Amount:    input.Amount,
				Balance:   accountBalance,
			})
		}
		// Process transfer...
		return TransferOutput{Status: "success", Balance: newBalance}, nil
	},
)
```

### Handling Interrupts

```go
resp, err := genkit.Generate(ctx, g,
	ai.WithModelName("googleai/gemini-flash-latest"),
	ai.WithTools(transferTool),
	ai.WithPrompt(userRequest),
)

for resp.FinishReason == ai.FinishReasonInterrupted {
	var restarts, responses []*ai.Part

	for _, interrupt := range resp.Interrupts() {
		meta, ok := ai.InterruptAs[TransferInterrupt](interrupt)
		if !ok {
			continue
		}

		switch meta.Reason {
		case "insufficient_balance":
			// RestartWith: re-execute the tool with adjusted input
			part, err := transferTool.RestartWith(interrupt,
				ai.WithNewInput(TransferInput{
					ToAccount: meta.ToAccount,
					Amount:    meta.Balance, // transfer what's available
				}),
			)
			if err != nil { return err }
			restarts = append(restarts, part)

		case "confirm_large":
			// RespondWith: provide a response directly without re-executing
			part, err := transferTool.RespondWith(interrupt,
				TransferOutput{Status: "cancelled", Message: "User declined"},
			)
			if err != nil { return err }
			responses = append(responses, part)
		}
	}

	// Continue generation with the resolved interrupts
	resp, err = genkit.Generate(ctx, g,
		ai.WithMessages(resp.History()...),
		ai.WithTools(transferTool),
		ai.WithToolRestarts(restarts...),
		ai.WithToolResponses(responses...),
	)
	if err != nil { return err }
}
```

### Checking Resume State

Inside a tool function, check if the tool is being resumed from an interrupt:

```go
func(ctx *ai.ToolContext, input TransferInput) (TransferOutput, error) {
	if ctx.IsResumed() {
		// This is a resumed call after an interrupt
		original, ok := ai.OriginalInputAs[TransferInput](ctx)
		// original contains the input from the first call
	}
	// ...
}
```
