package com.example.animewatch

import android.app.Application
import com.example.animewatch.di.AppContainer

class AnimeWatchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
