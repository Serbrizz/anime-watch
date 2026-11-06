package com.example.animewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.animewatch.ui.AnimeWatchRoot
import com.example.animewatch.ui.theme.AnimeWatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AnimeWatchApp).container
        setContent {
            AnimeWatchTheme {
                AnimeWatchRoot(container)
            }
        }
    }
}
