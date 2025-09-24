# Fix for Jackson Date/Time Serialization and Deserialization Issues

## Issue Description

The backend module encountered two related errors when working with User objects:

### Serialization Error

```
Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Java 8 date/time type `java.time.Instant` not supported by default: add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to enable handling (through reference chain: models.User["createdAt"]->kotlinx.datetime.Instant["value$kotlinx_datetime"])
```

### Deserialization Error

```
Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance of `kotlinx.datetime.Instant` (no Creators, like default constructor, exist): cannot deserialize from Object value (no delegate- or property-based Creator)
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 84] (through reference chain: models.User["createdAt"])
```

These errors occurred because the ArangoDB Java driver was using Jackson for serialization and deserialization, but it didn't have proper support for `kotlinx.datetime.Instant`. The User model contains a `createdAt` field of type `kotlinx.datetime.Instant` (inherited from BaseModel), which was causing both serialization and deserialization to fail.

## Solution

The solution involved several steps:

1. Adding the necessary Jackson modules as dependencies:
   - `jackson-datatype-jsr310` for Java 8 date/time support
   - `jackson-module-kotlin` for Kotlin-specific features

2. Configuring the ArangoDB driver to use a custom Jackson ObjectMapper with these modules.

3. Creating a custom Jackson module to handle `kotlinx.datetime.Instant` during both serialization and deserialization.

### Changes Made

1. Added dependencies to `backend/build.gradle.kts`:

```kotlin
// Jackson modules for date/time handling
implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
```

2. Modified `backend/src/main/kotlin/database/ArangoConfig.kt` to configure the ArangoDB driver with a custom Jackson ObjectMapper that includes a special module for handling `kotlinx.datetime.Instant`:

```kotlin
// Custom serializer for kotlinx.datetime.Instant that extends StdSerializer
private class KotlinxInstantSerializer : StdSerializer<Any>(Any::class.java) {
    @Throws(IOException::class)
    override fun serialize(value: Any, gen: JsonGenerator, provider: SerializerProvider) {
        // Check if the value is a kotlinx.datetime.Instant
        if (value::class.qualifiedName == "kotlinx.datetime.Instant") {
            // Convert to string and write
            gen.writeString(value.toString())
        }
    }
}

// Custom deserializer for kotlinx.datetime.Instant that extends StdDeserializer
private class KotlinxInstantDeserializer : StdDeserializer<Any>(Any::class.java) {
    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any? {
        // Get the current value as a string
        val text = p.valueAsString
        
        // Check if we're trying to deserialize a kotlinx.datetime.Instant
        val currentType = ctxt.contextualType?.rawClass?.name
        if (currentType == "kotlinx.datetime.Instant") {
            try {
                // Use reflection to parse the string to kotlinx.datetime.Instant
                val instantClass = Class.forName("kotlinx.datetime.Instant")
                val parseMethod = instantClass.getMethod("parse", String::class.java)
                return parseMethod.invoke(null, text)
            } catch (e: Throwable) {
                throw IOException("Failed to parse kotlinx.datetime.Instant: ${e.message}", e)
            }
        }
        
        // Return the original value
        return text
    }
}

// Create a custom module for handling kotlinx.datetime.Instant
private val kotlinxDateTimeModule = SimpleModule("KotlinxDateTimeModule").apply {
    addSerializer(Any::class.java, KotlinxInstantSerializer())
    addDeserializer(Any::class.java, KotlinxInstantDeserializer())
}

// Configure Jackson ObjectMapper with all necessary modules
private val objectMapper = ObjectMapper().apply {
    registerModule(JavaTimeModule())
    registerModule(kotlinxDateTimeModule)
    registerKotlinModule()
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

private val jacksonSerde = JacksonSerdeImpl(objectMapper)

private val arangoDB = ArangoDB.Builder()
    .host(HOST, PORT)
    .user(USERNAME)
    .password(PASSWORD)
    .serde(jacksonSerde)
    .build()
```

## Explanation

The issues were caused by a mismatch between the serialization libraries used in the project:

- The models are annotated with `@Serializable` for Kotlin Serialization
- The ArangoDB driver uses Jackson for serialization internally

When the ArangoDB driver tried to serialize and deserialize the User model, it encountered the `kotlinx.datetime.Instant` type, which it didn't know how to handle.

The first part of the solution (adding the `jackson-datatype-jsr310` module) fixed the serialization issue by enabling proper handling of Java 8 date/time types. However, this didn't fully solve the deserialization issue because Jackson still didn't know how to create instances of `kotlinx.datetime.Instant`.

To solve the deserialization issue, we created a custom Jackson module that:

1. Uses a custom `StdSerializer<Any>` that checks if the value is a `kotlinx.datetime.Instant` by its qualified name, and if so, serializes it as a string.
2. Uses a custom `StdDeserializer<Any>` that checks if the target type is `kotlinx.datetime.Instant` by its name, and if so, uses reflection to parse the string into a `kotlinx.datetime.Instant` object.

This approach avoids the need to directly reference `kotlinx.datetime.Instant` in the type system, which would cause compilation errors due to the multiplatform nature of the library. Instead, it uses reflection and type checking to handle `kotlinx.datetime.Instant` objects at runtime.

We specifically used `StdSerializer` and `StdDeserializer` instead of `JsonSerializer` and `JsonDeserializer` because they properly handle the `handledType()` method, which is required by Jackson for proper type registration. This fixed the initialization error that occurred with our previous implementation.

## Additional Notes

- The `disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` configuration ensures that dates are serialized in ISO-8601 format rather than as numeric timestamps, which is generally more readable and interoperable.
- The reflection-based approach used for deserialization is a workaround for the limitations of using a multiplatform library (kotlinx.datetime) with a JVM-only serialization library (Jackson). In a pure JVM project, a more direct approach could be used.
- This fix addresses the specific errors with date/time serialization and deserialization, but there might be other serialization issues that could arise in the future due to the use of different serialization libraries in the project.
