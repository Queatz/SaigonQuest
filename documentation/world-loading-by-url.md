# World Loading by URL Feature Documentation

## Overview

The World Loading by URL feature allows users to navigate to specific worlds in Saigon.Quest by using URLs. For example, a user can visit `saigon.quest/my-world` to load the world with the URL "my-world". If the world doesn't exist, the user is given the option to create it (after logging in if necessary).

## Implementation Details

### 1. Backend Changes

#### WorldRepository.kt
- Added a `getByUrl` method to find worlds by URL in the database:
```kotlin
fun getByUrl(url: String): World? {
    val query = "FOR w IN @@collection FILTER w.url == @url RETURN w"
    val bindVars = mapOf(
        "@collection" to CollectionsConfig.WORLDS_COLLECTION,
        "url" to url
    )
    
    val results = ArangoConfig.db.query(query, World::class.java, bindVars).toList()
    return results.firstOrNull()
}
```

#### WorldRoutes.kt
- Added a `/world/url` endpoint to get a world by URL:
```kotlin
// Get world by URL - no authentication required
get("/url") {
    val url = call.request.queryParameters["url"] ?: ""
    
    val world = worldRepository.getByUrl(url)
        ?: return@get call.respond(HttpStatusCode.NotFound)
        
    call.respond(world)
}
```

#### Application.kt
- Added CORS configuration to allow cross-origin requests:
```kotlin
// Install CORS
install(CORS) {
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Patch)
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Accept)
    // Allow requests from any origin
    anyHost()
}
```

#### UserRoutes.kt
- Modified the `/user/upgrade` endpoint to require authentication:
```kotlin
// Upgrade visitor to player
authenticate {
    post("/upgrade") {
        // Implementation...
    }
}
```

### 2. Frontend Changes

#### WorldApi.kt
- Added a `getWorldByUrl` method to call the new endpoint:
```kotlin
fun getWorldByUrl(url: String): Promise<WorldModel> {
    return scope.promise {
        try {
            val response = client.get("${ApiConstants.API_BASE_URL}/world/url") {
                url {
                    parameters.append("url", url)
                }
            }
            
            if (response.status.isSuccess()) {
                response.body<WorldModel>()
            } else if (response.status == HttpStatusCode.NotFound) {
                throw Error("World not found")
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            console.error("Error fetching world by URL", e)
            throw e
        }
    }
}
```

#### Main.kt
- Updated the routing system to capture all URLs and pass them to SceneComponent:
```kotlin
// Home route (empty URL)
route("/") {
    // Render the 3D scene with empty URL (Home World)
    SceneComponent(url = "")
}

// Catch-all route for any other path
noMatch {
    // Get the current path from the window.location.pathname
    val path = kotlinx.browser.window.location.pathname
    val url = if (path.startsWith("/")) path.substring(1) else path
    
    // Render the 3D scene with the URL from the path
    SceneComponent(url = url)
}
```

#### Scene.kt
- Modified SceneComponent to accept a URL parameter and load worlds by URL:
```kotlin
@Composable
fun SceneComponent(url: String) {
    // Implementation...
    
    // Load the world data from the API based on the URL
    WorldApi.getWorldByUrl(url)
        .then { world ->
            // Store the world data
            currentWorld.value = world
            
            // Connect to WebSocket for real-time updates
            WebSocketApi.connect(url)
            
            // Determine the file to load
            val worldFile = if (world.file.isNotEmpty()) "${world.file}.glb" else "world.glb"
            
            // Load the 3D model...
        }
        .catch { error ->
            console.error("Error loading world: ${error.message}")
            loadingFailed.value = true
        }
}
```

- Added error handling for when a world is not found:
```kotlin
if (loadingFailed.value) {
    // Error message when world not found
    H2 {
        Text("No world found at saigon.quest/${if (url.isNotEmpty()) url else ""}")
    }
    
    // Create this world or Login button
    Button(
        attrs = {
            // Button styling...
            onClick { 
                // Check if user is logged in
                if (UserApi.getToken() != null) {
                    // User is logged in, show world edit panel
                    showWorldEditPanel.value = true
                } else {
                    // User is not logged in, show login dialog
                    showLoginDialog.value = true
                }
            }
        }
    ) {
        // Show different text based on login status
        Text(if (UserApi.getToken() != null) "[ Create this world ]" else "[ Login ]")
    }
    
    // Go back button
    Button(
        attrs = {
            // Button styling...
            onClick { 
                router.navigate("/")
            }
        }
    ) {
        Text("[ Go back ]")
    }
}
```

- Added login dialog and registration handling:
```kotlin
// Show login dialog if requested
if (showLoginDialog.value) {
    VisitorDialog(
        onClose = { 
            showLoginDialog.value = false 
        },
        onRegister = { username, password ->
            // Handle registration
            // First ensure we have a visitor token by getting the current user
            UserApi.getCurrentUser()
                .then { _ ->
                    // Now upgrade the visitor to a player
                    UserApi.upgradeToPlayer(username, password)
                        .then { user ->
                            console.log("User registered: ${user.username}")
                            showLoginDialog.value = false
                            // After successful registration, show the world edit panel
                            showWorldEditPanel.value = true
                        }
                        .catch { error ->
                            console.error("Registration error: ${error.message}")
                        }
                }
                .catch { error ->
                    console.error("Error getting current user: ${error.message}")
                }
        },
        onLogin = { username, password ->
            // Handle login
            UserApi.login(username, password)
                .then { user ->
                    console.log("User logged in: ${user.username}")
                    showLoginDialog.value = false
                    // After successful login, show the world edit panel
                    showWorldEditPanel.value = true
                }
                .catch { error ->
                    console.error("Login error: ${error.message}")
                }
        }
    )
}
```

- Modified the overlay condition to hide it when the world edit panel or login dialog is shown:
```kotlin
// Loading screen or error message
if (!isLoaded.value && !showWorldEditPanel.value && !showLoginDialog.value) {
    // Overlay content...
}
```

## User Flow

1. User navigates to a URL (e.g., `saigon.quest/my-world`)
2. The application attempts to load the world with the URL "my-world"
3. If the world exists:
   - The 3D model is loaded
   - The user can interact with the world
4. If the world doesn't exist:
   - An error message is shown: "No world found at saigon.quest/my-world"
   - The user is given two options:
     - Create this world (or Login if not logged in)
     - Go back to the home page
5. If the user clicks "Login":
   - A login dialog is shown
   - The user can log in or register
   - After successful login/registration, the world edit panel is shown
6. If the user clicks "Create this world" (when logged in):
   - The world edit panel is shown
   - The user can create a new world
   - After saving, the user is redirected to the new world

## Recommendations for Future Improvements

1. **Client-side Caching**: Implement caching of world data to reduce API calls and improve loading times for frequently visited worlds.

2. **Progressive Loading**: Implement progressive loading of 3D models to improve the initial load time, especially for large worlds.

3. **Preloading**: Preload adjacent or connected worlds in the background to enable seamless transitions between worlds.

4. **Error Recovery**: Enhance error handling to automatically retry failed API calls or model loading with exponential backoff.

5. **URL Validation**: Add validation for URLs to ensure they follow a consistent format and don't contain special characters that could cause issues.

6. **SEO Optimization**: Implement server-side rendering or static generation for world metadata to improve search engine visibility.

7. **Deep Linking**: Enhance the routing system to support deep linking to specific locations or objects within a world.

8. **Offline Support**: Implement service workers to enable basic offline functionality, allowing users to access previously visited worlds without an internet connection.

9. **Analytics**: Add analytics to track which worlds are most popular and how users navigate between them.

10. **Performance Monitoring**: Implement performance monitoring to identify and address bottlenecks in the world loading process.

## Conclusion

The World Loading by URL feature provides a seamless way for users to navigate to specific worlds in Saigon.Quest. The implementation handles all the necessary edge cases, including authentication, error handling, and user feedback. With the recommended future improvements, the feature can be further enhanced to provide an even better user experience.
