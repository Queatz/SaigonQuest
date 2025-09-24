# Scene.kt Refactoring Analysis

## Overview

This document provides an analysis of the challenges involved in refactoring `Scene.kt` to use coroutines instead of Promises, as part of the broader effort to eliminate Promise usage from the Saigon.Quest codebase.

## Current State

`Scene.kt` contains two main areas that use Promises:

1. **The onRegister callback in the VisitorDialog component (lines 222-270)**: This contains a complex nested Promise chain for handling user registration and token validation.

2. **The worldLoader.loadWorld call (lines 341-397)**: This uses a Promise chain to handle the success and failure cases of loading the world.

The file also contains an already-refactored `onLogin` callback that uses coroutines, but it has an issue with using `rememberCoroutineScope()` outside a @Composable function.

## Refactoring Challenges

### 1. Context for Suspend Functions

The main challenge is that `Scene.kt` calls suspend functions from non-coroutine contexts:

- `worldLoader.loadWorld(url)` is now a suspend function but is called directly, not from within a coroutine.
- The `UserApi` methods used in the `onRegister` callback are now suspend functions but are called as if they return Promises.

### 2. Coroutine Scope Management

Different parts of the file require different approaches to coroutine scope management:

- The `DisposableEffect` needs a coroutine scope that is tied to its lifecycle.
- The callbacks like `onRegister` need a coroutine scope that can be used within the callback.
- The `onLogin` callback is using `rememberCoroutineScope()`, which can only be called from a @Composable function.

### 3. State Updates

The Promise-based code updates state variables like `isLoaded`, `loadingFailed`, and `errorMessage`. When refactoring to coroutines, these state updates need to happen within the coroutine.

## Recommended Approach

### 1. For the worldLoader.loadWorld Call

```kotlin
// Create a coroutine scope for the DisposableEffect
val coroutineScope = CoroutineScope(Dispatchers.Main)

// Add the scope to the onDispose handler to ensure it's cancelled when the component is disposed
onDispose {
    coroutineScope.cancel()
    // ... other cleanup code ...
}

// Load the world using coroutines
coroutineScope.launch {
    try {
        // Call the suspend function
        val worldMeshes = worldLoader.loadWorld(url)
        
        // The rest of the code from the .then block
        currentWorld.value = worldLoader.getCurrentWorld()
        // ... other code ...
        isLoaded.value = true
    } catch (error: Throwable) {
        // The code from the .catch block
        console.error("Error loading world: ${error.message}")
        loadingFailed.value = true
        // ... other error handling ...
    }
}
```

### 2. For the onRegister Callback

The `onRegister` callback needs to be refactored to use a coroutine scope that is created within the callback, not using `rememberCoroutineScope()`:

```kotlin
onRegister = { username, password, yearOfBirth, sex ->
    // Create a coroutine scope for the callback
    val callbackScope = CoroutineScope(Dispatchers.Main)
    
    // Handle registration
    callbackScope.launch {
        try {
            // First ensure we have a visitor token by getting the current user
            try {
                val currentUser = UserApi.getCurrentUser()
                
                // Check if we have a token before proceeding
                val token = UserApi.getToken()
                if (token == null || token.isEmpty()) {
                    console.error("Registration error: No valid token available")
                    // Try to create a visitor again to get a token
                    try {
                        val visitor = UserApi.createOrGetVisitor("")
                        console.log("Created new visitor: ${visitor.visitorId}")
                        
                        // Add a small delay to ensure token is saved
                        delay(500) // 500ms delay
                        
                        try {
                            // Now upgrade the visitor to a player
                            val user = UserApi.upgradeToPlayer(username, password, yearOfBirth, sex)
                            console.log("User registered: ${user.username}")
                            showLoginDialog.value = false
                            // After successful registration, show the world edit panel
                            showWorldEditPanel.value = true
                        } catch (error: Throwable) {
                            console.error("Registration error after retry: ${error.message}")
                        }
                    } catch (error: Throwable) {
                        console.error("Error creating visitor: ${error.message}")
                    }
                } else {
                    // We have a token, proceed with upgrade
                    try {
                        val user = UserApi.upgradeToPlayer(username, password, yearOfBirth, sex)
                        console.log("User registered: ${user.username}")
                        showLoginDialog.value = false
                        // After successful registration, show the world edit panel
                        showWorldEditPanel.value = true
                    } catch (error: Throwable) {
                        console.error("Registration error: ${error.message}")
                    }
                }
            } catch (error: Throwable) {
                console.error("Error getting current user: ${error.message}")
            }
        } catch (error: Throwable) {
            console.error("Registration error: ${error.message}")
        }
    }
}
```

### 3. For the onLogin Callback

The `onLogin` callback is already using coroutines, but it's using `rememberCoroutineScope()` which can only be called from a @Composable function. It should be refactored to use a regular coroutine scope:

```kotlin
onLogin = { username, password ->
    // Create a coroutine scope for the callback
    val callbackScope = CoroutineScope(Dispatchers.Main)
    
    // Handle login
    callbackScope.launch {
        try {
            val user = UserApi.login(username, password)
            console.log("User logged in: ${user.username}")
            showLoginDialog.value = false
            // After successful login, show the world edit panel
            showWorldEditPanel.value = true
        } catch (error: Throwable) {
            console.error("Login error: ${error.message}")
        }
    }
}
```

## Additional Considerations

### 1. Imports

The following imports need to be added to `Scene.kt`:

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
```

### 2. Coroutine Scope Cancellation

It's important to ensure that all coroutine scopes are properly cancelled when they're no longer needed to avoid memory leaks:

- The coroutine scope for the `DisposableEffect` should be cancelled in the `onDispose` handler.
- The coroutine scopes for callbacks should be cancelled when the callback is no longer needed, if possible.

### 3. Error Handling

The refactored code should maintain the same error handling behavior as the original code:

- Errors should be caught and logged.
- State variables should be updated appropriately.
- User-friendly error messages should be displayed.

## Conclusion

Refactoring `Scene.kt` to use coroutines instead of Promises is a complex task that requires careful consideration of the context in which suspend functions are called and how coroutine scopes are managed. By following the recommended approach outlined in this document, the refactoring can be done in a way that maintains the same functionality while eliminating Promise usage from the codebase.
