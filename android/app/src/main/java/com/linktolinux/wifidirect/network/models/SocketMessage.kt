package com.linktolinux.wifidirect.network.models

import kotlinx.serialization.Serializable

@Serializable
data class SocketMessage(
    val type: String,
    val sender_id: String,
    val payload: String,
    val timestamp: Long
)
