package com.quranreader.custom.di

import android.content.Context
import androidx.room.Room
import com.quranreader.custom.data.audio.download.DownloadedSurahDao
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.quranreader.custom.data.audio.download.AudioCacheManager
import com.quranreader.custom.data.audio.timing.AyahTimingApi
import com.quranreader.custom.data.audio.timing.AyahTimingDao
import com.quranreader.custom.data.memorization.MemorizationSessionDao
import com.quranreader.custom.data.local.AyahCoordinateDao
import com.quranreader.custom.data.local.RecitationDao
import com.quranreader.custom.data.local.TranslationEditionDao
import com.quranreader.custom.data.remote.QuranComApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.quranreader.custom.data.local.BookmarkDao
import com.quranreader.custom.data.local.QuranDatabase
import com.quranreader.custom.data.local.TranslationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideQuranDatabase(
        @ApplicationContext context: Context
    ): QuranDatabase {
        return Room.databaseBuilder(
            context,
            QuranDatabase::class.java,
            "quran_database"
        )
        .addMigrations(
            QuranDatabase.MIGRATION_3_4,
            QuranDatabase.MIGRATION_4_5,
            QuranDatabase.MIGRATION_5_6,
            QuranDatabase.MIGRATION_6_7,
            QuranDatabase.MIGRATION_7_8,
            QuranDatabase.MIGRATION_8_9
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideDownloadedSurahDao(database: QuranDatabase): DownloadedSurahDao {
        return database.downloadedSurahDao()
    }

    @Provides
    @Singleton
    fun provideAyahTimingDao(database: QuranDatabase): AyahTimingDao {
        return database.ayahTimingDao()
    }

    @Provides
    @Singleton
    fun provideMemorizationSessionDao(database: QuranDatabase): MemorizationSessionDao {
        return database.memorizationSessionDao()
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(database: QuranDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    @Singleton
    fun provideAyahCoordinateDao(database: QuranDatabase): AyahCoordinateDao {
        return database.ayahCoordinateDao()
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideTranslationDao(database: QuranDatabase): TranslationDao {
        return database.translationDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // Increased for slow connections
            .readTimeout(120, TimeUnit.SECONDS)   // Increased for large image downloads
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)       // Enable automatic retry
            .build()
    }

    /** Retrofit instance pointing at quran.com API for v3.0 timing data (REQ-004). */
    @Provides
    @Singleton
    fun provideQuranComRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.quran.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAyahTimingApi(retrofit: Retrofit): AyahTimingApi {
        return retrofit.create(AyahTimingApi::class.java)
    }

    /**
     * Quran.com REST surface for translations + recitations editions.
     * Shares the same Retrofit instance as [AyahTimingApi] — they
     * both target `https://api.quran.com/`, so re-using the client
     * keeps OkHttp connections pooled.
     */
    @Provides
    @Singleton
    fun provideQuranComApi(retrofit: Retrofit): QuranComApi {
        return retrofit.create(QuranComApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTranslationEditionDao(database: QuranDatabase): TranslationEditionDao {
        return database.translationEditionDao()
    }

    @Provides
    @Singleton
    fun provideRecitationDao(database: QuranDatabase): RecitationDao {
        return database.recitationDao()
    }

    /**
     * Singleton ExoPlayer used by MemorizationViewModel for hifz looping playback.
     * AudioService builds its own ExoPlayer for page-level playback (separate instance).
     */
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideMemorizationExoPlayer(
        @ApplicationContext context: Context,
        cacheManager: AudioCacheManager
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(cacheManager.cacheAwareSourceFactory)
            .build()
    }
}
