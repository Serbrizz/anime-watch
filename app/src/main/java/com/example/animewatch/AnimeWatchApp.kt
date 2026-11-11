package com.example.animewatch

import android.app.Application
import com.example.animewatch.di.AppContainer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics

class AnimeWatchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Ogni crash porta l'UID di chi usava l'app, così si ritrova nella console fra quelli di più utenti.
        FirebaseAuth.getInstance().addAuthStateListener {
            FirebaseCrashlytics.getInstance().setUserId(it.currentUser?.uid.orEmpty())
        }
    }
}
