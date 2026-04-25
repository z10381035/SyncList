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
        var listBackgroundColor by remember { mutableStateOf<Color?>(null) }
        var showColorPicker by remember { mutableStateOf(false) }
        var colorTarget by remember { mutableStateOf("Top Bar") } // "Top Bar" or "List Background"
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
            val baseColor = appBarColor ?: Color(0xFF6750A4)
            val luminance = 0.299 * baseColor.red + 0.587 * baseColor.green + 0.114 * baseColor.blue
            if (luminance > 0.5) Color.Black else Color.White
        }

        val listItemContentColor = remember(listBackgroundColor) {
            val baseColor = listBackgroundColor ?: Color.White
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
                title = "Pick $colorTarget Color",
                initialColor = if (colorTarget == "Top Bar") (appBarColor ?: Color(0xFF6750A4)) else (listBackgroundColor ?: Color.White),
                savedCustomColors = savedCustomColors,
                onDismiss = { showColorPicker = false },
                onColorSelected = { 
                    if (colorTarget == "Top Bar") appBarColor = it else listBackgroundColor = it
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
                                placeholder = { Text("Search...", color = contentColor.copy(alpha = 0.7f)) },
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
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        } else {
                            IconButton(onClick = { /* Handle navigation back */ }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, 
                                    contentDescription = "Back",
                                    modifier = Modifier.size(32.dp)
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
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = { undoRedoManager.redo() },
                                enabled = canRedo
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo, 
                                    contentDescription = "Redo",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
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
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box {
                                IconButton(onClick = { isMenuExpanded = true }) {
                                    Icon(
                                        Icons.Default.Menu, 
                                        contentDescription = "Menu",
                                        modifier = Modifier.size(32.dp)
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
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Top Bar Color") },
                                        leadingIcon = { 
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(appBarColor ?: MaterialTheme.colorScheme.primary)
                                                    .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            )
                                        },
                                        onClick = {
                                            colorTarget = "Top Bar"
                                            showColorPicker = true
                                            isMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("List Background") },
                                        leadingIcon = { 
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(listBackgroundColor ?: Color.White)
                                                    .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            )
                                        },
                                        onClick = {
                                            colorTarget = "List Background"
                                            showColorPicker = true
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(listBackgroundColor ?: MaterialTheme.colorScheme.surface)
            ) {
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
                            color = listItemContentColor.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last modified: ${formatTimestamp(lastModifiedTimestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = listItemContentColor.copy(alpha = 0.6f)
                        )
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = listItemContentColor.copy(alpha = 0.2f)
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
                        AddItemTile(
                            contentColor = listItemContentColor,
                            onClick = { 
                                addAtTop = true
                                showAddDialog = true 
                            }
                        )
                    }

                    val filteredItems = items.filter { it.text.contains(searchQuery, ignoreCase = true) }

                    items(filteredItems, key = { it.id }) { item ->
                        val isDragging = draggingItemId == item.id
                        ListItemRow(
                            item = item,
                            contentColor = listItemContentColor,
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
                        AddItemTile(
                            contentColor = listItemContentColor,
                            onClick = { 
                                addAtTop = false
                                showAddDialog = true 
                            }
                        )
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
    contentColor: Color,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
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
                    modifier = Modifier.scale(1.5f),
                    colors = CheckboxDefaults.colors(
                        checkedColor = contentColor,
                        uncheckedColor = contentColor.copy(alpha = 0.6f),
                        checkmarkColor = if (contentColor == Color.White) Color.Black else Color.White
                    )
                )
                Text(
                    text = item.text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = contentColor,
                        fontWeight = FontWeight.Medium
                    )
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
                            tint = contentColor.copy(alpha = 0.7f)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).offset(y = (-12).dp),
                            tint = contentColor.copy(alpha = 0.7f)
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
fun AddItemTile(contentColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = contentColor.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
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
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add item",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
    }
}

@Composable
fun ColorPickerDialog(
    title: String,
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
        Color(0xFFFFC0CB), // Pink
        Color.Black,
        Color.Gray,
        Color.White
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Rainbow Presets (2x5 Grid)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rainbowColors.take(5).forEach { color ->
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rainbowColors.drop(5).forEach { color ->
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
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 2. Custom Color Center (Wheel + Slider)
                Row(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorWheel(
                        modifier = Modifier.weight(1f),
                        initialColor = selectedColor,
                        onColorChange = { 
                            // Preserve V when moving wheel
                            val currentHsv = colorToHsv(selectedColor)
                            val newHsv = colorToHsv(it)
                            selectedColor = Color.hsv(newHsv[0], newHsv[1], currentHsv[2])
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    BrightnessSlider(
                        modifier = Modifier.width(36.dp).fillMaxHeight(),
                        initialColor = selectedColor,
                        onColorChange = { selectedColor = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // 3. Dashboard Row (Preview + Hex)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
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

                    Spacer(modifier = Modifier.width(16.dp))

                    val hexCode = remember(selectedColor) {
                        val r = (selectedColor.red * 255).roundToInt()
                        val g = (selectedColor.green * 255).roundToInt()
                        val b = (selectedColor.blue * 255).roundToInt()
                        "#" + r.toString(16).padStart(2, '0').uppercase() +
                              g.toString(16).padStart(2, '0').uppercase() +
                              b.toString(16).padStart(2, '0').uppercase()
                    }
                    
                    OutlinedTextField(
                        value = hexCode,
                        onValueChange = { input ->
                            val clean = input.removePrefix("#").trim()
                            if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                                try {
                                    val r = clean.substring(0, 2).toInt(16)
                                    val g = clean.substring(2, 4).toInt(16)
                                    val b = clean.substring(4, 6).toInt(16)
                                    selectedColor = Color(r, g, b)
                                } catch (e: Exception) { /* Invalid hex */ }
                            }
                        },
                        label = { Text("Hex Code") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.width(120.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // 4. Saved Palette
                Text(
                    "Saved custom colors",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
                var showOverwriteConfirm by remember { mutableStateOf<Int?>(null) }
                
                if (showDeleteConfirm != null) {
                    val colorToDelete = savedCustomColors[showDeleteConfirm!!]
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = null },
                        title = { Text("Confirm Deletion") },
                        text = { 
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Delete this saved color?")
                                Spacer(modifier = Modifier.height(20.dp))
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(colorToDelete ?: Color.Transparent)
                                        .border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                savedCustomColors[showDeleteConfirm!!] = null
                                showDeleteConfirm = null
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
                        }
                    )
                }

                if (showOverwriteConfirm != null) {
                    val existingColor = savedCustomColors[showOverwriteConfirm!!]
                    AlertDialog(
                        onDismissRequest = { showOverwriteConfirm = null },
                        title = { Text("Overwrite Color?") },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("This slot is already full. Would you like to overwrite it or use the saved color?")
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(existingColor ?: Color.Transparent)
                                                .border(1.dp, Color.LightGray, CircleShape)
                                        )
                                        Text("Saved", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.width(24.dp))
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, tint = Color.Gray)
                                    Spacer(modifier = Modifier.width(24.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(selectedColor)
                                                .border(1.dp, Color.LightGray, CircleShape)
                                        )
                                        Text("New", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                savedCustomColors[showOverwriteConfirm!!] = selectedColor
                                showOverwriteConfirm = null
                            }) { Text("Overwrite") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                selectedColor = existingColor ?: selectedColor
                                showOverwriteConfirm = null
                            }) { Text("Use Saved") }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 0 until 7) {
                        val color = savedCustomColors.getOrNull(i)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f) // Equal distribution to ensure perfect circles
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(color ?: Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (color != null) Color.LightGray else Color.Gray.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (color == null) {
                                            savedCustomColors[i] = selectedColor
                                        } else if (color != selectedColor) {
                                            showOverwriteConfirm = i
                                        }
                                    }
                            )
                            if (color != null) {
                                IconButton(
                                    onClick = { showDeleteConfirm = i },
                                    modifier = Modifier.size(40.dp).padding(top = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                }
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
                        hsv = floatArrayOf(angle, saturation, hsv[2]) // Preserve V
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
                            hsv = floatArrayOf(angle, saturation, hsv[2]) // Preserve V
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

@Composable
fun BrightnessSlider(
    modifier: Modifier = Modifier,
    initialColor: Color,
    onColorChange: (Color) -> Unit
) {
    val hsv = remember(initialColor) { colorToHsv(initialColor) }
    
    BoxWithConstraints(modifier = modifier) {
        val height = constraints.maxHeight.toFloat()
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hsv[0], hsv[1]) {
                    detectDragGestures { change, _ ->
                        val y = change.position.y.coerceIn(0f, height)
                        val brightness = 1f - (y / height)
                        onColorChange(Color.hsv(hsv[0], hsv[1], brightness))
                    }
                }
                .pointerInput(hsv[0], hsv[1]) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitFirstDown()
                            val y = event.position.y.coerceIn(0f, height)
                            val brightness = 1f - (y / height)
                            onColorChange(Color.hsv(hsv[0], hsv[1], brightness))
                        }
                    }
                }
        ) {
            // Background gradient from black to pure color (at current H/S)
            val brush = Brush.verticalGradient(
                colors = listOf(Color.hsv(hsv[0], hsv[1], 1f), Color.Black)
            )
            drawRect(brush = brush, size = size)
            drawRect(color = Color.Black, size = size, style = Stroke(width = 1.dp.toPx()))
            
            // Thumb
            val thumbY = (1f - hsv[2]) * size.height
            drawRect(
                color = Color.White,
                topLeft = Offset(0f, thumbY - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(0f, thumbY - 5.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 10.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
