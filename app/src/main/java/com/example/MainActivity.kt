package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CachedBranding
import com.example.data.PreferencesRepository
import com.example.data.ThemeMode
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.StudySparkApp
import com.example.ui.theme.StudySparkTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge layout
        enableEdgeToEdge()

        val preferencesRepository = PreferencesRepository(applicationContext)

        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val cachedBranding by preferencesRepository.cachedBranding.collectAsStateWithLifecycle(
                initialValue = CachedBranding()
            )
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            StudySparkTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val auth = remember { Firebase.auth }
                    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(
                        initialValue = auth.currentUser
                    )

                    val user = currentUser
                    if (user == null) {
                        AuthScreen(
                            cachedBranding = cachedBranding,
                            onAuthSuccess = { /* AuthStateListener drives navigation reactively */ }
                        )
                    } else {
                        // Key by uid so account switching cleanly disposes old Firestore listeners
                        key(user.uid) {
                            StudySparkApp(
                                firebaseUser = user,
                                preferencesRepository = preferencesRepository,
                                themeMode = themeMode
                            )
                        }
                    }
                }
            }
        }
    }
}
