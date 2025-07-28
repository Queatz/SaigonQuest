package main.api

/**
 * API client for user operations
 * 
 * This file uses Ktor HttpClient for making HTTP requests to the backend API.
 * It handles user authentication, registration, and profile management.
 * 
 * Key features:
 * - Uses Ktor HttpClient with ContentNegotiation for JSON serialization
 * - Converts between suspend functions and Promises using kotlinx.coroutines.promise
 * - Manages authentication tokens and visitor IDs in localStorage
 * - Provides error handling with detailed error messages
 * 
 * Note: This implementation replaces the previous window.fetch implementation
 * with a more idiomatic Kotlin approach using Ktor.
 */

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import models.User
import kotlin.js.Promise
import main.api.ApiConstants

// Data classes for request/response (matching backend)
@Serializable
data class VisitorRequest(val visitorId: String = "")

@Serializable
data class UpgradeRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val user: User, val token: String)

/**
 * API client for user operations
 */
object UserApi {
    private const val TOKEN_STORAGE_KEY = "auth_token"
    private const val VISITOR_ID_STORAGE_KEY = "visitor_id"
    
    private val scope = MainScope()
    
    // Create a Ktor HttpClient with JSON serialization
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    /**
     * Gets the current user from local storage or creates a new visitor
     */
    fun getCurrentUser(): Promise<User> {
        // Check if we have a token
        val token = getToken()
        
        return if (token != null) {
            // We have a token, get the current user
            fetchCurrentUser()
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
        }
    }
    
    /**
     * Creates a new visitor or gets an existing one
     */
    fun createOrGetVisitor(visitorId: String): Promise<User> {
        val request = VisitorRequest(visitorId)
        
        return scope.promise {
            try {
                val response = client.post("${ApiConstants.API_BASE_URL}/user/visitor") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(request)
                }
                
                val authResponse: AuthResponse = response.body()

                // Save token and visitor ID
                saveToken(authResponse.token)
                saveVisitorId(authResponse.user.visitorId)

                authResponse.user
            } catch (e: Exception) {
                println("Error creating visitor: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Upgrades a visitor to a player
     */
    fun upgradeToPlayer(username: String, password: String): Promise<User> {
        val request = UpgradeRequest(username, password)
        
        return scope.promise {
            try {
                val response = client.post("${ApiConstants.API_BASE_URL}/user/upgrade") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(getToken() ?: "")
                    setBody(request)
                }
                
                if (response.status.isSuccess()) {
                    val authResponse: AuthResponse = response.body()
                    
                    // Save new token
                    saveToken(authResponse.token)
                    
                    authResponse.user
                } else {
                    throw Error(response.bodyAsText())
                }
            } catch (e: Exception) {
                println("Error upgrading to player: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Logs in a user with username and password
     */
    fun login(username: String, password: String): Promise<User> {
        val request = LoginRequest(username, password)
        
        return scope.promise {
            try {
                val response = client.post("${ApiConstants.API_BASE_URL}/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                
                if (response.status.isSuccess()) {
                    val authResponse: AuthResponse = response.body()
                    
                    // Save token
                    saveToken(authResponse.token)
                    
                    authResponse.user
                } else {
                    throw Error(response.bodyAsText())
                }
            } catch (e: Exception) {
                println("Error logging in: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Fetches the current user
     */
    fun fetchCurrentUser(): Promise<User> {
        return scope.promise {
            try {
                val response = client.get("${ApiConstants.API_BASE_URL}/user/me") {
                    bearerAuth(getToken() ?: "")
                }
                
                if (response.status.isSuccess()) {
                    response.body<User>()
                } else {
                    // Token is invalid, clear it
                    clearToken()
                    
                    // Create or get visitor instead
                    val visitorId = getVisitorId()
                    if (visitorId != null) {
                        createOrGetVisitorSuspend(visitorId)
                    } else {
                        createOrGetVisitorSuspend("")
                    }
                }
            } catch (e: Exception) {
                println("Error fetching current user: ${e.message}")
                
                // Handle network errors by creating a visitor
                clearToken()
                
                val visitorId = getVisitorId()
                if (visitorId != null) {
                    createOrGetVisitorSuspend(visitorId)
                } else {
                    createOrGetVisitorSuspend("")
                }
            }
        }
    }
    
    /**
     * Suspend version of createOrGetVisitor for internal use
     */
    private suspend fun createOrGetVisitorSuspend(visitorId: String): User {
        val request = VisitorRequest(visitorId)
        
        val response = client.post("${ApiConstants.API_BASE_URL}/user/visitor") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
        }
        
        // Check if response has a content type
        if (response.contentType() == null) {
            // Handle missing content type by parsing the response body as text first
            val responseText = response.bodyAsText()
            try {
                // Try to parse the text as JSON with a properly configured Json instance
                val json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
                val authResponse = json.decodeFromString<AuthResponse>(responseText)
                
                // Save token and visitor ID
                saveToken(authResponse.token)
                saveVisitorId(authResponse.user.visitorId)
                
                return authResponse.user
            } catch (e: Exception) {
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
    private fun getVisitorId(): String? {
        return localStorage.getItem(VISITOR_ID_STORAGE_KEY)
    }
    
    /**
     * Saves the visitor ID to local storage
     */
    private fun saveVisitorId(visitorId: String) {
        localStorage.setItem(VISITOR_ID_STORAGE_KEY, visitorId)
    }
}
