package com.example.nearme

import android.app.Application
import androidx.room.Room
import com.example.nearme.data.ApiClient
import com.example.nearme.data.BaseUrlProvider
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
        // Load any previously-discovered backend URL before the API client is
        // built (ApiClient.api is lazy and first touched just below).
        BaseUrlProvider.init(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, "nearme.db")
            .fallbackToDestructiveMigration()
            .build()
        repository = StationRepository(ApiClient.api, database.placeDao())
    }
}
