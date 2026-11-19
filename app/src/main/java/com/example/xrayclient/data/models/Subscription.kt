package com.example.xrayclient.data.models

import java.util.UUID

enum class SubscriptionStatus {
    IDLE,
    UPDATING,
    SUCCESS,
    ERROR
}

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val lastUpdatedAt: Long? = null,
    val status: SubscriptionStatus = SubscriptionStatus.IDLE,
    val errorMessage: String? = null,
    val serverCount: Int = 0,
    val autoUpdateEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
