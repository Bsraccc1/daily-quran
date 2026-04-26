package com.quranreader.custom.di

import android.content.Context
import androidx.room.Room
import com.quranreader.custom.data.local.ayahinfo.AyahInfoDatabase
import com.quranreader.custom.data.local.ayahinfo.GlyphDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that exposes the bundled ayah-info database (per-glyph
 * pixel coordinates for the 604 page images). Pure read-only — there
 * is no migration story because the database is always overwritten
 * from the asset on a bundle bump.
 */
@Module
@InstallIn(SingletonComponent::class)
object AyahInfoModule {

    @Provides
    @Singleton
    fun provideAyahInfoDatabase(
        @ApplicationContext context: Context,
    ): AyahInfoDatabase = Room.databaseBuilder(
        context,
        AyahInfoDatabase::class.java,
        AyahInfoDatabase.DB_NAME,
    )
        .createFromAsset(AyahInfoDatabase.DB_ASSET_PATH)
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideGlyphDao(db: AyahInfoDatabase): GlyphDao = db.glyphDao()
}
