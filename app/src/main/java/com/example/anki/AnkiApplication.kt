package com.example.anki

import android.app.Application
import com.example.anki.data.local.AnkiDatabase
import com.example.anki.data.preferences.PreferencesRepository
import com.example.anki.data.repository.AnkiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnkiApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AnkiDatabase by lazy { AnkiDatabase.getInstance(this) }
    val repository: AnkiRepository by lazy { AnkiRepository(database.ankiDao()) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.ensureSeeded()
        }
    }
}
