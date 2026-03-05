package com.lumera.app.data.model.stremio

import com.google.gson.JsonElement

// Tells us what the addon can do (stream, catalog, meta)
data class Manifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String? = null,

    // List<JsonElement> accepts both Cinemeta (Strings) and Torrentio (Objects)
    val resources: List<JsonElement>? = null,

    val types: List<String>? = null,
    val catalogs: List<CatalogManifest>? = null,
    val logo: String? = null
)

data class CatalogManifest(
    val type: String = "",
    val id: String = "",
    val name: String = "",
    val extra: List<CatalogExtra>? = null
)

data class CatalogExtra(
    val name: String = "",
    val isRequired: Boolean = false,
    val options: List<String>? = null
)