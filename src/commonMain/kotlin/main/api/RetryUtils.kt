package main.api

/**
 * Executes the given [block] and retries it up to [times] times if it fails.
 * Throws the last exception if all attempts fail.
 */
suspend fun <T> withRetry(
    times: Int = 3,
    block: suspend () -> T
): T {
    var lastError: Throwable? = null
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            lastError = e
            // If it's the last attempt, don't catch so we don't swallow the error unnecessarily
            // though we throw it later anyway.
            if (attempt == times - 1) throw e
        }
    }
    throw lastError ?: Error("Retry failed")
}
