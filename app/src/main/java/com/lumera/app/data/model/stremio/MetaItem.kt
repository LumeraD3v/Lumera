package com.lumera.app.data.model.stremio

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class MetaResponse(
    val meta: MetaItem
)

@Immutable
data class MetaItem(
    val id: String = "",
    val type: String = "", // "movie", "series"
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String>? = null,
    val videos: List<MetaVideo>? = null,
    @Transient val progress: Float = 0f // Watch progress (0.0–1.0), used by Continue Watching
)

@Immutable
data class MetaVideo(
    val id: String = "",

    @SerializedName(value = "title", alternate = ["name"])
    val title: String = "Episode",

    val released: String? = null,
    val thumbnail: String? = null,
    val season: Int = 0,
    val episode: Int = 0
)


@Immutable
data class CatalogResponse(
    val metas: List<MetaItem>? = null,
    val hasMore: Boolean? = null
)
