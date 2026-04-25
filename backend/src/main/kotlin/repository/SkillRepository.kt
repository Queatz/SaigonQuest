package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.entity.DocumentUpdateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Skill

/**
 * Repository for managing skills.
 */
class SkillRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.SKILLS_COLLECTION)
    
    /**
     * Creates a new skill in the database
     */
    fun create(skill: Skill): Skill {
        val result: DocumentCreateEntity<Skill> = collection.insertDocument(
            skill,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Gets a skill by its ID (key)
     */
    fun getSkillById(id: String): Skill? {
        return try {
            collection.getDocument(
                id,
                Skill::class.java,
                DocumentReadOptions()
            )
        } catch (e: Throwable) {
            null
        }
    }
    
    /**
     * Gets all skills
     */
    fun getAllSkills(): List<Skill> {
        val query = "FOR s IN @@collection SORT s.title ASC RETURN s"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.SKILLS_COLLECTION
        )
        
        return ArangoConfig.db.query(query, Skill::class.java, bindVars).toList()
    }
    
    /**
     * Updates a skill in the database
     */
    fun update(id: String, skill: Skill): Skill? {
        val existingSkill = getSkillById(id) ?: return null
        
        // Preserve the original key
        skill.key = existingSkill.key
        
        val result: DocumentUpdateEntity<Skill> = collection.updateDocument(
            id,
            skill,
            DocumentUpdateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Deletes a skill from the database
     */
    fun delete(id: String): Boolean {
        return try {
            collection.deleteDocument(id)
            true
        } catch (e: Throwable) {
            false
        }
    }
}
