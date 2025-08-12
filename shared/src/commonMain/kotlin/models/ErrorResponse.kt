package models

/**
 * Standardized error response model for API errors.
 * This class is used by both frontend and backend to ensure consistent error handling.
 */
class ErrorResponse(
    message: String,
    val code: Int = -1,
    val details: String? = null
) : Throwable(message)

