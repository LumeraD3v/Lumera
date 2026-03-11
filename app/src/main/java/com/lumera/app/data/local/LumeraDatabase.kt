package com.lumera.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.HubRowEntity
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.ThemeEntity
import com.lumera.app.data.model.WatchHistoryEntity


@Database(
    entities = [
        AddonEntity::class,
        ProfileEntity::class,
        WatchHistoryEntity::class,
        CatalogConfigEntity::class,
        ThemeEntity::class,
        HubRowEntity::class,
        HubRowItemEntity::class
    ],
    version = 34
)
abstract class LumeraDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
}