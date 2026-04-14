package main.utils

import app.softwork.routingcompose.Router

/**
 * Utility functions for handling paths and routes in the application.
 */
object PathUtils {
    /**
     * Gets the current path from the router.
     * 
     * @param router The current Router instance
     * @return The current path without the leading slash
     */
    fun getCurrentPath(router: Router): String {
        return router.currentPath.path.trimStart('/')
    }
}
