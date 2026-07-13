package com.pocketshadow.app.di

import android.content.Context
import androidx.room.Room
import com.pocketshadow.app.data.db.PocketShadowDatabase
import com.pocketshadow.app.data.db.dao.ChatDao
import com.pocketshadow.app.data.db.dao.InputHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePocketShadowDatabase(
        @ApplicationContext context: Context
    ): PocketShadowDatabase =
        Room.databaseBuilder(
            context,
            PocketShadowDatabase::class.java,
            PocketShadowDatabase.DATABASE_NAME
        )
        .addMigrations(PocketShadowDatabase.MIGRATION_1_2)
        .build()

    @Provides
    @Singleton
    fun provideChatDao(db: PocketShadowDatabase): ChatDao = db.chatDao()

    @Provides
    @Singleton
    fun provideInputHistoryDao(db: PocketShadowDatabase): InputHistoryDao =
        db.inputHistoryDao()
}
