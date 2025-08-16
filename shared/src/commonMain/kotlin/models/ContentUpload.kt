package models

import kotlinx.serialization.Serializable

/**
 * Record of a user-uploaded content file stored by the content server.
 *
 * - userId: the authenticated API user id
 * - originalFileName: the original file name from the client
 * - uploadedFilePath: the public path served by the content server (e.g. /static/xyz.png)
 */
@Serializable
data class ContentUpload(
    var userId: String = "",
    var originalFileName: String = "",
    var uploadedFilePath: String = ""
) : BaseModel()

