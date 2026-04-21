package org.example.synclist

import kotlinx.serialization.Serializable

@Serializable
data class ListItem(
    val id: String = "",
    val text: String = "",
    val isChecked: Boolean = false,
    val timestamp: Long = 0L,
    val position: Double = 0.0
)
