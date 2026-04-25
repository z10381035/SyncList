package org.example.synclist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        val repository = remember { ListRepository() }
        val viewModel: ListViewModel = viewModel { ListViewModel(repository) }
        val items by viewModel.items.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        var listTitle by remember { mutableStateOf("SyncList") }
        var isEditingTitle by remember { mutableStateOf(false) }
        var appBarColor by remember { mutableStateOf<Color?>(null) }
        var showColorPicker by remember { mutableStateOf(false) }
        val savedCustomColors = remember { mutableStateListOf<Color?>(null, null, null, null, null, null, null) }
        
        var isMenuExpanded by remember { mutableStateOf(false) }
        var isSearchMode by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var showMetadata by remember { mutableStateOf(true) }

        val undoRedoManager = GlobalUndoRedoManager
        var previousTitle by remember { mutableStateOf(listTitle) }
        
        val canUndo = globalCanUndo
        val canRedo = globalCanRedo

        val contentColor = remember(appBarColor) {
            val baseColor = appBarColor ?: Color(0xFF6750A4) // Material3 default primary
            val luminance = 0.299 * baseColor.red + 0.587 * baseColor.green + 0.114 * baseColor.blue
            if (luminance > 0.5) Color.Black else Color.White
        }

        var createdTimestamp by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
        var lastModifiedTimestamp by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }

        val lazyListState = rememberLazyListState()
        var draggingItemId by remember { mutableStateOf<String?>(null) }
        var initialDraggingIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableStateOf(0f) }
        
        // Use updated states for the coroutine loop to avoid stale values
        val currentDragOffset by rememberUpdatedState(dragOffset)

        // --- AUTOSCROLL LOOP (REFINED) ---
        LaunchedEffect(draggingItemId) {
            if (draggingItemId == null) return@LaunchedEffect
            while (true) {
                if (draggingItemId == null) break
                
                val layoutInfo = lazyListState.layoutInfo
                val draggingItemInfo = layoutInfo.visibleItemsInfo.find { it.key == draggingItemId }
                
                if (draggingItemInfo != null) {
                    val viewPortHeight = layoutInfo.viewportSize.height
                    val threshold = viewPortHeight * 0.2f
                    
                    val top = draggingItemInfo.offset + currentDragOffset
                    val bottom = top + draggingItemInfo.size
                    
                    if (top < threshold) {
                        val speed = (threshold - top) / 3f
                        val scrolled = lazyListState.scrollBy(-speed)
                        dragOffset += scrolled // Update the actual state
                    } else if (bottom > viewPortHeight - threshold) {
                        val speed = (bottom - (viewPortHeight - threshold)) / 3f
                        val scrolled = lazyListState.scrollBy(speed)
                        dragOffset += scrolled // Update the actual state
                    }
                }
                delay(10)
            }
        }

        // --- REAL-TIME REORDERING (REFINED) ---
        LaunchedEffect(draggingItemId, dragOffset) {
            val draggingId = draggingItemId ?: return@LaunchedEffect
            val layoutInfo = lazyListState.layoutInfo
            val draggingItem = layoutInfo.visibleItemsInfo.find { it.key == draggingId } ?: return@LaunchedEffect
            
            val itemCenter = draggingItem.offset + (draggingItem.size / 2) + dragOffset
            
            val targetItem = layoutInfo.visibleItemsInfo.find { item ->
                item.key != draggingId && 
                item.key is String && 
                itemCenter > item.offset && 
                itemCenter < item.offset + item.size
            }
            
            if (targetItem != null) {
                val fromIndex = items.indexOfFirst { it.id == draggingId }
                val targetId = targetItem.key as String
                val toIndex = items.indexOfFirst { it.id == targetId }

                if (fromIndex != -1 && toIndex != -1) {
                    val offsetDiff = draggingItem.offset - targetItem.offset
                    dragOffset += offsetDiff
                    viewModel.moveItem(fromIndex, toIndex)
                }
            }
        }

        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = appBarColor ?: Color(0xFF6750A4),
                savedCustomColors = savedCustomColors,
                onDismiss = { showColorPicker = false },
                onColorSelected = { 
                    appBarColor = it
                    showColorPicker = false
                }
            )
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = appBarColor ?: MaterialTheme.colorScheme.primary,
                        titleContentColor = contentColor,
                        navigationIconContentColor = contentColor,
                        actionIconContentColor = contentColor
                    ),
                    title = {
                        if (isSearchMode) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search items...", color = contentColor.copy(alpha = 0.7f)) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = contentColor
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (isEditingTitle) {
                            TextField(
                                value = listTitle,
                                onValueChange = { listTitle = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = contentColor
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = listTitle,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    navigationIcon = {
                        if (isSearchMode) {
                            IconButton(onClick = { 
                                isSearchMode = false 
                                searchQuery = ""
                            }) {
                                Icon(
                                    Icons.Default.Close, 
                                    contentDescription = "Close Search",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else if (isEditingTitle) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(appBarColor ?: MaterialTheme.colorScheme.primary)
                                    .border(2.dp, contentColor, RoundedCornerShape(4.dp))
                                    .clickable { showColorPicker = true }
                            )
                        } else {
                            IconButton(onClick = { /* Handle navigation back */ }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, 
                                    contentDescription = "Back",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        if (!isSearchMode) {
                            IconButton(
                                onClick = { undoRedoManager.undo() },
                                enabled = canUndo
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo, 
                                    contentDescription = "Undo",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(
                                onClick = { undoRedoManager.redo() },
                                enabled = canRedo
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo, 
                                    contentDescription = "Redo",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(onClick = { 
                                if (isEditingTitle) {
                                    if (listTitle != previousTitle) {
                                        undoRedoManager.add(RenameAction(previousTitle, listTitle) { listTitle = it })
                                    }
                                    isEditingTitle = false
                                } else {
                                    previousTitle = listTitle
                                    isEditingTitle = true
                                }
                            }) {
                                Icon(
                                    if (isEditingTitle) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isEditingTitle) "Done" else "Edit",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Box {
                                IconButton(onClick = { isMenuExpanded = true }) {
                                    Icon(
                                        Icons.Default.Menu, 
                                        contentDescription = "Menu",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = isMenuExpanded,
                                    onDismissRequest = { isMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Search") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        onClick = {
                                            isSearchMode = true
                                            isMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (showMetadata) "Hide Metadata" else "Show Metadata") },
                                        onClick = {
                                            showMetadata = !showMetadata
                                            isMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Metadata Header
                if (showMetadata) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Date created: ${formatTimestamp(createdTimestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last modified: ${formatTimestamp(lastModifiedTimestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                var showAddDialog by remember { mutableStateOf(false) }
                var addAtTop by remember { mutableStateOf(false) }

                if (showAddDialog) {
                    AddItemDialog(
                        onAdd = { text ->
                            scope.launch {
                                val pos = viewModel.getNextPosition(addAtTop)
                                val newItem = ListItem(
                                    id = (Clock.System.now().toEpochMilliseconds() + (0..1000).random()).toString(),
                                    text = text,
                                    timestamp = Clock.System.now().toEpochMilliseconds(),
                                    position = pos
                                )
                                // Record in history INSTANTLY
                                undoRedoManager.add(AddAction(newItem, viewModel))
                                // Save to Firebase in background
                                viewModel.addItemDirectly(newItem)
                                lastModifiedTimestamp = Clock.System.now().toEpochMilliseconds()
                            }
                            showAddDialog = false
                        },
                        onDismiss = { showAddDialog = false }
                    )
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        AddItemTile(onClick = { 
                            addAtTop = true
                            showAddDialog = true 
                        })
                    }

                    val filteredItems = items.filter { it.text.contains(searchQuery, ignoreCase = true) }

                    items(filteredItems, key = { it.id }) { item ->
                        val isDragging = draggingItemId == item.id
                        ListItemRow(
                            item = item,
                            onToggle = { 
                                undoRedoManager.add(ToggleAction(item.id, item.isChecked, !item.isChecked, viewModel))
                                viewModel.toggleItem(item)
                                lastModifiedTimestamp = Clock.System.now().toEpochMilliseconds()
                            },
                            onDelete = { 
                                undoRedoManager.add(DeleteAction(item, viewModel))
                                viewModel.deleteItem(item)
                                lastModifiedTimestamp = Clock.System.now().toEpochMilliseconds()
                            },
                            isEditMode = isEditingTitle,
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = null
                                )
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                    alpha = if (isDragging) 0.8f else 1.0f
                                },
                            handleModifier = Modifier.pointerInput(item.id) {
                                detectDragGestures(
                                    onDragStart = { 
                                        draggingItemId = item.id
                                        initialDraggingIndex = lazyListState.layoutInfo.visibleItemsInfo
                                            .find { it.key == item.id }?.index
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                    },
                                    onDragEnd = { 
                                        if (isEditingTitle && draggingItemId != null && initialDraggingIndex != null) {
                                            val finalIndex = items.indexOfFirst { it.id == draggingItemId }
                                            if (finalIndex != -1 && finalIndex != (initialDraggingIndex!! - 1)) {
                                                undoRedoManager.add(MoveAction(initialDraggingIndex!! - 1, finalIndex, viewModel))
                                            }
                                        }
                                        draggingItemId = null
                                        initialDraggingIndex = null
                                        dragOffset = 0f
                                        lastModifiedTimestamp = Clock.System.now().toEpochMilliseconds()
                                    },
                                    onDragCancel = {
                                        draggingItemId = null
                                        initialDraggingIndex = null
                                        dragOffset = 0f
                                    }
                                )
                            }
                        )
                    }

                    item {
                        AddItemTile(onClick = { 
                            addAtTop = false
                            showAddDialog = true 
                        })
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    
    val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val day = dateTime.dayOfMonth.toString().padStart(2, '0')
    val year = dateTime.year
    
    val hour24 = dateTime.hour
    val amPm = if (hour24 >= 12) "PM" else "AM"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    val minute = dateTime.minute.toString().padStart(2, '0')
    
    return "$month $day, $year $hour12:$minute $amPm"
}

@Composable
fun ListItemRow(
    item: ListItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zone A: Action Zone
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle() }
                    .padding(vertical = 16.dp, horizontal = 12.dp),
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
                        .padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (isEditMode) {
                // Zone B: Move Zone (Center 20% approx)
                Box(
                    modifier = handleModifier
                        .width(64.dp)
                        .fillMaxHeight()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).offset(y = (-12).dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Zone C: Delete Zone (Right 20% approx)
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { onDelete() }
                        .fillMaxHeight()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddItemDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Item") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Item name...") },
                modifier = Modifier.focusRequester(focusRequester),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
        },
        confirmButton = {
            Button(onClick = {
                if (text.isNotBlank()) {
                    onAdd(text)
                    text = ""
                }
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddItemTile(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add item",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    savedCustomColors: MutableList<Color?>,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }

    val rainbowColors = listOf(
        Color.Red,
        Color(0xFFFFA500), // Orange
        Color.Yellow,
        Color.Green,
        Color.Blue,
        Color(0xFF800080), // Purple
        Color(0xFFFFC0CB)  // Pink
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color Studio", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Rainbow Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rainbowColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                .clickable { selectedColor = color }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 2. Custom Color Preview
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(selectedColor)
                        .border(2.dp, Color.Black, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val luminance = 0.299 * selectedColor.red + 0.587 * selectedColor.green + 0.114 * selectedColor.blue
                    Text(
                        "Preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (luminance > 0.5) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // 3. The Color Wheel
                ColorWheel(
                    modifier = Modifier.size(220.dp),
                    initialColor = selectedColor,
                    onColorChange = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                // 4. Saved Palette
                Text(
                    "Saved custom colors",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 0 until 7) {
                        val color = savedCustomColors.getOrNull(i)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color ?: Color.Transparent)
                                .border(1.dp, if (color != null) Color.LightGray else Color.Gray.copy(alpha = 0.3f), CircleShape)
                                .pointerInput(i) {
                                    detectDragGestures(
                                        onDragStart = { /* No-op to enable other gestures if needed */ },
                                        onDragEnd = { /* No-op */ },
                                        onDragCancel = { /* No-op */ },
                                        onDrag = { _, _ -> /* No-op */ }
                                    )
                                }
                                .pointerInput(i) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitFirstDown()
                                            
                                            // Wait for up or timeout for long press
                                            val upEvent = withTimeoutOrNull<PointerInputChange?>(500) {
                                                waitForUpOrCancellation()
                                            }
                                            
                                            if (upEvent == null) {
                                                // Timeout reached, it's a long press
                                                savedCustomColors[i] = null
                                            } else {
                                                // Up event received before timeout
                                                if (color != null) {
                                                    selectedColor = color
                                                } else {
                                                    savedCustomColors[i] = selectedColor
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Long press to delete",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(selectedColor) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    initialColor: Color,
    onColorChange: (Color) -> Unit
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val radius = constraints.maxWidth / 2f
        val center = Offset(radius, radius)

        // Helper to convert Color to HSV
        fun colorToHsv(color: Color): FloatArray {
            val r = color.red
            val g = color.green
            val b = color.blue
            val max = max(r, max(g, b))
            val min = min(r, min(g, b))
            val delta = max - min
            
            var h = 0f
            if (delta != 0f) {
                h = when (max) {
                    r -> (g - b) / delta + (if (g < b) 6 else 0)
                    g -> (b - r) / delta + 2
                    else -> (r - g) / delta + 4
                }
                h /= 6f
            }
            
            val s = if (max == 0f) 0f else delta / max
            val v = max
            
            return floatArrayOf(h * 360f, s, v)
        }

        // Initialize HSV from initialColor
        var hsv by remember(initialColor) { mutableStateOf(colorToHsv(initialColor)) }

        val thumbOffset = remember(hsv, radius) {
            val angle = (hsv[0] * PI.toFloat() / 180f)
            val dist = hsv[1] * radius
            Offset(
                x = center.x + dist * cos(angle),
                y = center.y + dist * sin(angle)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val touch = change.position
                        val dx = touch.x - center.x
                        val dy = touch.y - center.y
                        val dist = min(sqrt((dx * dx + dy * dy).toDouble()), radius.toDouble()).toFloat()
                        
                        var angle = atan2(dy, dx) * 180f / PI.toFloat()
                        if (angle < 0) angle += 360f
                        
                        val saturation = dist / radius
                        hsv = floatArrayOf(angle, saturation, 1f)
                        onColorChange(Color.hsv(hsv[0], hsv[1], hsv[2]))
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitFirstDown()
                            val touch = event.position
                            val dx = touch.x - center.x
                            val dy = touch.y - center.y
                            val dist = min(sqrt((dx * dx + dy * dy).toDouble()), radius.toDouble()).toFloat()
                            
                            var angle = atan2(dy, dx) * 180f / PI.toFloat()
                            if (angle < 0) angle += 360f
                            
                            val saturation = dist / radius
                            hsv = floatArrayOf(angle, saturation, 1f)
                            onColorChange(Color.hsv(hsv[0], hsv[1], hsv[2]))
                        }
                    }
                }
        ) {
            val hueColors = listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan, 
                Color.Blue, Color.Magenta, Color.Red
            )
            drawCircle(brush = Brush.sweepGradient(hueColors, center))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radius
                )
            )
            drawCircle(
                color = Color.Black,
                radius = 10.dp.toPx(),
                center = thumbOffset,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = thumbOffset,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
