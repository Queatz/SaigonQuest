package main.utils

fun String.ellipsize(length: Int = 24) = if (this.length <= length) this else "${substring(0, length)}…"
