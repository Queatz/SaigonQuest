package utils

import kotlinx.serialization.json.Json

/**
 * Standardized JSON configuration for the entire application.
 * This configuration is used for all JSON serialization and deserialization operations.
 */
val json = Json { 
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true  // Ensure default values are encoded
    classDiscriminator = "type"  // Use "type" as the discriminator for polymorphic serialization
}
