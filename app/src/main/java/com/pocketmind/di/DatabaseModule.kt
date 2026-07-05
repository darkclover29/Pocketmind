package com.pocketmind.di

import android.content.Context
import androidx.room.Room
import com.pocketmind.data.db.PocketMindDatabase
import com.pocketmind.data.db.dao.ChatDao
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
    fun providePocketMindDatabase(
        @ApplicationContext context: Context
    ): PocketMindDatabase =
        Room.databaseBuilder(
            context,
            PocketMindDatabase::class.java,
            PocketMindDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()   // swap for proper Migrations in prod
        .build()

    @Provides
    @Singleton
    fun provideChatDao(db: PocketMindDatabase): ChatDao = db.chatDao()
}
