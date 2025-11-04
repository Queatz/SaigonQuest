package models

import kotlinx.serialization.Serializable

@Serializable
sealed class DocOperation {
    @Serializable data class Insert(val position: Int, val text: String) : DocOperation()
    @Serializable data class Delete(val position: Int, val length: Int) : DocOperation()
    @Serializable data class Format(val position: Int, val length: Int, val format: TextFormat) : DocOperation()
}

@Serializable
sealed class WsMsg {
    @Serializable data class Join(val docId: String, val userId: String, val cursor: Int) : WsMsg()
    @Serializable data class Presence(val userId: String, val cursor: Int) : WsMsg()
    @Serializable data class Op(val userId: String, val baseVersion: Int, val ops: List<DocOperation>) : WsMsg()
    @Serializable data class Snapshot(val content: String, val version: Int) : WsMsg()
    @Serializable data class Ack(val version: Int) : WsMsg()
    @Serializable data class Error(val message: String) : WsMsg()
}

@Serializable
data class CollaborationSession(
    val docId: String,
    val userId: String,
    val cursorPosition: Int,
    val version: Int,
)

@Serializable
data class TextFormat(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)
