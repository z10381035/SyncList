package org.example.synclist

import kotlinx.serialization.Serializable

@Serializable
data class ListMetadata(
    val id: String = "",
    val title: String = "Untitled List",
    val createdTimestamp: Long = 0L,
    val lastModifiedTimestamp: Long = 0L
)
