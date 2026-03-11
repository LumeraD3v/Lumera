package com.lumera.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,

    val themeId: String = "void",

    val avatarRef: String = "avatar_1",

    val roundCorners: Boolean = true,
    val hubRoundCorners: Boolean = true,
    val continueWatchingShape: String = "poster",  // "poster" or "landscape"
    val navPosition: String = "left",
    val splashEnabled: Boolean = true,

    val homeTabLayout: String = "cinematic",
    val moviesTabLayout: String = "cinematic",
    val seriesTabLayout: String = "cinematic",

    val homeHeroCategory: String? = null,
    val homeHeroPosterCount: Int = 10,
    val homeHeroAutoScrollSeconds: Int = 0,

    val moviesHeroCategory: String? = null,
    val moviesHeroPosterCount: Int = 10,
    val moviesHeroAutoScrollSeconds: Int = 0,

    val seriesHeroCategory: String? = null,
    val seriesHeroPosterCount: Int = 10,
    val seriesHeroAutoScrollSeconds: Int = 0,

    val tunnelingEnabled: Boolean = false,
    val mapDV7ToHevc: Boolean = false,
    val decoderPriority: Int = 1,       // 0=device only, 1=prefer device, 2=prefer app
    val frameRateMatching: Boolean = false,
    val playerPreference: String = "internal",  // "internal", "external", "ask"
    val autoplayNextEpisode: Boolean = false,
    val autoplayThresholdMode: String = "percentage",  // "percentage" or "time"
    val autoplayThresholdPercent: Int = 95,             // 50..99
    val autoplayThresholdSeconds: Int = 30,             // 10..300
    val autoSelectSource: Boolean = false,
    val rememberSourceSelection: Boolean = true,
    val sourceSortingEnabled: Boolean = true,
    val sourceSortPrimary: String = "quality",    // "quality", "size", "seeds"
    val sourceSortSecondary: String = "size",     // "quality", "size", "seeds"
    val sourceEnabledQualities: String = "4k,1080p,720p,unknown",
    val sourceExcludePhrases: String = "",
    val skipIntro: Boolean = true,

    val preferredAudioLanguage: String = "",
    val preferredAudioLanguageSecondary: String = "",
    val preferredSubtitleLanguage: String = "",
    val preferredSubtitleLanguageSecondary: String = "",

    val subtitleSize: Int = 100,                     // 50-200%
    val subtitleOffset: Int = 0,                     // -20 to 20%
    val subtitleTextColor: Long = 0xFFFFFFFFL,       // White (ARGB)
    val subtitleBackgroundColor: Long = 0x00000000L  // Transparent
)