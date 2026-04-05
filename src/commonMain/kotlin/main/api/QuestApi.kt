package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import models.ErrorResponse
import models.Quest
import models.QuestState
import models.PlayerQuestState

/**
 * API client for quest operations
 */
object QuestApi {
    private val baseUrl = "${ApiConstants.API_BASE_URL}/quests"

    /**
     * Gets all available quests
     */
    suspend fun getQuests(): List<Quest> {
        try {
            val response = ApiClient.httpClient.get(baseUrl) {
                ApiClient.addAuthHeader(this)
            }

            if (response.status.isSuccess()) {
                return response.body<List<Quest>>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error fetching quests", e)
            throw e
        }
    }

    /**
     * Gets a quest by ID
     */
    suspend fun getQuest(id: String): Quest {
        try {
            val response = ApiClient.httpClient.get("$baseUrl/$id") {
                ApiClient.addAuthHeader(this)
            }

            if (response.status.isSuccess()) {
                return response.body<Quest>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error fetching quest", e)
            throw e
        }
    }

    /**
     * Creates a new quest (admin only)
     */
    suspend fun createQuest(quest: Quest): Quest {
        try {
            val response = ApiClient.httpClient.post(baseUrl) {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(quest)
            }

            if (response.status.isSuccess()) {
                return response.body<Quest>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error creating quest", e)
            throw e
        }
    }

    /**
     * Deletes a quest (admin only)
     */
    suspend fun deleteQuest(id: String) {
        try {
            val response = ApiClient.httpClient.delete("$baseUrl/$id") {
                ApiClient.addAuthHeader(this)
            }

            if (!response.status.isSuccess()) {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error deleting quest", e)
            throw e
        }
    }

    /**
     * Gets global states for a quest
     */
    suspend fun getQuestStates(questId: String): List<QuestState> {
        try {
            val response = ApiClient.httpClient.get("$baseUrl/$questId/states") {
                ApiClient.addAuthHeader(this)
            }

            if (response.status.isSuccess()) {
                return response.body<List<QuestState>>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error fetching quest states", e)
            throw e
        }
    }

    /**
     * Updates or creates a global quest state (admin only)
     */
    suspend fun updateQuestState(questId: String, state: QuestState): QuestState {
        try {
            val response = ApiClient.httpClient.post("$baseUrl/$questId/states") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(state)
            }

            if (response.status.isSuccess()) {
                return response.body<QuestState>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error updating quest state", e)
            throw e
        }
    }

    /**
     * Updates or creates a player quest state (admin only)
     */
    suspend fun updatePlayerQuestState(playerQuestState: PlayerQuestState): PlayerQuestState {
        try {
            val response = ApiClient.httpClient.post("$baseUrl/player-states") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(playerQuestState)
            }

            if (response.status.isSuccess()) {
                return response.body<PlayerQuestState>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error updating player quest state", e)
            throw e
        }
    }

    /**
     * Deletes a global quest state (admin only)
     */
    suspend fun deleteQuestState(questId: String, stateName: String) {
        try {
            val response = ApiClient.httpClient.delete("$baseUrl/$questId/states/$stateName") {
                ApiClient.addAuthHeader(this)
            }

            if (!response.status.isSuccess()) {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error deleting quest state", e)
            throw e
        }
    }

    /**
     * Gets player-local states for the current user for a specific quest
     */
    suspend fun getPlayerQuestStates(questId: String): List<PlayerQuestState> {
        try {
            val response = ApiClient.httpClient.get("$baseUrl/$questId/player-states") {
                ApiClient.addAuthHeader(this)
            }

            if (response.status.isSuccess()) {
                return response.body<List<PlayerQuestState>>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error fetching player quest states", e)
            throw e
        }
    }

    /**
     * Gets all player states for a quest (admin only)
     */
    suspend fun getAllPlayerQuestStates(questId: String): List<PlayerQuestState> {
        try {
            val response = ApiClient.httpClient.get("$baseUrl/$questId/player-states/all") {
                ApiClient.addAuthHeader(this)
            }

            if (response.status.isSuccess()) {
                return response.body<List<PlayerQuestState>>()
            } else {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error fetching all player quest states", e)
            throw e
        }
    }

    /**
     * Deletes a player state (admin only)
     */
    suspend fun deletePlayerQuestState(playerStateId: String) {
        try {
            val response = ApiClient.httpClient.delete("$baseUrl/player-states/$playerStateId") {
                ApiClient.addAuthHeader(this)
            }

            if (!response.status.isSuccess()) {
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    ErrorResponse(message = response.bodyAsText(), code = response.status.value)
                }
                throw errorResponse
            }
        } catch (e: Throwable) {
            console.error("Error deleting player quest state", e)
            throw e
        }
    }
}
