package models.annotations

/**
 * Declares a composite persistent index for a model (ArangoDB).
 *
 * Use this when a field must participate in multiple composite indexes, or when
 * you prefer to declare composite indexes at the class level.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class CompositeIndex(
    val fields: Array<String>,
    val unique: Boolean = false,
    val sparse: Boolean = false,
    val deduplicate: Boolean = true,
)

/**
 * Container annotation to declare multiple composite indexes on the same class.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class CompositeIndexes(
    val value: Array<CompositeIndex>,
)
