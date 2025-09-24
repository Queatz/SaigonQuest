# DOM Utils Refactoring

## Overview

This document describes the refactoring of DOM-related operations in the Saigon.Quest project to avoid using `js()` and `asDynamic()` functions, which are unsafe and can lead to runtime errors.

## Problem

The project was using `js()` and `asDynamic()` for DOM operations, which are unsafe and can lead to runtime errors. For example, we encountered the following error:

```
element.asDynamic is not a function
```

This error occurs when trying to call a method on an object that doesn't have that method, which is a common issue when using `asDynamic()`.

## Solution

We created a set of type-safe utility functions in a new file `DomUtils.kt` to replace the unsafe `js()` and `asDynamic()` calls:

1. `Element.getAttributeSafe(name: String)`: A safe way to get an attribute from an Element
2. `Element.getBoundingClientRectSafe()`: A safe way to get the bounding client rect of an Element
3. `EventTarget.getBoundingClientRectSafe()`: A safe way to get the bounding client rect of an EventTarget
4. `EventTarget.getAttributeSafe(name: String)`: A safe way to get an attribute from an EventTarget
5. `Document.getElementFromPoint(x: Float, y: Float)`: A type-safe way to get an element from a point in the document
6. `getDocument()`: A type-safe way to get the current document

We then replaced all instances of `js()` and `asDynamic()` in the node editor components with these type-safe alternatives.

## Files Changed

1. Created new file: `src/commonMain/kotlin/main/utils/DomUtils.kt`
2. Updated `src/commonMain/kotlin/main/components/nodeeditor/PortElement.kt`
3. Updated `src/commonMain/kotlin/main/components/nodeeditor/LogicEditorDialog.kt`

## Changes Made

### DomUtils.kt

Created a new file with type-safe utility functions for DOM operations:

```kotlin
package main.utils

import org.w3c.dom.Element
import org.w3c.dom.DOMRect
import org.w3c.dom.Document
import org.w3c.dom.get
import org.w3c.dom.events.EventTarget
import kotlinx.browser.document

fun Element.getAttributeSafe(name: String): String? {
    return getAttribute(name)
}

fun Element.getBoundingClientRectSafe(): DOMRect {
    return getBoundingClientRect()
}

fun EventTarget.getBoundingClientRectSafe(): DOMRect? {
    return (this as? Element)?.getBoundingClientRect()
}

fun EventTarget.getAttributeSafe(name: String): String? {
    return (this as? Element)?.getAttribute(name)
}

fun Document.getElementFromPoint(x: Float, y: Float): Element? {
    return elementFromPoint(x.toDouble(), y.toDouble())
}

fun getDocument(): Document {
    return document
}
```

### PortElement.kt

Replaced `asDynamic()` calls with type-safe alternatives:

```diff
- val rect = event.currentTarget?.asDynamic()?.getBoundingClientRect() as? DOMRect
+ val rect = event.currentTarget?.getBoundingClientRectSafe()
```

```diff
- val rect = element.asDynamic().getBoundingClientRect() as? DOMRect ?: return Pair(0f, 0f)
+ val rect = element.getBoundingClientRectSafe() ?: return Pair(0f, 0f)
```

### LogicEditorDialog.kt

Replaced `js()` and `asDynamic()` calls with type-safe alternatives:

```diff
- val element = js("document.elementFromPoint(x, y)")
+ val element = getDocument().getElementFromPoint(x, y)
```

```diff
- val portType = element?.asDynamic()?.getAttribute("data-port-type")
- val portId = element?.asDynamic()?.getAttribute("data-port-id")
- val portNodeId = element?.asDynamic()?.getAttribute("data-port-node")
+ val portType = element?.getAttributeSafe("data-port-type")
+ val portId = element?.getAttributeSafe("data-port-id")
+ val portNodeId = element?.getAttributeSafe("data-port-node")
```

```diff
- val rect = event.currentTarget?.asDynamic()?.getBoundingClientRect() as? DOMRect
+ val rect = event.currentTarget?.getBoundingClientRectSafe()
```

## Benefits

1. **Type Safety**: The new utility functions provide type safety, which helps catch errors at compile time rather than runtime.
2. **Better IDE Support**: The IDE can provide better code completion and error checking for the type-safe functions.
3. **Improved Readability**: The code is now more readable and easier to understand.
4. **Reduced Risk of Runtime Errors**: By avoiding `js()` and `asDynamic()`, we reduce the risk of runtime errors.

## Future Work

1. Continue to identify and replace any remaining `js()` and `asDynamic()` calls in the codebase with type-safe alternatives.
2. Consider adding more utility functions to `DomUtils.kt` as needed.
3. Add unit tests for the utility functions to ensure they work correctly.
