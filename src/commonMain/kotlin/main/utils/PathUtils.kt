package main.utils

import app.softwork.routingcompose.Router
import kotlinx.browser.window

/**
 * Utility functions for handling paths and routes in the application.
 */
object PathUtils {
    /**
     * Gets the current path from the URL.
     * This function uses Router.current as a dependency but still uses window.location.pathname
     * for getting the actual path to maintain compatibility with the current implementation.
     * 
     * @param router The current Router instance
     * @return The current path without the leading slash
     */
    fun getCurrentPath(router: Router): String {
        return router.currentPath.path.trimStart('/')
    }
}
