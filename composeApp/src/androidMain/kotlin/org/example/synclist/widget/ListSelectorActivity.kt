package org.example.synclist.widget

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.content.Intent
import org.example.synclist.MainActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.example.synclist.ListRepository
import org.example.synclist.SettingsProvider
import org.example.synclist.ListMetadata
import org.example.synclist.AndroidSettingsRepository

class ListSelectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            SettingsProvider.initialize(AndroidSettingsRepository(this))
        } catch (e: Exception) {}

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    ListSelectorDialog(
                        context = this@ListSelectorActivity, 
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun ListSelectorDialog(context: android.content.Context, onDismiss: () -> Unit) {
    val repository = remember { ListRepository() }
    val settings = SettingsProvider.get()
    val scope = rememberCoroutineScope()
    
    var allLists by remember { mutableStateOf<List<ListMetadata>>(emptyList()) }
    var sortType by remember { mutableStateOf(settings.getString("listSortType", "Last Modified")) }
    
    // Naming prompt state
    var showNamingPrompt by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            allLists = repository.getAllLists().first()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load lists.", Toast.LENGTH_LONG).show()
        }
    }

    val sortedLists = remember(allLists, sortType) {
        when (sortType) {
            "Alphabetical" -> allLists.sortedBy { it.title }
            "Created Date" -> allLists.sortedByDescending { it.createdTimestamp }
            else -> allLists.sortedByDescending { it.lastModifiedTimestamp }
        }
    }

    if (showNamingPrompt) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            // Small delay ensures the dialog is fully composed and ready for focus
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { if (!isCreating) showNamingPrompt = false },
            title = { Text("New List Name") },
            text = {
                Column {
                    TextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        placeholder = { Text("List title...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        enabled = !isCreating
                    )
                    if (isCreating) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isCreating,
                    onClick = {
                        isCreating = true
                        // Ensure we use a stable scope and handle exceptions
                        scope.launch {
                            try {
                                // DELAY TO PREVENT RACE
                                kotlinx.coroutines.delay(500)
                                
                                val id = kotlinx.coroutines.withTimeout(10000) {
                                    repository.createList(if (newListName.isEmpty()) "New List" else newListName)
                                }
                                
                                // FORCE STORAGE SYNC
                                settings.saveString("widgetListId", id)
                                settings.saveString("currentListId", id)
                                
                                // Update the widget UI in background
                                SyncListWidget().updateAll(context)
                                
                                // Launch MainActivity with clear intent
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    putExtra("listId", id)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                context.startActivity(intent)
                                
                                onDismiss()
                            } catch (e: Exception) {
                                isCreating = false
                                Toast.makeText(context, "Creation failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(enabled = !isCreating, onClick = { showNamingPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select List", style = MaterialTheme.typography.titleLarge)
                    
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf("Last Modified", "Created Date", "Alphabetical").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        sortType = type
                                        settings.saveString("listSortType", type)
                                        showSortMenu = false
                                    },
                                    trailingIcon = {
                                        if (sortType == type) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            onClick = { showNamingPrompt = true },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Add list/note", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(sortedLists) { list ->
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete List?") },
                                text = { Text("Delete '${list.title}' and all items? This cannot be undone.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                repository.deleteListWithItems(list.id)
                                                allLists = repository.getAllLists().first()
                                                SyncListWidget().updateAll(context)
                                            }
                                            showDeleteConfirm = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Delete") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                                }
                            )
                        }

                        Card(
                            onClick = { 
                                settings.saveString("widgetListId", list.id)
                                settings.saveString("currentListId", list.id)
                                scope.launch {
                                    SyncListWidget().updateAll(context)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = list.title, modifier = Modifier.weight(1f))
                                IconButton(onClick = { showDeleteConfirm = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        )
    }
}
