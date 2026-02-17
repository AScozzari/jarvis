package it.edgvoip.jarvis.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.edgvoip.jarvis.data.api.ApiClient
import it.edgvoip.jarvis.data.api.JarvisApi
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.db.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        return ApiClient.getOkHttpClient(tokenManager)
    }

    @Provides
    @Singleton
    fun provideRetrofit(tokenManager: TokenManager): Retrofit {
        return ApiClient.getRetrofit(tokenManager)
    }

    @Provides
    @Singleton
    fun provideJarvisApi(retrofit: Retrofit): JarvisApi {
        return retrofit.create(JarvisApi::class.java)
    }

    @Provides
    @Singleton
    fun provideJarvisDatabase(@ApplicationContext context: Context): JarvisDatabase {
        return Room.databaseBuilder(
            context,
            JarvisDatabase::class.java,
            "jarvis_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideConversationDao(database: JarvisDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideMessageDao(database: JarvisDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideCallLogDao(database: JarvisDatabase): CallLogDao {
        return database.callLogDao()
    }

    @Provides
    fun provideNotificationDao(database: JarvisDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    fun provideContactDao(database: JarvisDatabase): ContactDao {
        return database.contactDao()
    }
}
