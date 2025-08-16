package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.ContentUpload

class UploadRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.CONTENT_UPLOADS_COLLECTION)

    fun save(upload: ContentUpload): ContentUpload {
        val result: DocumentCreateEntity<ContentUpload> = collection.insertDocument(
            upload,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
    }
}

