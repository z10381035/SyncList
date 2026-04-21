package org.example.synclist

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform