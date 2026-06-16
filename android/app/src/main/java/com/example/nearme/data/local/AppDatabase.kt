package com.example.nearme.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    /** Live stream the UI observes — emits whenever this category's cache changes. */
    @Query("SELECT * FROM cached_place WHERE category = :category ORDER BY distanceMeters ASC")
    fun observePlaces(category: String): Flow<List<CachedPlace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaces(places: List<CachedPlace>)

    /** Replace this category's cached set so stale places from a prior location don't linger. */
    @Query("DELETE FROM cached_place WHERE category = :category")
    suspend fun clearCategory(category: String)
}

@Database(entities = [CachedPlace::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
}
