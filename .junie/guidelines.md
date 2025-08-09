*# Saigon.Quest Development Guidelines

This document provides guidelines and instructions for developing and maintaining the Saigon.Quest project.

## Project Status

The project is not in production yet, so we do not need to account for backwards compatibility!

## Project Overview

Saigon.Quest is a Kotlin Multiplatform project targeting JavaScript (Kotlin/JS) using Compose for Web. The project uses several key technologies:

- **Kotlin Multiplatform**: Allows sharing code between different platforms
- **Compose for Web**: For building the UI using a declarative approach
- **Routing Compose**: For handling navigation and routing
- **External JavaScript Libraries**: Including BabylonJS for 3D graphics, QR code generation, and Marked for Markdown rendering

## Local Development Environment

The web frontend is hosted at: `http://localhost:4080`

The backend is hosted at: `http://localhost:8080`

## Build and Configuration

### Prerequisites

- JDK 11 or higher
- Gradle 7.4 or higher
- Node.js and npm (automatically managed by Kotlin/JS Gradle plugin)

### Building the Project

The project uses Gradle for building. The main build tasks are:

```bash
# Clean build
./gradlew clean

# Build the project
./gradlew build
```

### Project Structure

- `src/commonMain/kotlin/`: Common Kotlin code shared across platforms
  - `lib/`: External library wrappers
  - `main/`: Main application code
    - `components/`: UI components
    - `theme/`: Styling and theming
- `src/jsMain/`: JavaScript-specific code
  - `kotlin/`: Kotlin code specific to the JS target
  - `resources/`: Static resources for the JS application

## External Libraries

The project uses several external JavaScript libraries, which are wrapped in Kotlin interfaces for type-safe access:

### BabylonJS (3D Graphics)

Located in `src/commonMain/kotlin/lib/Bjs.kt`, this wrapper provides access to the BabylonJS library for 3D graphics. The library is included as an npm dependency:

### Marked (Markdown Rendering)

Located in `src/commonMain/kotlin/lib/Marked.kt`, this wrapper provides access to the marked library for rendering Markdown to HTML. The library is included as an npm dependency:

```kotlin
implementation(npm("marked", "15.0.7"))
```

## UI & UX Requirements

Use sentence case for all UI.

## Translations

The project supports multiple langauges.

### Supported Languages

`en`, `vi`

### How to Add New Translations

Translations are declared in `shared/src/commonMain/kotlin/Translations.kt`.

**Add all new translated strings to this file in the `translations` val!**

### How To Use Translations

Composable helpers are declared in `src/commonMain/kotlin/main/components/Translated.kt`

```kotlin
Div {
    Translated("Some text")
}
```

or

```kotlin
BasicConfirmDialog(
    message = translated("Some text")
    // ...
)
```

When we are not able to use the Composable helpers, we should use `str()` directly:

```kotlin
Div(
    attrs = {
        title(str("Some text"))
    }
) {
    
}
```

Alternatively, we can use a val from the Composable context to make the string auto-recompose:

```kotlin
val someText = translated("Some text")

Div(
    attrs = {
        title(someText)
    }
) {
    
}
```

## Architecture Requirements

Always implement a proper system before jumping into code changes.

Step 1) The architected, reusable, clean system.
Step 2) The actual implementation, using the system.

This ensures we are carefully building for the future, ensuring code flexibility and longevity,
allowing the project to remain maintainable as it grows.

## Kotlin/JS Code Style Guidelines

- **Kotlin inline objects like `object { val field: String = "" }` DO NOT work in JavaScript**: Create an `external interface` to handle POJOs correctly.
- **DO NOT** put globals on the `window` object, just use top-level Kotlin vals
- **No more than one @Composable function per file**
- **750 lines max in one file**: Split larger files into smaller ones 
- **Avoid JS Promises**: Use Kotlin coroutines instead 
- **Always use Ktor for networking**: This includes SSE, WebSockets, HTTP, etc.
- **Avoid using `js()`, `dynamic` and `asDynamic()` at all costs:** Implement the interface in the correct library file instead.
- **Avoid using html element IDs**: Do not use querySelector and getElementById. Instead, reference elements by their ref obtained with `ref { element -> }`
- **Avoid using inline styles like `attrs = { style { ... } }`:** Style definitions are in Styles.kt, Theme.kt, and new stylesheet files can be created as needed by adding an entry for them in Main.kt.
- **Use `by remember`** when possible to avoid the need to reference and set using `.value`
- **Use dark, mobile, desktop helpers**: change the colors and other styles based on the situation. 
- Use trailing commas
- Always put arguments on separate lines
- Always use named arguments when possible

## Code Style and Development Guidelines

- **Use @Serializable data class**: For all data models
- **Use enum classes instead of raw strings**: For all enumerated values
- **Enums use** PascalCase (UpperCamelCase)
- **Shared data classes**: All shared models go in the shared module
- **Clean up as you go**: Always go the extra mile and look for chances to clean up or remove dead code and imports
- **No duplicate code**: Always create reusabe code instead of duplicatating code
- **Plan for the future**: Structure code well, prefer to create new files for new classes and objects
- **Package Structure**: Follow the existing package structure, with common code in `src/commonMain` and platform-specific code in `src/jsMain`.
- **Naming Conventions**: Use standard Kotlin naming conventions (camelCase for variables and functions, PascalCase for classes and interfaces).
- **Routing**: Use the Route sealed interface for type-safe routing, and the extension functions in RouterExtensions.kt for navigation.
- **UI Components**: Place reusable UI components in the `main/components` package.
- **Styling**: Use the theme system defined in `main/theme` for consistent styling.
