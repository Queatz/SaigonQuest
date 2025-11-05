package models.annotations

/**
 * Marks a model field to be indexed in ArangoDB.
 *
 * Usage:
 * - Single-field index: annotate the field without a group.
 * - Composite index: annotate multiple fields with the same [group] and specify [order]
 *   to control field order in the composite index.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class Indexed(
    /**
     * Optional group identifier to build a composite index.
     * Fields with the same non-blank group will be combined into a single index,
     * ordered by [order].
     */
    val group: String = "",
    /**
     * Field position within its [group] (0-based). Ignored when [group] is blank.
     */
    val order: Int = 0,
    /**
     * Whether the index should be unique.
     */
    val unique: Boolean = false,
    /**
     * Whether the index should be sparse.
     */
    val sparse: Boolean = false,
    /**
     * Whether the index should deduplicate values (applies to array fields).
     * Matches ArangoDB's PersistentIndexOptions.deduplicate setting.
     */
    val deduplicate: Boolean = true,
)
