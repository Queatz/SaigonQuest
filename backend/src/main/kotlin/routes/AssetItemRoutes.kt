package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.AssetType
import models.ItemAssetItem
import models.ObjectAssetItem
import models.SpriteSheetAssetItem
import models.WorldAssetItem
import repository.AssetItemRepository
import repository.UserRepository

/**
 * Asset item routes: search and per-asset CRUD (bulk replace) for individual *AssetItem types.
 */
fun Routing.configureAssetItemRoutes() {
    val repo = AssetItemRepository()
    val userRepository = UserRepository()

    route("/asset-items") {
        // Search by type/title/category
        get("/search") {
            val typeParam = call.request.queryParameters["type"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing type parameter"))
            val title = call.request.queryParameters["title"]
            val category = call.request.queryParameters["category"]

            val type = try {
                AssetType.valueOf(typeParam)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid type"))
            }

            when (type) {
                AssetType.Items -> call.respond(repo.searchItems(type, title, category) as List<ItemAssetItem>)
                AssetType.SpriteSheet -> call.respond(repo.searchItems(type, title, category) as List<SpriteSheetAssetItem>)
                AssetType.World -> call.respond(repo.searchItems(type, title, category) as List<WorldAssetItem>)
                AssetType.Objects -> call.respond(repo.searchItems(type, title, category) as List<ObjectAssetItem>)
            }
        }

        // Search including parent Asset document
        get("/search-with-asset") {
            val typeParam = call.request.queryParameters["type"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing type parameter"))
            val title = call.request.queryParameters["title"]
            val category = call.request.queryParameters["category"]

            val type = try {
                AssetType.valueOf(typeParam)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid type"))
            }

            call.respond(repo.searchWithAsset(type, title, category))
        }

        // Get all items for a given asset
        get("/{type}/by-asset/{assetId}") {
            val typeParam = call.parameters["type"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing type parameter"))
            val assetId = call.parameters["assetId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing assetId parameter"))

            val type = try {
                AssetType.valueOf(typeParam)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid type"))
            }

            when (type) {
                AssetType.Items -> call.respond(repo.getByAssetId(type, assetId) as List<ItemAssetItem>)
                AssetType.SpriteSheet -> call.respond(repo.getByAssetId(type, assetId) as List<SpriteSheetAssetItem>)
                AssetType.World -> call.respond(repo.getByAssetId(type, assetId) as List<WorldAssetItem>)
                AssetType.Objects -> call.respond(repo.getByAssetId(type, assetId) as List<ObjectAssetItem>)
            }
        }

        // Admin-only: replace all items for an asset
        authenticate {
            put("/{type}/by-asset/{assetId}") {
                val typeParam = call.parameters["type"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing type parameter"))
                val assetId = call.parameters["assetId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing assetId parameter"))

                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                }

                val type = try {
                    AssetType.valueOf(typeParam)
                } catch (e: IllegalArgumentException) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid type"))
                }

                when (type) {
                    AssetType.Items -> {
                        val items = call.receive<List<ItemAssetItem>>()
                        repo.replaceForAsset(type, assetId, items)
                        call.respond(repo.getByAssetId(type, assetId) as List<ItemAssetItem>)
                    }
                    AssetType.SpriteSheet -> {
                        val items = call.receive<List<SpriteSheetAssetItem>>()
                        repo.replaceForAsset(type, assetId, items)
                        call.respond(repo.getByAssetId(type, assetId) as List<SpriteSheetAssetItem>)
                    }
                    AssetType.World -> {
                        val items = call.receive<List<WorldAssetItem>>()
                        repo.replaceForAsset(type, assetId, items)
                        call.respond(repo.getByAssetId(type, assetId) as List<WorldAssetItem>)
                    }
                    AssetType.Objects -> {
                        val items = call.receive<List<ObjectAssetItem>>()
                        repo.replaceForAsset(type, assetId, items)
                        call.respond(repo.getByAssetId(type, assetId) as List<ObjectAssetItem>)
                    }
                }
            }
        }
    }
}
