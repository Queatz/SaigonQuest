package main

sealed interface Route {
    data object Home : Route
}
