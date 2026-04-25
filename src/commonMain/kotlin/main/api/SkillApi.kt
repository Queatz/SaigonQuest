package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import models.ErrorResponse
import models.Skill
import models.User

/**
 * API client for skill operations
 */
object SkillApi {
    
    /**
     * Gets all skills
     */
    suspend fun getAllSkills(): List<Skill> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/skills") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<List<Skill>>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error fetching skills", e)
            throw e
        }
    }
    
    /**
     * Gets a skill by its ID
     */
    suspend fun getSkillById(skillId: String): Skill {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/skills/$skillId") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Skill>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error fetching skill", e)
            throw e
        }
    }
    
    /**
     * Creates a new skill (admin)
     */
    suspend fun createSkill(skill: Skill): Skill {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/skills") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(skill)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Skill>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error creating skill", e)
            throw e
        }
    }
    
    /**
     * Updates an existing skill (admin)
     */
    suspend fun updateSkill(skillId: String, skill: Skill): Skill {
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/skills/$skillId") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(skill)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Skill>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error updating skill", e)
            throw e
        }
    }
    
    /**
     * Deletes a skill (admin)
     */
    suspend fun deleteSkill(skillId: String) {
        try {
            val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/skills/$skillId") {
                ApiClient.addAuthHeader(this)
            }
            
            if (!response.status.isSuccess()) {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error deleting skill", e)
            throw e
        }
    }
    
    /**
     * Gets users with a specific skill (admin)
     */
    suspend fun getUsersBySkill(skillId: String): List<User> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/skills/$skillId/users") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<List<User>>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error fetching users by skill", e)
            throw e
        }
    }
    
    /**
     * Manually sets a user's skill level (admin)
     */
    suspend fun setUserSkillLevel(userId: String, skillId: String, level: Int): User {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/skills/admin/user/$userId/$skillId/level") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(level)
            }
            
            if (response.status.isSuccess()) {
                return response.body<User>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error setting user skill level", e)
            throw e
        }
    }
}
