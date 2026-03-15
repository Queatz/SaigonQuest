package main.api

/**
 * API client for user operations
 * 
 * This file uses Ktor HttpClient for making HTTP requests to the backend API.
 * It handles user authentication, registration, and profile management.
 * 
 * Key features:
 * - Uses shared ApiClient for HttpClient and Json configuration
 * - Uses Kotlin coroutines for asynchronous operations
 * - Manages authentication tokens and visitor IDs in localStorage
 * - Provides error handling with detailed error messages
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import main.components.LanguageSettings
import models.AuthResponse
import models.ChangePasswordRequest
import models.LanguageUpdateRequest
import models.LoginRequest
import models.Sex
import models.StatusUpdateRequest
import models.UpgradeRequest
import models.User
import models.VisitorRequest
import models.WornItemsRequest
import models.UserCustomizationRequest
import models.StatusBarsUpdateRequest


/**
 * API client for user operations
 */
class UnauthorizedHttpError(message: String = "Unauthorized") : Throwable(message)

object UserApi {
    private const val TOKEN_STORAGE_KEY = "auth_token"
    private const val VISITOR_ID_STORAGE_KEY = "visitor_id"

    private var user: User? by mutableStateOf(null)

    /**
     * Returns true if the current cached user is a logged-in player.
     * This is a pure, synchronous check that defaults to false when unknown.
     */
    @Composable
    fun isLoggedInAsPlayer(): Boolean {
        return user?.isPlayer == true
    }

    /**
     * Gets the current user from local storage or creates a new visitor
     */
    suspend fun getCurrentUser(forceReload: Boolean = false): User {
        if (!forceReload && user != null) {
            return user!!
        }

        // Check if we have a token
        val token = getToken()
        
        return if (token != null) {
            // We have a token, get the current user
            try {
                fetchCurrentUser()
            } catch (e: Throwable) {
                // Only fall back to visitor if we are explicitly unauthorized
                if (e is UnauthorizedHttpError) {
                    val visitorId = getVisitorId()
                    if (visitorId != null) {
                        createOrGetVisitor(visitorId)
                    } else {
                        createOrGetVisitor("")
                    }
                } else {
                    // Keep the user logged in: return cached user if available, otherwise rethrow
                    user ?: throw e
                }
            }
        } else {
            // No token, check if we have a visitor ID
            val visitorId = getVisitorId()
            
            if (visitorId != null) {
                // We have a visitor ID, get or create the visitor
                createOrGetVisitor(visitorId)
            } else {
                // No visitor ID, create a new visitor
                createOrGetVisitor("")
            }
        }.also {
            user = it
        }
    }
    
    /**
     * Creates a new visitor or gets an existing one
     */
    suspend fun createOrGetVisitor(visitorId: String): User {
        val request = VisitorRequest(visitorId)
        
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/visitor") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }
            
            // Check if response has a content type
            if (response.contentType() == null) {
                // Handle missing content type by parsing the response body as text first
                val responseText = response.bodyAsText()
                try {
                    // Try to parse the text as JSON
                    val authResponse = ApiClient.json.decodeFromString<AuthResponse>(responseText)
                    
                    // Save token and visitor ID
                    saveToken(authResponse.token)
                    saveVisitorId(authResponse.user.visitorId)
                    
                    return authResponse.user
                } catch (e: Throwable) {
                    println("Error parsing response text: ${e.message}")
                    throw e
                }
            } else {
                // Normal flow when content type is present
                val authResponse: AuthResponse = response.body()
                
                // Save token and visitor ID
                saveToken(authResponse.token)
                saveVisitorId(authResponse.user.visitorId)
                
                return authResponse.user
            }
        } catch (e: Throwable) {
            println("Error creating visitor: ${e.message}")
            throw e
        }
    }
    
    /**
     * Upgrades a visitor to a player
     */
    suspend fun upgradeToPlayer(
        username: String,
        password: String,
        yearOfBirth: Int? = null,
        sex: Sex? = null
    ): User {
        // Get the current language from settings
        val language = LanguageSettings.getCurrentLanguage()
        
        val request = UpgradeRequest(username, password, yearOfBirth, sex, language)
        
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/upgrade") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                
                // Save new token
                saveToken(authResponse.token)
                
                // Update cached current user to the newly registered player
                user = authResponse.user
                
                return authResponse.user
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            println("Error upgrading to player: ${e.message}")
            throw e
        }
    }
    
    /**
     * Logs in a user with username and password
     */
    suspend fun login(username: String, password: String): User {
        val request = LoginRequest(username, password)
        
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                
                // Save token
                saveToken(authResponse.token)
                
                // Update cached current user to the newly authenticated player
                user = authResponse.user
                
                return authResponse.user
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            println("Error logging in: ${e.message}")
            throw e
        }
    }
    
    /**
     * Fetches the current user
     */
    suspend fun fetchCurrentUser(): User {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/user/me") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<User>()
            } else {
                // Only clear token when the server definitively says it's unauthorized
                if (response.status.value == 401) {
                    clearToken()
                    throw UnauthorizedHttpError()
                }
                // For other errors (e.g., during server restarts or 5xx), keep token and propagate
                throw Error("Failed to fetch current user: ${response.status.value}")
            }
        } catch (e: Throwable) {
            println("Error fetching current user: ${e.message}")
            // Do not clear token on transient errors (e.g., network/server restart)
            throw e
        }
    }
    
    /**
     * Logs out the current user
     */
    fun logout() {
        clearToken()
    }
    
    /**
     * Gets the token from local storage
     */
    fun getToken(): String? {
        return localStorage.getItem(TOKEN_STORAGE_KEY)
    }
    
    /**
     * Saves the token to local storage
     */
    private fun saveToken(token: String) {
        localStorage.setItem(TOKEN_STORAGE_KEY, token)
    }
    
    /**
     * Clears the token from local storage
     */
    private fun clearToken() {
        localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
    
    /**
     * Gets the visitor ID from local storage
     */
    fun getVisitorId(): String? {
        return localStorage.getItem(VISITOR_ID_STORAGE_KEY)
    }
    
    /**
     * Saves the visitor ID to local storage
     */
    private fun saveVisitorId(visitorId: String) {
        localStorage.setItem(VISITOR_ID_STORAGE_KEY, visitorId)
    }
    
    /**
     * Updates user character customization
     */
    suspend fun updateUserCustomization(user: User): User {
        val request = UserCustomizationRequest(
            bodyType = user.bodyType,
            skinTone = user.skinTone,
            eyeColor = user.eyeColor,
            hairStyle = user.hairStyle
        )
        
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/customization") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val updatedUser = response.body<User>()
                // Update local cache
                this@UserApi.user = updatedUser
                // Notify others via WebSocket to refresh appearance immediately
                try {
                    WebSocketApi.updatePlayer(
                        name = updatedUser.username,
                        isPlayer = updatedUser.isPlayer,
                        activity = null,
                        status = null,
                    )
                } catch (_: Throwable) { }
                return updatedUser
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            console.error("Error updating user customization: ${e.message}")
            throw e
        }
    }
    
    /**
     * Updates user worn items
     */
    suspend fun updateUserWornItems(user: User): User {
        val request = WornItemsRequest(
            wornItems = user.wornItems
        )

        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/worn-items") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val updatedUser = response.body<User>()
                // Update local cache
                this@UserApi.user = updatedUser
                // Notify others via WebSocket to refresh appearance immediately
                try {
                    WebSocketApi.updatePlayer(
                        name = updatedUser.username,
                        isPlayer = updatedUser.isPlayer,
                        activity = null,
                        status = null,
                    )
                } catch (_: Throwable) { }
                return updatedUser
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            console.error("Error updating user worn items: ${e.message}")
            throw e
        }
    }
    
    /**
     * Updates user status bars (health/mind/spirit)
     */
    suspend fun updateUserStatusBars(
        health: Int? = null,
        mind: Int? = null,
        spirit: Int? = null,
    ): User {
        val request = StatusBarsUpdateRequest(health = health, mind = mind, spirit = spirit)
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/status-bars") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                val updatedUser = response.body<User>()
                user = updatedUser
                return updatedUser
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            console.error("Error updating user status bars: ${e.message}")
            throw e
        }
    }
    
    /**
     * Updates user language preference
     */
    suspend fun updateUserLanguage(language: String): User {
        val request = LanguageUpdateRequest(language)
        
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/language") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                return response.body<User>()
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            console.error("Error updating user language: ${e.message}")
            throw e
        }
    }
    
    /**
     * Updates user status
     */
    suspend fun updateUserStatus(status: String): User {
        val request = StatusUpdateRequest(status)
        
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/status") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody<StatusUpdateRequest>(request)
            }
            
            if (response.status.isSuccess()) {
                val updatedUser = response.body<User>()
                // Update the local user object
                user = updatedUser
                return updatedUser
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            console.error("Error updating user status: ${e.message}")
            throw e
        }
    }

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
    /**
     * Changes the current user's password and refreshes the token
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): User {
        val request = ChangePasswordRequest(currentPassword, newPassword)

        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/change-password") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                // Save new token
                saveToken(authResponse.token)
                // Update cached user to returned player
                user = authResponse.user
                return authResponse.user
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Throwable) {
            console.error("Error changing password: ${e.message}")
            throw e
        }
    }
}
