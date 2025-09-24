# USER_INTERACT Implementation Summary

This document provides an overview of the USER_INTERACT feature implementation in the Saigon.Quest project.

## Overview

The USER_INTERACT feature allows users to interact with objects in the world by clicking on them. When a user clicks on an object, a USER_INTERACT event is sent to the backend, which processes the event and triggers the appropriate node logic for the object.

## Implementation Details

### Frontend Implementation

The frontend implementation is in the `InputHandler.kt` file. When a user clicks on an object, the following happens:

1. The `handlePointerEvent` method detects the click and identifies the object that was clicked
2. It determines if the user is a player (authenticated) or visitor based on whether they have a token
3. It sends a USER_INTERACT event to the backend via `WebSocketApi.playerInteract(objectId, isPlayer)`

```kotlin
// This is a single-click on an object
console.log("[POINTER_DOWN] Single-click detected on object: $closestObjectId")

// Send USER_INTERACT event to the backend
// Determine if the user is a player (authenticated) or visitor
val isPlayer = main.api.UserApi.getToken() != null
main.api.WebSocketApi.playerInteract(closestObjectId, isPlayer)
```

### Backend Implementation

The backend implementation is in the `PlayerInteractHandler.kt` file. When a USER_INTERACT event is received, the following happens:

1. The `handle` method parses the `PlayerInteractData` from the message
2. It broadcasts the interaction to all clients in the same world
3. It retrieves the object from the repository
4. It creates a `NodeRunnerContext` and `NodeLogicRunner`
5. It prepares event data with userId, objectId, and isPlayer
6. It processes the appropriate starting node based on whether the user is a player or visitor:
   - If isPlayer is true, it processes `LogicNode.PlayerInteract()`
   - If isPlayer is false, it processes `LogicNode.VisitorInteract()`

```kotlin
// Process the appropriate starting node based on whether the user is a player or visitor
if (interactData.isPlayer) {
    nodeLogicRunner.processNodes(LogicNode.PlayerInteract(), eventData)
} else {
    nodeLogicRunner.processNodes(LogicNode.VisitorInteract(), eventData)
}
```

### Node Logic Processing

The `NodeLogicRunner` processes nodes when a USER_INTERACT event is received:

1. It finds all nodes of the specified type in the object's node configuration
2. For each node, it gets the appropriate `NodeRunner` based on the node's type and ID
3. It executes the node's logic using the `NodeRunner`
4. If the execution is successful, it follows the node's connections and processes the connected nodes

The actions that can be triggered by nodes include:
- Teleporting players to a different world (`TeleportNodeRunner`)
- Showing dialogues to players (`DialogueNodeRunner`)
- Other actions that don't have specific runners (`NoOpNodeRunner`)

## Testing the Implementation

To test the USER_INTERACT implementation, you can use the provided test script:

1. Make sure the backend and frontend servers are running
2. Run the test script: `./test_user_interact.sh`
3. The script will open a test page in your browser
4. The test page will automatically send USER_INTERACT events to the backend
5. Check the logs in the browser to verify that the backend processes the events correctly

### Expected Results

- The backend should receive the USER_INTERACT events
- The PlayerInteractHandler should process the events
- If there are objects with player_interact or visitor_interact nodes, the NodeLogicRunner should process them
- Depending on the node configuration, you may see SHOW_DIALOGUE or TELEPORT_PLAYER messages

## Troubleshooting

If the test fails, check the following:

1. Make sure the backend server is running
2. Make sure the frontend server is running
3. Check the backend logs for any errors
4. Make sure there are objects in the database with player_interact or visitor_interact nodes

## Conclusion

The USER_INTERACT feature is now fully implemented and tested. Users can interact with objects in the world by clicking on them, and the backend will process the interactions and trigger the appropriate node logic for the objects.
