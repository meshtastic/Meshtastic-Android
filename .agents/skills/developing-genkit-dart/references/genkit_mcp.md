# Genkit MCP (`genkit_mcp`)

MCP (Model Context Protocol) integration for Genkit Dart.

## MCP Host (Recommended)
Connect to one or more MCP servers and aggregate their capabilities into the Genkit registry automatically.

```dart
import 'package:genkit/genkit.dart';
import 'package:genkit_mcp/genkit_mcp.dart';

void main() async {
  final ai = Genkit();

  final host = defineMcpHost(
    ai,
    McpHostOptionsWithCache(
      name: 'my-host',
      mcpServers: {
        'fs': McpServerConfig(
          command: 'npx',
          args: ['-y', '@modelcontextprotocol/server-filesystem', '.'],
        ),
      },
    ),
  );

  // Tools can be discovered and executed dynamically using a wildcard...
  final response = await ai.generate(
    model: 'gemini-2.5-flash',
    prompt: 'Summarize the contents of README.md',
    toolNames: ['my-host:tool/fs/*'],
  );
  
  // ...or by specifying the exact tool name
  final exactResponse = await ai.generate(
    model: 'gemini-2.5-flash',
    prompt: 'Read README.md',
    toolNames: ['my-host:tool/fs/read_file'],
  );
}
```

## MCP Client (Advanced / Single Server)
Connecting to a single MCP server with a client object is an advanced usecase for when you need manual control over the client lifecycle. Standalone clients do not automatically register tools into the registry, so they must be passed into `generate` or `defineDynamicActionProvider` manually.

```dart
import 'package:genkit/genkit.dart';
import 'package:genkit_mcp/genkit_mcp.dart';

void main() async {
  final ai = Genkit();

  final client = createMcpClient(
    McpClientOptions(
      name: 'my-client',
      mcpServer: McpServerConfig(
        command: 'npx',
        args: ['-y', '@modelcontextprotocol/server-filesystem', '.'],
      ),
    ),
  );
  
  await client.ready();

  // Retrieve the tools from the connected client
  final tools = await client.getActiveTools(ai);
  
  final response = await ai.generate(
    model: 'gemini-2.5-flash',
    prompt: 'Read the contents of README.md',
    tools: tools,
  );
}
```

## MCP Server
Expose Genkit actions (tools, prompts, resources) over MCP.

```dart
import 'package:genkit/genkit.dart';
import 'package:genkit_mcp/genkit_mcp.dart';

void main() async {
  final ai = Genkit();

  ai.defineTool(
    name: 'add',
    description: 'Add two numbers together',
    inputSchema: .map(.string(), .dynamicSChema()),
    fn: (input, _) async => (input['a'] + input['b']).toString(),
  );

  ai.defineResource(
    name: 'my-resource',
    uri: 'my://resource',
    fn: (_, _) async => ResourceOutput(content: [TextPart(text: 'my resource')]),
  );

  // Stdio transport by default
  final server = createMcpServer(ai, McpServerOptions(name: 'my-server'));
  await server.start();
}
```

### Streamable HTTP Transport
```dart
import 'dart:io';

final transport = await StreamableHttpServerTransport.bind(
  address: InternetAddress.loopbackIPv4,
  port: 3000,
);
await server.start(transport);
```
