package org.example.synclist.widget

import org.example.synclist.AndroidSettingsRepository
import org.example.synclist.initAndroidSettings
import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import kotlinx.coroutines.flow.first
import org.example.synclist.ListRepository
import org.example.synclist.MainActivity
import org.example.synclist.R
import org.example.synclist.SettingsProvider
import org.example.synclist.ListMetadata

class SyncListWidget : GlanceAppWidget() {

    @androidx.glance.ExperimentalGlanceApi
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Initialize settings for the widget process
        try {
            initAndroidSettings(context)
            SettingsProvider.initialize(AndroidSettingsRepository(context))
        } catch (e: Exception) {
            // Already initialized or failed
        }

        val settings = SettingsProvider.get()
        val listId = settings.getString("widgetListId", "")
        val repository = ListRepository()
        
        var listMetadata: ListMetadata? = null
        var items: List<org.example.synclist.ListItem> = emptyList()

        try {
            val allLists = repository.getAllLists().first()
            listMetadata = if (listId.isNotEmpty()) {
                allLists.find { it.id == listId }
            } else {
                allLists.firstOrNull()
            }

            if (listMetadata != null) {
                items = repository.getItems(listMetadata.id).first()
            }
        } catch (e: Exception) {
            // Handle Firestore errors
        }

        val appBarColorHex = settings.getString("appBarColor", "null")
        val appBarColor = if (appBarColorHex != "null") Color(appBarColorHex.toLong().toInt()) else Color(0xFF6750A4)
        
        val luminance = (0.299 * appBarColor.red) + (0.587 * appBarColor.green) + (0.114 * appBarColor.blue)
        val contentColor = if (luminance > 0.5) Color.Black else Color.White

        // Define ActivityOptions to allow Background Activity Launch (BAL) on Android 14/15
        val activityOptionsBundle: Bundle? = if (Build.VERSION.SDK_INT >= 34) { 
            ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= 35) {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
            }.toBundle()
        } else {
            null
        }

        val listIdParam = ActionParameters.Key<String>("listId")
        val openAppAction = actionStartActivity<MainActivity>(
            parameters = actionParametersOf(listIdParam to (listMetadata?.id ?: "")),
            activityOptions = activityOptionsBundle
        )
        
        val openSelectorAction = actionStartActivity<ListSelectorActivity>()

        provideContent {
            // Root container
            // We use a scrollable Box or Column at the root if needed, but for simplicity and reliable clicks,
            // we use a standard Column for the list items instead of LazyColumn.
            // Glance's LazyColumn often has issues with click propagation in RemoteViews.
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color.White)
                    .appWidgetBackground()
                    .cornerRadius(16.dp)
                    .clickable(openAppAction) // Root clickable as a fallback
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Top Bar
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(appBarColor)
                            .padding(8.dp)
                            .clickable(openAppAction), // Title area clickable
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Title Area
                        Text(
                            text = listMetadata?.title ?: "No list selected",
                            modifier = GlanceModifier.defaultWeight().padding(end = 4.dp),
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(contentColor),
                                fontSize = 16.sp,
                                fontWeight = androidx.glance.text.FontWeight.Bold
                            ),
                        )
                        
                        // Hamburger Menu
                        Image(
                            provider = ImageProvider(R.drawable.ic_menu_hamburger),
                            contentDescription = "Menu",
                            modifier = GlanceModifier
                                .size(32.dp)
                                .padding(4.dp)
                                .clickable(openSelectorAction),
                        )
                    }

                    // Content Area: Use a simple Column to ensure clicks work reliably.
                    // If the list is very long, it will be clipped, but clicks will ALWAYS work.
                    // This is a tradeoff for functional interactivity.
                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(openAppAction), // Content area clickable
                    ) {
                        items.take(15).forEach { item ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(openAppAction),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val icon = if (item.isChecked) R.drawable.ic_check_checked else R.drawable.ic_check_empty
                                Image(
                                    provider = ImageProvider(icon),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(18.dp),
                                )
                                Spacer(GlanceModifier.width(8.dp))
                                Text(
                                    text = item.text,
                                    maxLines = 1,
                                    style = TextStyle(fontSize = 14.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
