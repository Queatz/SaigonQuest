package main.components.inventory

import kotlinx.serialization.Serializable

@Serializable
data class InventoryDragPayload(
    val inventoryItemKey: String,
    val itemKey: String,
    val quantity: Int,
    val inventoryId: String? = null,
)
