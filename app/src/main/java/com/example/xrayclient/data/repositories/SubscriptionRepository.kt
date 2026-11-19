package com.example.xrayclient.data.repositories

import com.example.xrayclient.data.database.SubscriptionDao
import com.example.xrayclient.data.database.SubscriptionEntity
import com.example.xrayclient.data.models.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SubscriptionRepository(private val subscriptionDao: SubscriptionDao) {

    val subscriptionsFlow: Flow<List<Subscription>> = subscriptionDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getAllOnce(): List<Subscription> =
        subscriptionDao.getAllOnce().map { it.toDomain() }

    suspend fun getById(id: String): Subscription? =
        subscriptionDao.getById(id)?.toDomain()

    suspend fun save(subscription: Subscription) {
        subscriptionDao.insert(SubscriptionEntity.fromDomain(subscription))
    }

    suspend fun deleteById(id: String) {
        subscriptionDao.deleteById(id)
    }
}
