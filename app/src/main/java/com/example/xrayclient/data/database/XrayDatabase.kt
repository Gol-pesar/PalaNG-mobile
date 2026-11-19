package com.example.xrayclient.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ServerEntity::class, SubscriptionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class XrayDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: XrayDatabase? = null

        fun getInstance(context: Context): XrayDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    XrayDatabase::class.java,
                    "xray_client.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
