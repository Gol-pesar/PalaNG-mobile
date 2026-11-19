package com.example.xrayclient.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.xrayclient.data.models.Subscription
import com.example.xrayclient.data.models.SubscriptionStatus

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val lastUpdatedAt: Long?,
    val status: String,
    val errorMessage: String?,
    val serverCount: Int,
    val autoUpdateEnabled: Boolean,
    val createdAt: Long
) {
    fun toDomain(): Subscription = Subscription(
        id = id,
        name = name,
        url = url,
        lastUpdatedAt = lastUpdatedAt,
        status = runCatching { SubscriptionStatus.valueOf(status) }.getOrDefault(SubscriptionStatus.IDLE),
        errorMessage = errorMessage,
        serverCount = serverCount,
        autoUpdateEnabled = autoUpdateEnabled,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(sub: Subscription): SubscriptionEntity = SubscriptionEntity(
            id = sub.id,
            name = sub.name,
            url = sub.url,
            lastUpdatedAt = sub.lastUpdatedAt,
            status = sub.status.name,
            errorMessage = sub.errorMessage,
            serverCount = sub.serverCount,
            autoUpdateEnabled = sub.autoUpdateEnabled,
            createdAt = sub.createdAt
        )
    }
}
