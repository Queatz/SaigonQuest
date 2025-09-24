# WebSocket Debug Utility Documentation

## Overview

The WebSocketDebug utility provides enhanced debugging capabilities for WebSocket connections in the Saigon.Quest application. It offers structured logging for WebSocket connections, messages, and errors, making it easier to diagnose issues with WebSocket communication.

## Features

- Detailed logging of WebSocket connection events
- Structured logging of WebSocket messages with content analysis
- Comprehensive error logging with stack traces
- Configuration options to enable/disable logging
- Support for both environment variable and programmatic control

## Implementation

The WebSocketDebug utility consists of two main components:

1. **Server-side debugging (WebSocketDebug.kt)**: A Kotlin utility class that provides structured logging for WebSocket operations on the server.
2. **Client-side debugging (websocket_debug.js)**: A JavaScript utility that overrides the WebSocket constructor to add logging on the client side.

## Usage

### Enabling WebSocket Debug Logging

WebSocket debug logging can be enabled in two ways:

1. **Environment Variable**: Set the `WEBSOCKET_DEBUG` environment variable to `true` before starting the application:

   ```bash
   export WEBSOCKET_DEBUG=true
   ./gradlew run
   ```

2. **Programmatically**: Call the `setEnabled` method in your code:

   ```kotlin
   // Enable WebSocket debug logging
   WebSocketDebug.setEnabled(true)

   // Disable WebSocket debug logging
   WebSocketDebug.setEnabled(false)
   ```

### Server-Side Logging Methods

The WebSocketDebug utility provides three main logging methods:

1. **logConnection**: Logs WebSocket connection events (connect, disconnect)

   ```kotlin
   WebSocketDebug.logConnection("CONNECT", session, worldId, userId)
   ```

2. **logMessage**: Logs WebSocket messages with content analysis

   ```kotlin
   WebSocketDebug.logMessage("RECEIVED", messageText, session)
   ```

3. **logError**: Logs WebSocket errors with stack traces

   ```kotlin
   WebSocketDebug.logError("Error processing message", exception, session)
   ```

### Client-Side Debugging

The client-side debugging script (`websocket_debug.js`) can be included in your HTML to enable client-side WebSocket debugging:

```html
<script src="websocket_debug.js"></script>
```

This script will automatically log all WebSocket operations in the browser console.

## Integration with WebSocketRoutes

The WebSocketDebug utility has been integrated with the WebSocketRoutes.kt file to provide comprehensive logging of WebSocket operations. The integration includes:

1. Logging connection events when clients connect or disconnect
2. Logging all received messages with content analysis
3. Logging errors that occur during message processing

## Example Output

### Connection Logging

```
=== WEBSOCKET DEBUG [Connection #1] ===
Event: CONNECT
Session: DefaultWebSocketServerSession@1a2b3c4d
World ID: world-123
User ID: user-456
Remote Address: 192.168.1.100
User Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36
=== END WEBSOCKET DEBUG [Connection #1] ===
```

### Message Logging

```
=== WEBSOCKET DEBUG [Message #1] ===
Direction: RECEIVED
Session: DefaultWebSocketServerSession@1a2b3c4d
Raw Message: {"action":"SEND_CHAT_MESSAGE","data":"{\"worldId\":\"world-123\",\"userId\":\"user-456\",\"username\":\"John\",\"message\":\"Hello, world!\",\"timestamp\":1625123456789}"}
Parsed Message:
  Action: SEND_CHAT_MESSAGE
  Data Length: 123
  Message Type: Chat Message
  Chat Message Data: {
    "worldId": "world-123",
    "userId": "user-456",
    "username": "John",
    "message": "Hello, world!",
    "timestamp": 1625123456789
  }
=== END WEBSOCKET DEBUG [Message #1] ===
```

### Error Logging

```
=== WEBSOCKET DEBUG [ERROR] ===
Error: Error processing WebSocket message
Session: DefaultWebSocketServerSession@1a2b3c4d
Exception: java.lang.IllegalArgumentException
Message: Invalid message format
Stack Trace:
  at routes.WebSocketRoutes$configureWebSocketRoutes$1$1$1.invokeSuspend(WebSocketRoutes.kt:200)
  at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
  at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:106)
  at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:570)
  at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:750)
  at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:677)
  at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:664)
=== END WEBSOCKET DEBUG [ERROR] ===
```

## Recommendations

1. **Use in Development**: Enable WebSocketDebug during development to diagnose WebSocket issues.
2. **Disable in Production**: Disable WebSocketDebug in production to avoid performance overhead and log clutter.
3. **Selective Logging**: Consider adding more granular control over which types of messages are logged.
4. **Log Rotation**: Implement log rotation for WebSocketDebug logs to prevent log files from growing too large.
5. **Integration with Monitoring**: Consider integrating WebSocketDebug with a monitoring system for real-time alerts on WebSocket issues.

## Future Improvements

1. **Log to File**: Add option to log to a separate file instead of standard output.
2. **Filtering**: Add filtering options to focus on specific types of messages or connections.
3. **Performance Metrics**: Add performance metrics for WebSocket operations.
4. **Web Interface**: Create a web interface for viewing WebSocket debug logs in real-time.
5. **Message Replay**: Add ability to replay WebSocket messages for testing purposes.
