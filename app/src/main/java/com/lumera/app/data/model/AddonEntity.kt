package com.lumera.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "addons")
data class AddonEntity(
    @PrimaryKey val transportUrl: String,

    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val iconUrl: String?,
    val isTrusted: Boolean = false,
    val isEnabled: Boolean = true,
    val nickname: String? = null,

    val catalogsJson: String = "[]",

    val supportsMeta: Boolean = false,
    val supportsStream: Boolean = true,
    val typesJson: String = "[]",
    val idPrefixesJson: String = "[]",

    val sortOrder: Int = 999
)