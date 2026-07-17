package org.example.synclist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        
        // Ensure SettingsProvider is initialized before the ViewModel is created
        try {
            SettingsProvider.initialize(AndroidSettingsRepository(this))
        } catch (e: Exception) {
            // Log if needed, or fallback
        }

        // Handle potential deep link from widget
        intent.getStringExtra("listId")?.let { listId ->
            SettingsProvider.get().saveString("currentListId", listId)
        }

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("listId")?.let { listId ->
            SettingsProvider.get().saveString("currentListId", listId)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
