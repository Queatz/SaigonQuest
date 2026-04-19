package main.components.objects

import lib.Vector3
import kotlin.math.floor

/**
 * A simple 2D spatial hash for efficient object lookups by position.
 * This is used to optimize proximity checks and interaction distance calculations.
 */
class SpatialHash(private val cellSize: Float) {
    private val grid = mutableMapOf<Long, MutableSet<String>>()
    private val objectPositions = mutableMapOf<String, Long>()

    /**
     * Updates or adds an object's position in the spatial hash.
     */
    fun update(objectId: String, position: Vector3) {
        val hash = getHash(position.x, position.z)
        val oldHash = objectPositions[objectId]

        if (oldHash == hash) return

        if (oldHash != null) {
            grid[oldHash]?.remove(objectId)
        }

        objectPositions[objectId] = hash
        grid.getOrPut(hash) { mutableSetOf() }.add(objectId)
    }

    /**
     * Removes an object from the spatial hash.
     */
    fun remove(objectId: String) {
        val oldHash = objectPositions.remove(objectId)
        if (oldHash != null) {
            grid[oldHash]?.remove(objectId)
        }
    }

    /**
     * Clears all objects from the spatial hash.
     */
    fun clear() {
        grid.clear()
        objectPositions.clear()
    }

    /**
     * Returns a list of object IDs within the cells that overlap with a square area
     * defined by the center and radius.
     */
    fun getNearby(position: Vector3, radius: Float): Set<String> {
        val minX = floor((position.x - radius) / cellSize).toInt()
        val maxX = floor((position.x + radius) / cellSize).toInt()
        val minZ = floor((position.z - radius) / cellSize).toInt()
        val maxZ = floor((position.z + radius) / cellSize).toInt()

        val result = mutableSetOf<String>()
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val hash = getHashFromCoords(x, z)
                grid[hash]?.let { result.addAll(it) }
            }
        }
        return result
    }

    private fun getHash(x: Float, z: Float): Long {
        val ix = floor(x / cellSize).toInt()
        val iz = floor(z / cellSize).toInt()
        return getHashFromCoords(ix, iz)
    }

    private fun getHashFromCoords(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}
