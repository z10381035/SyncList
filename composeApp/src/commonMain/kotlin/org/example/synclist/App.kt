package org.example.synclist

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        val repository = remember { ListRepository() }
        val viewModel: ListViewModel = viewModel { ListViewModel(repository) }
        val items by viewModel.items.collectAsStateWithLifecycle()

        val lazyListState = rememberLazyListState()
        var draggingItemId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(0f) }

        LaunchedEffect(draggingItemId, dragOffset) {
            val draggingId = draggingItemId ?: return@LaunchedEffect
            val layoutInfo = lazyListState.layoutInfo
            val draggingItem = layoutInfo.visibleItemsInfo.find { it.key == draggingId } ?: return@LaunchedEffect
            
            // Calculate item center in viewport coordinates
            val itemCenter = draggingItem.offset + (draggingItem.size / 2) + dragOffset
            
            val targetItem = layoutInfo.visibleItemsInfo.find { item ->
                item.key != draggingId && 
                itemCenter > item.offset && 
                itemCenter < item.offset + item.size
            }
            
            if (targetItem != null) {
                // Adjust dragOffset by the difference in positions to prevent jumping
                val offsetDiff = draggingItem.offset - targetItem.offset
                dragOffset += offsetDiff
                
                viewModel.moveItem(draggingItem.index, targetItem.index)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text("SyncList") })
            },
            floatingActionButton = {
                AddItemDialog(onAdd = { viewModel.addItem(it) })
            }
        ) { padding ->
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                lazyListState.layoutInfo.visibleItemsInfo
                                    .find { item -> 
                                        offset.y.toInt() in item.offset..(item.offset + item.size)
                                    }?.let { 
                                        draggingItemId = it.key as? String 
                                    }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                            },
                            onDragEnd = { 
                                draggingItemId = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingItemId = null
                                dragOffset = 0f
                            }
                        )
                    },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val isDragging = draggingItemId == item.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                alpha = if (isDragging) 0.8f else 1.0f
                            }
                    ) {
                        ListItemRow(
                            item = item,
                            onToggle = { viewModel.toggleItem(item) },
                            onDelete = { viewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ListItemRow(item: ListItem, onToggle: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(1.5f)
            )
            Text(
                text = item.text,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .fillMaxHeight(),
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun AddItemDialog(onAdd: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    FloatingActionButton(onClick = { showDialog = true }) {
        Icon(Icons.Default.Add, contentDescription = "Add Item")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add New Item") },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Item name...") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (text.isNotBlank()) {
                        onAdd(text)
                        text = ""
                        showDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}
