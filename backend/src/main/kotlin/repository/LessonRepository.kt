package repository

import com.arangodb.ArangoCollection
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentReplaceOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.LessonScene

class LessonRepository {
    private val lessonColl: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.LESSON_SCENES_COLLECTION)

    fun createLesson(lesson: LessonScene): LessonScene {
        return lessonColl.insertDocument(lesson, DocumentCreateOptions().returnNew(true)).new
    }

    fun updateLesson(lesson: LessonScene): LessonScene {
        return lessonColl.replaceDocument(lesson.key, lesson, DocumentReplaceOptions().returnNew(true)).new
    }

    fun getLessonById(id: String): LessonScene? {
        return try {
            lessonColl.getDocument(id, LessonScene::class.java, DocumentReadOptions())
        } catch (e: Throwable) {
            println("[ERROR] Failed to get lesson by id $id: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun getAllLessons(): List<LessonScene> {
        val query = "FOR l IN @@collection RETURN l"
        return ArangoConfig.db.query(query, LessonScene::class.java, mapOf("@collection" to CollectionsConfig.LESSON_SCENES_COLLECTION)).toList()
    }

    fun getLessonsByUserId(userId: String): List<LessonScene> {
        val query = "FOR l IN @@collection FILTER l.createdBy == @userId RETURN l"
        return ArangoConfig.db.query(query, LessonScene::class.java, mapOf("@collection" to CollectionsConfig.LESSON_SCENES_COLLECTION, "userId" to userId)).toList()
    }

    fun deleteLesson(id: String): Boolean {
        return try {
            lessonColl.deleteDocument(id)
            true
        } catch (e: Throwable) {
            println("[ERROR] Failed to delete lesson $id: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
