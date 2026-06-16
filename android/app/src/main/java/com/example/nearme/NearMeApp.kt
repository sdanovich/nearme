package com.example.nearme

import android.app.Application
import androidx.room.Room
import com.example.nearme.data.ApiClient
import com.example.nearme.data.StationRepository
import com.example.nearme.data.local.AppDatabase

/** Holds app-wide singletons (database, repository). */
class NearMeApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: StationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, AppDatabase::class.java, "nearme.db")
            .fallbackToDestructiveMigration()
            .build()
        repository = StationRepository(ApiClient.api, database.placeDao())
    }
}
