# Promise to Coroutine Refactoring Guide

## Overview

This document outlines the remaining Promise usages in the Saigon.Quest codebase and provides guidance on how to refactor them to use Kotlin coroutines.

## Completed Refactorings

The following components have already been refactored to use coroutines:

1. **API Client Layer**
   - ApiClient.kt - Created a centralized client with shared HttpClient and Json instances
   - UserApi.kt - Converted Promise-returning functions to suspend functions
   - ObjectApi.kt - Converted Promise-returning functions to suspend functions
   - InventoryApi.kt - Converted Promise-returning functions to suspend functions
   - ChatApi.kt - Converted Promise-returning functions to suspend functions
   - WebSocketApi.kt - Replaced Promise-based callbacks with coroutine launches
   - WorldApi.kt - Converted Promise-returning functions to suspend functions

2. **World Loading**
   - WorldLoader.kt - Converted Promise-returning functions to suspend functions and used suspendCancellableCoroutine for callback-based APIs

## Remaining Promise Usages

Based on the search results, the following components still use Promises and need to be refactored:

### UI Components

1. **WorldEditPanel.kt**
   - Uses Promise.resolve(Unit) in several places
   - Has Promise-based logic for handling world editing

2. **LogicEditorDialog.kt**
   - Uses Promise.resolve(Unit) in several places
   - Has Promise-based logic for handling logic editing

3. **ObjectEditorDialog.kt**
   - Uses Promise.resolve(Unit) in several places
   - Has Promise-based logic for handling object editing

4. **WorldEditDialog.kt**
   - Uses Promise.resolve(Unit) in several places
   - Has Promise-based logic for handling world editing

5. **WorldUI.kt**
   - Uses Promise.resolve(Unit) in several places
   - Likely has more complex Promise-based logic

6. **ObjectsPanel.kt**
   - Uses Promise.resolve(Unit) in several places
   - Has Promise-based logic for handling object management

7. **InventoryDialog.kt**
   - Uses Promise.resolve(Unit) in several places
   - Has Promise-based logic for handling inventory management

## Refactoring Approach

### General Strategy

1. **Identify Promise Return Types**
   - Change functions that return Promise<T> to suspend functions that return T directly
   - For functions that return Promise<Unit>, change to suspend functions that return Unit

2. **Replace Promise.resolve(Unit) with Coroutine Scope**
   - Replace Promise.resolve(Unit) with coroutine scope launches
   - Use structured concurrency with proper scope management

3. **Convert Callback-Based APIs**
   - Use suspendCoroutine or suspendCancellableCoroutine to convert callback-based APIs to coroutines
   - Handle success and error callbacks by resuming the coroutine appropriately

4. **Update Error Handling**
   - Replace Promise-based error handling (.catch { ... }) with try-catch blocks
   - Ensure exceptions are properly propagated or handled

### UI Component Refactoring

UI components require special consideration because they often involve event handling and state management. Here's the approach for refactoring UI components:

1. **Identify Component Lifecycle**
   - Determine when coroutines should be launched and cancelled
   - Use rememberCoroutineScope() in Compose UI components to get a coroutine scope tied to the component's lifecycle

2. **Replace Promise.resolve(Unit) with LaunchedEffect or rememberCoroutineScope**
   - For one-time effects, use LaunchedEffect
   - For event handlers, use rememberCoroutineScope().launch { ... }

3. **Handle State Updates**
   - Use mutableStateOf and remember for state management
   - Update state within coroutines using proper synchronization if needed

4. **Manage Concurrency**
   - Use coroutine context elements like Job and SupervisorJob to manage concurrency
   - Cancel ongoing operations when appropriate

### Example: Converting a UI Event Handler

Before:
```kotlin
fun onSaveClick() {
    val promise = if (isEditing) {
        WorldApi.updateWorld(worldId, worldData)
    } else {
        WorldApi.createWorld(worldData)
    }
    
    promise
        .then { world ->
            onSaveComplete(world)
            closeDialog()
        }
        .catch { error ->
            showError(error.message ?: "Unknown error")
        }
}
```

After:
```kotlin
fun onSaveClick() {
    coroutineScope.launch {
        try {
            val world = if (isEditing) {
                WorldApi.updateWorld(worldId, worldData)
            } else {
                WorldApi.createWorld(worldData)
            }
            
            onSaveComplete(world)
            closeDialog()
        } catch (error: Throwable) {
            showError(error.message ?: "Unknown error")
        }
    }
}
```

## Next Steps

1. **Prioritize Components**
   - Start with simpler components like dialogs
   - Leave complex components like WorldUI.kt for later

2. **Update Tests**
   - Ensure tests are updated to use coroutines
   - Use runTest for testing suspend functions

3. **Verify Functionality**
   - Test each refactored component thoroughly
   - Ensure error handling works correctly

4. **Document Changes**
   - Update documentation to reflect the new coroutine-based approach
   - Provide examples for common patterns

## Conclusion

Refactoring from Promises to coroutines will make the codebase more idiomatic Kotlin and easier to maintain. The structured concurrency model of coroutines provides better error handling and cancellation support compared to Promises.

By following this guide, the remaining Promise usages in the codebase can be systematically refactored to use coroutines, completing the transition to a fully coroutine-based asynchronous programming model.
