# World Docs implementation plan

World Docs allow the user to create and view documents related to the world.
These docs can be used as input to logic nodes, as well as being viewed by other users.

Docs can be re-ordered, and reorganized under other docs.

Docs are owned by the world, not users.

Docs can be collaboratively edited by multiple users at the same time. (Operational Transform (OT) and Conflict-Free Replicated Data Types (CRDTs) over WebSockets)

## Entrypoint

Line 649 in WorldUI.kt

User clicks the "worldButton" button which opens the new WorldDocsDialog.

## Doc model

In `shared` module.

New file: `/shared/src/commonMain/kotlin/models/Doc.kt`

## Doc API

New file: `/src/commonMain/kotlin/main/api/DocApi.kt`
New file: `/backend/src/main/kotlin/routes/DocRoutes.kt`

## Doc DB

New collection: `docs` in `ArangoConfig.kt`

## WorldDocsDialog

A dialog with a left-side panel.

### Left-side panel

 - A header with the world name.
 - A button to create a new document. (if the user is the owner of the world)
 - A search bar to search the documents by name.
 - A tree of documents in the world.
   - Each document has a arrow to expand/collapse its children.
   - Drag and drop to reorder documents.

### Main content

Empty state:

"World has no docs." text
"Create doc" button

No doc selected:

Top doc is auto-selected.

Doc selected:

`RichTextEditor` with the doc's content. (if the user is the owner of the world)
The doc's content rendered in `Md`. (if the user is not the owner of the world)

---

# Additional ideas to help us get this right the first time around!

## Overview
World Docs is a collaborative real-time document editing system that allows users to create, organize, and edit documents within a shared world context. Documents can be used as input for logic nodes and viewed collaboratively by multiple users.

## Core Architecture

### Document Model
**File**: `/shared/src/commonMain/kotlin/models/Doc.kt`

```kotlin
data class Doc(
    val id: String,
    val worldId: String,
    val title: String,
    val content: String,
    val parentId: String?,
    val orderIndex: Double, // For manual ordering using fractional indexing
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String, // User ID
    val version: Int, // For optimistic concurrency control
    val permissions: DocPermissions
)

data class DocPermissions(
    val worldOwnerCanEdit: Boolean = true,
    val worldUsersCanView: Boolean = true,
    val worldUsersCanEdit: Boolean = false
)

data class DocTree(
    val doc: Doc,
    val children: List<DocTree> = emptyList()
)
```

### Real-time Collaboration System
**Technology Stack**: Operational Transform (OT) with WebSockets

**File**: `/shared/src/commonMain/kotlin/models/Collaboration.kt`
```kotlin
// OT Operation types
sealed class DocOperation {
    data class Insert(val position: Int, val text: String) : DocOperation()
    data class Delete(val position: Int, val length: Int) : DocOperation()
    data class Format(val position: Int, val length: Int, val format: TextFormat) : DocOperation()
}

data class CollaborationSession(
    val docId: String,
    val userId: String,
    val cursorPosition: Int,
    val version: Int
)

data class TextFormat(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false
)
```

## Backend Implementation

### Database Schema
**Collection**: `docs` in ArangoDB
```kotlin
// Additional indices required:
// - worldId, parentId, orderIndex (for tree traversal)
// - worldId, title (for search)
// - updatedAt (for recent docs)
```

### API Endpoints
**File**: `/backend/src/main/kotlin/routes/DocRoutes.kt`

**REST Endpoints**:
- `POST /api/worlds/{worldId}/docs` - Create new document
- `GET /api/worlds/{worldId}/docs` - Get document tree
- `GET /api/docs/{docId}` - Get single document
- `PUT /api/docs/{docId}` - Update document metadata
- `DELETE /api/docs/{docId}` - Delete document
- `POST /api/docs/{docId}/move` - Move/reorder document

**WebSocket Endpoint**:
- `WS /api/docs/{docId}/collaborate` - Real-time collaboration channel

### Collaboration Service
**File**: `/backend/src/main/kotlin/service/CollaborationService.kt`

Responsibilities:
- Manage WebSocket connections per document
- Handle OT operations with transformation
- Maintain document versioning
- Broadcast changes to all connected clients
- Handle conflict resolution

## Frontend Implementation

### Entry Point
**File**: `WorldUI.kt` (Line 649)
- World button opens `WorldDocsDialog`

### WorldDocsDialog Component
**Structure**:
```
WorldDocsDialog
├── Header (World name + user role indicator)
├── SplitPane
│   ├── LeftPanel (Document Tree)
│   │   ├── CreateDocButton (condition: world owner)
│   │   ├── SearchBar (real-time filtering)
│   │   └── DocTreeView (virtualized for performance)
│   └── RightPanel (Document Content)
│       ├── EmptyState / NoSelectionState
│       └── DocEditor (condition-based)
```

### Document Tree Component
**Features**:
- Expandable/collapsible tree nodes with arrows
- Drag-and-drop reordering (using fractional indexing)
- Visual indicators for:
    - Unsaved changes
    - Collaborative editing status
    - Permission restrictions
- Context menu for document operations
- Real-time updates when other users modify structure

### Document Editor Component
**Two Modes**:
1. **Edit Mode** (for owners/users with edit permissions):
    - RichTextEditor with toolbar
    - Real-time collaboration indicators (other users' cursors)
    - Auto-save with debouncing
    - Version history access

2. **View Mode** (for viewers):
    - Rendered Markdown with syntax highlighting
    - Read-only access
    - Option to request edit permissions

## Real-time Collaboration Features

### Operational Transform Implementation
**Client-side OT**:
- Buffer operations when offline
- Transform incoming operations against local changes
- Handle rollback on transformation conflicts

**Server-side OT**:
- Maintain operation history for conflict resolution
- Validate operations against current document state
- Broadcast transformed operations to all clients

### Presence Awareness
- Show active users in document
- Display real-time cursors and selections
- Typing indicators
- User avatars and colors

## Security & Permissions

### Access Control
```kotlin
enum class DocAccessLevel {
    NO_ACCESS,
    VIEWER,
    EDITOR,
    OWNER
}
```

### Validation Rules
- World owner has full access
- User permissions checked on every operation
- Document inheritance: child documents inherit parent permissions
- Operation validation in collaboration service

## Performance Optimizations

### Frontend
- Virtual scrolling for large document trees
- Debounced search (300ms)
- Operation batching for rapid typing
- Efficient re-rendering with selective updates

### Backend
- Database indexing on frequently queried fields
- WebSocket connection pooling
- Operation compression for large documents
- Caching for document metadata

## Error Handling & Edge Cases

### Collaboration Scenarios
- Network disconnection/reconnection
- Version conflicts with automatic resolution
- Concurrent structural changes (moving while editing)
- Large document performance

### User Experience
- Loading states for all async operations
- Error messages with recovery options
- Offline capability with sync on reconnect
- Conflict resolution UI when automatic resolution fails
