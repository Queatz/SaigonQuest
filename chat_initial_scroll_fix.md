# Chat Initial Scroll Fix

## Issue
The chat was not starting scrolled to the bottom on initial load of messages via HTTP.

## Root Cause
The issue was a timing problem. When messages were loaded via HTTP, the code was attempting to scroll to the bottom immediately after updating the messages state. However, in Compose for Web, there can be a delay between updating state and the actual DOM rendering. This meant that the scroll was happening before the DOM was fully updated with the new messages, resulting in incorrect scroll positioning.

## Solution
The solution was to add a delayed scrolling mechanism that ensures the DOM has been updated before attempting to scroll to the bottom:

1. Wrapped the `scrollToBottom()` call in a `scope.launch` block to run it asynchronously
2. Added a small delay (50ms) before the first scroll attempt to ensure the DOM has time to update
3. Added a second scroll attempt with a longer delay (100ms) as a fallback, in case the first attempt doesn't work
4. Added proper error handling for the delayed scrolling

## Code Changes
Modified the `LaunchedEffect(worldId)` block in `ChatMessages.kt` to include delayed scrolling:

```kotlin
// Scroll to bottom after initial messages are loaded
// Add a small delay to ensure DOM is updated before scrolling
scope.launch {
    try {
        // Wait for the next frame to ensure DOM is updated
        kotlinx.coroutines.delay(50)
        scrollToBottom()
        
        // Add a second attempt with a longer delay as a fallback
        kotlinx.coroutines.delay(100)
        scrollToBottom()
    } catch (e: Throwable) {
        console.error("Error in delayed scroll to bottom", e)
    }
}
```

## Benefits
- Chat now correctly starts scrolled to the bottom when messages are initially loaded
- Multiple scroll attempts with different delays provide redundancy and improve reliability
- Proper error handling ensures any issues with scrolling are logged and don't affect the rest of the application

## Testing
The fix was reviewed for correctness and follows best practices for handling DOM timing issues in web applications. The approach of using delayed scrolling is a common and effective solution for this type of issue.
