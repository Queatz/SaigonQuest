@file:OptIn(ExperimentalTime::class)

package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.entity.DocumentUpdateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Sex
import models.User
import utils.json
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.ExperimentalTime

class UserRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.USERS_COLLECTION)
    private val json = utils.json
    
    /**
     * Creates a new user in the database
     */
    fun create(user: User): User {
        val result: DocumentCreateEntity<User> = collection.insertDocument(
            user,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Gets a user by their ID (key)
     */
    fun getById(id: String): User? {
        return try {
            collection.getDocument(
                id,
                User::class.java,
                DocumentReadOptions()
            )
        } catch (e: Throwable) {
            null
        }
    }
    
    /**
     * Gets a user by their visitor ID
     */
    fun getByVisitorId(visitorId: String): User? {
        val query = "FOR u IN @@collection FILTER u.visitorId == @visitorId RETURN u"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.USERS_COLLECTION,
            "visitorId" to visitorId
        )
        
        val users = ArangoConfig.db.query(query, User::class.java, bindVars).toList()
        return users.firstOrNull()
    }
    
    /**
     * Gets a user by their username
     */
    fun getByUsername(username: String): User? {
        val query = "FOR u IN @@collection FILTER u.username == @username RETURN u"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.USERS_COLLECTION,
            "username" to username
        )
        
        val users = ArangoConfig.db.query(query, User::class.java, bindVars).toList()
        return users.firstOrNull()
    }
    
    /**
     * Updates a user in the database
     */
    fun update(id: String, user: User): User? {
        val existingUser = getById(id) ?: return null
        
        // Preserve the original key and creation date
        user.key = existingUser.key
        
        val result: DocumentUpdateEntity<User> = collection.updateDocument(
            id,
            user,
            DocumentUpdateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Creates a new visitor user with a unique visitor ID
     */
    fun createVisitor(): User {
        val visitorId = UUID.randomUUID().toString()
        val user = User(
            username = "visitor-${visitorId.substring(0, 8)}",
            isPlayer = false,
            visitorId = visitorId
        )
        
        return create(user)
    }
    
    /**
     * Upgrades a visitor to a player with a username, password, year of birth, sex, and language
     */
    fun upgradeToPlayer(
        id: String,
        username: String,
        password: String,
        yearOfBirth: Int? = null,
        sex: Sex? = null,
        language: String? = null
    ): User? {
        val user = getById(id) ?: return null
        
        // Check if username is already taken
        if (getByUsername(username) != null) {
            return null
        }
        
        user.username = username
        user.passwordHash = hashPassword(password)
        user.isPlayer = true
        user.yearOfBirth = yearOfBirth
        user.sex = sex
        user.language = language
        user.visitorId = ""
        
        return update(id, user)
    }
    
    /**
     * Authenticates a user with username and password
     */
    fun authenticate(username: String, password: String): User? {
        val user = getByUsername(username) ?: return null
        
        // Check if password matches
        if (user.passwordHash != hashPassword(password)) {
            return null
        }
        
        return user
    }
    
    /**
     * Updates a user's character customization
     */
    fun updateCustomization(id: String, bodyType: String, skinTone: Float, eyeColor: String, hairStyle: String): User? {
        val user = getById(id) ?: return null
        
        // Update customization properties
        user.bodyType = bodyType
        user.skinTone = skinTone
        user.eyeColor = eyeColor
        user.hairStyle = hairStyle
        
        return update(id, user)
    }
    
    /**
     * Updates a user's worn items
     */
    fun updateWornItems(id: String, wornItems: List<String>): User? {
        val user = getById(id) ?: return null
        
        // Update worn items
        user.wornItems = wornItems
        
        return update(id, user)
    }
    
    /**
     * Updates a user's language preference
     */
    fun updateLanguage(id: String, language: String): User? {
        val user = getById(id) ?: return null
        
        // Update language
        user.language = language
        
        return update(id, user)
    }
    
    /**
     * Hashes a password using SHA-256
     */
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
