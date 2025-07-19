package main

import app.softwork.routingcompose.Router

fun Router.navigateTo(route: Route) {
    when (route) {
        is Route.Home -> navigate("/")
    }
}

fun Router.home() {
    navigateTo(Route.Home)
}
