# Saigon.Quest Development Guidelines

This document provides guidelines and instructions for developing and maintaining the Saigon.Quest project.

## Project Overview

Saigon.Quest is a Kotlin Multiplatform project targeting JavaScript (Kotlin/JS) using Compose for Web. The project uses several key technologies:

- **Kotlin Multiplatform**: Allows sharing code between different platforms
- **Compose for Web**: For building the UI using a declarative approach
- **Routing Compose**: For handling navigation and routing
- **External JavaScript Libraries**: Including BabylonJS for 3D graphics, QR code generation, and Marked for Markdown rendering

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

# Run the development server
./gradlew run
```

The `run` task is an alias for `jsBrowserDevelopmentRun`, which starts a webpack dev server on port 4080 and opens the application in a browser.

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

```kotlin
implementation(npm("@babylonjs/core", "8.7.0"))
implementation(npm("@babylonjs/materials", "8.7.0"))
```

### QR Code Generation

Located in `src/commonMain/kotlin/lib/Qr.kt`, this wrapper provides access to the @paulmillr/qr library for generating QR codes. The library is included as an npm dependency:

```kotlin
implementation(npm("@paulmillr/qr", "0.2.0"))
```

Usage example:
```kotlin
import lib.Qr

// Generate a QR code
val qrCode = Qr.createQR("https://example.com", "svg")
```

### Marked (Markdown Rendering)

Located in `src/commonMain/kotlin/lib/Marked.kt`, this wrapper provides access to the marked library for rendering Markdown to HTML. The library is included as an npm dependency:

```kotlin
implementation(npm("marked", "15.0.7"))
```

## Kotlin/JS Code Guidelines

- **Avoid JS Promises**: Use Kotlin coroutines instead 
- **Always use Ktor for networking**
- **Avoid using `js()`, `dynamic` and `asDynamic()` at all costs:** Implement the interface in the correct library file instead.
- **Avoid using inline styles:** Style definitions are in Styles.kt, Theme.kt, and new stylesheet files can be created as needed by adding an entry for them in Main.kt.
- **Use `by remember`** when possible to avoid the need to reference and set using `.value`
- **Use dark, mobile, desktop helpers**: change the colors and other styles based on the situation. 

## Code Style and Development Guidelines

- **No duplicate code**: Always create reusabe code instead of duplicatating code
- **Package Structure**: Follow the existing package structure, with common code in `src/commonMain` and platform-specific code in `src/jsMain`.
- **Naming Conventions**: Use standard Kotlin naming conventions (camelCase for variables and functions, PascalCase for classes and interfaces).
- **Routing**: Use the Route sealed interface for type-safe routing, and the extension functions in RouterExtensions.kt for navigation.
- **UI Components**: Place reusable UI components in the `main/components` package.
- **Styling**: Use the theme system defined in `main/theme` for consistent styling.
