# Implementation Plan: Fetching User Objects for Other Players

## Problem Statement

Currently, other players in the game world are rendered without their worn items because the client doesn't have access to the full User objects for other players. The `OtherPlayersController.kt` file contains a TODO comment:

```kotlin
// TODO: Implement a proper solution for fetching User objects for other players
// For now, we can't display worn items for other players because we don't have their User objects
// A future improvement would be to fetch User objects for other players or extend WornItemsRenderer
// to support fetching worn items by userId
```

## Current Implementation

1. When a user joins a world, the `OtherPlayersController` receives a `UserJoinData` object via WebSocket, which contains:
   - `userId`: The ID of the user who joined
   - `username`: The username of the user
   - `isPlayer`: Whether the user is a player or visitor
   - `position`: The position of the user in the world
   - `worldId`: The ID of the world the user joined

2. The `OtherPlayersController` creates a character for the user using `WorldObjectManager.createCharacter()`, but doesn't pass a `User` object, resulting in the character being rendered without worn items.

3. The `WorldObjectManager.createCharacter()` method has been modified to only use worn items when a `User` object is explicitly provided, preventing other players from incorrectly using the current player's worn items.

## Proposed Solution

Add a new REST endpoint to the backend API that allows fetching User objects for other players, and modify the `OtherPlayersController` to fetch User objects for other players when they join the world.

### Backend Changes

1. Add a new endpoint to `UserRoutes.kt`:

```kotlin
// Get user by ID - requires authentication
authenticate {
    get("/{userId}") {
        // Get the requested user ID from the path parameter
        val userId = call.parameters["userId"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse(message = "User ID is required", code = HttpStatusCode.BadRequest.value)
        )
        
        // Get user
        val user = userRepository.getById(userId) ?: return@get call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
        )
        
        // Remove sensitive information
        user.passwordHash = null
        
        call.respond(HttpStatusCode.OK, user)
    }
}
```

### Frontend Changes

1. Add a new method to `UserApi.kt`:

```kotlin
/**
 * Fetches a user by their ID
 * 
 * @param userId The ID of the user to fetch
 * @return The user object, or null if not found
 */
suspend fun getUserById(userId: String): User? {
    return try {
        // Check if we have a token
        val token = getToken()
        if (token == null) {
            console.error("Cannot fetch user: No authentication token")
            return null
        }
        
        // Make the API request
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/user/$userId") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        
        // Parse the response
        if (response.status.isSuccess()) {
            response.body<User>()
        } else {
            console.error("Error fetching user: ${response.status}")
            null
        }
    } catch (e: Throwable) {
        console.error("Error fetching user", e)
        null
    }
}
```

2. Modify the `createPlayerMesh` method in `OtherPlayersController.kt` to fetch the User object for other players:

```kotlin
private fun createPlayerMesh(userId: String, position: Vector3, isPlayer: Boolean, username: String? = null) {
    console.log("[DEBUG_LOG] Creating player mesh for $userId with isPlayer=$isPlayer, username=$username")
    // Remove existing player if any, but preserve state when recreating
    removePlayerMesh(userId, preserveState = true)
    
    // Get the world object manager
    val manager = worldObjectManager
    if (manager == null) {
        console.error("OtherPlayersController: WorldObjectManager not set, cannot create character")
        return
    }
    
    // Create a sprite sheet for the player
    val spriteSheet = SpriteSheet.createPlayerSpriteSheet()
    
    // Default direction (south)
    val direction = CharacterDirection.S
    
    // Fetch the User object for this player
    scope.launch {
        try {
            val user = UserApi.getUserById(userId)
            
            // Create the player character using the WorldObjectManager
            val playerHandle = if (user != null) {
                console.log("[DEBUG_LOG] OtherPlayersController: Creating character for $userId with User object")
                manager.createCharacter(
                    spriteSheet = spriteSheet,
                    direction = direction,
                    position = position,
                    additionalSpriteSheets = emptyList(),
                    user = user
                )
            } else {
                console.log("[DEBUG_LOG] OtherPlayersController: Creating character for $userId without User object")
                manager.createCharacter(
                    spriteSheet = spriteSheet,
                    direction = direction,
                    position = position,
                    additionalSpriteSheets = emptyList()
                )
            }
            
            if (playerHandle == null) {
                console.error("OtherPlayersController: Failed to create character for $userId")
                return@launch
            }
            
            // Start with scale 0 for join animation
            playerHandle.scale = Vector3(0f, 0f, 0f)
            
            // Store the player handle
            playerHandles[userId] = playerHandle
            
            // Store the mesh for backward compatibility
            playerHandle.node.let { node ->
                playerNodes[userId] = node
            }
            
            // Store target position and animation time
            playerTargetPositions[userId] = position
            playerAnimationTimes[userId] = window.performance.now()
            
            // Configure shadow casting for the player if shadow manager is available
            configureShadowForPlayer(userId)
            
            // Create username text billboard
            createUsernameTextBillboard(userId, position, username)
        } catch (e: Throwable) {
            console.error("OtherPlayersController: Error fetching user data for $userId", e)
            
            // Create character without User object as fallback
            val playerHandle = manager.createCharacter(
                spriteSheet = spriteSheet,
                direction = direction,
                position = position,
                additionalSpriteSheets = emptyList()
            )
            
            // Continue with the rest of the character creation...
            // (Same code as above)
        }
    }
}

// Helper method to create username text billboard
private fun createUsernameTextBillboard(userId: String, position: Vector3, username: String?) {
    worldObjectManager?.let { manager ->
        try {
            // Calculate position above the player
            val textPosition = Vector3(position.x, position.y + .5f, position.z)
            
            // Use the provided username if available, otherwise use a default name
            val displayUsername = username ?: "Player-$userId"
            
            // Create the text billboard with the username
            val textHandle = manager.createTextBillboard(
                text = displayUsername,
                position = textPosition,
                fontSize = 18,
                textColor = "white"
            )
            
            // Store the text handle
            if (textHandle != null) {
                usernameTextHandles[userId] = textHandle
            }
        } catch (e: Throwable) {
            console.error("OtherPlayersController: Error creating username text for $userId", e)
        }
    }
}
```

## Security Considerations

1. The new endpoint should require authentication to prevent unauthorized access to user data.
2. Sensitive information (like password hashes) should be removed from the User object before sending it to the client.
3. Consider implementing rate limiting to prevent abuse of the endpoint.

## Performance Considerations

1. The client will need to make an additional HTTP request for each player that joins the world, which could impact performance if many players join at once.
2. Consider implementing caching on the client side to avoid fetching the same user data multiple times.
3. Consider batching requests for user data if multiple players join at once.

## Future Improvements

1. Extend the WebSocket API to include user data in the join/update events, eliminating the need for separate HTTP requests.
2. Implement a more efficient way to transmit worn items data, such as sending only the item IDs instead of the full User object.
3. Add support for updating worn items in real-time when a player changes their equipment.
