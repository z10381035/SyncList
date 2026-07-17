@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@file:Suppress("DEPRECATION")
package org.example.synclist

import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
@Preview
fun App() {
    MaterialTheme {
        val repository = remember { ListRepository() }
        val viewModel: ListViewModel = viewModel { ListViewModel(repository) }
        val items by viewModel.items.collectAsStateWithLifecycle()
        val currentMetadata by viewModel.currentListMetadata.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.syncWithSettings()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val undoRedoManager = GlobalUndoRedoManager
        val canUndo = globalCanUndo
        val canRedo = globalCanRedo

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(viewModel.isEditingTitle) {
            if (viewModel.isEditingTitle) {
                focusRequester.requestFocus()
            }
        }

        val contentColor = remember(viewModel.appBarColor) {
            val baseColor = viewModel.appBarColor ?: Color(0xFF6750A4)
            val luminance = (0.299 * baseColor.red) + (0.587 * baseColor.green) + (0.114 * baseColor.blue)
            if (luminance > 0.5) Color.Black else Color.White
        }

        val listItemContentColor = remember(viewModel.listBackgroundColor, viewModel.isDarkMode) {
            val baseColor = if (viewModel.isDarkMode) Color.Black else (viewModel.listBackgroundColor ?: Color.White)
            val luminance = (0.299 * baseColor.red) + (0.587 * baseColor.green) + (0.114 * baseColor.blue)
            if (luminance > 0.5) Color.Black else Color.White
        }

        val lazyListState = rememberLazyListState()
        var dragOffset by remember { mutableStateOf(0f) }
        val currentDragOffset by rememberUpdatedState(dragOffset)

        LaunchedEffect(viewModel.draggingItemId) {
            if (viewModel.draggingItemId == null) return@LaunchedEffect
            while (true) {
                if (viewModel.draggingItemId == null) break
                val layoutInfo = lazyListState.layoutInfo
                val draggingItemInfo = layoutInfo.visibleItemsInfo.find { it.key == viewModel.draggingItemId }
                if (draggingItemInfo != null) {
                    val viewPortHeight = layoutInfo.viewportSize.height
                    val threshold = viewPortHeight * 0.2f
                    val top = draggingItemInfo.offset + currentDragOffset
                    val bottom = top + draggingItemInfo.size
                    if (top < threshold) {
                        val speed = (threshold - top) / 3f
                        val scrolled = lazyListState.scrollBy(-speed)
                        dragOffset += scrolled
                    } else if (bottom > viewPortHeight - threshold) {
                        val speed = (bottom - (viewPortHeight - threshold)) / 3f
                        val scrolled = lazyListState.scrollBy(speed)
                        dragOffset += scrolled
                    }
                }
                delay(10)
            }
        }

        LaunchedEffect(viewModel.draggingItemId, dragOffset) {
            val draggingId = viewModel.draggingItemId ?: return@LaunchedEffect
            val layoutInfo = lazyListState.layoutInfo
            val draggingItem = layoutInfo.visibleItemsInfo.find { it.key == draggingId } ?: return@LaunchedEffect
            val itemCenter = draggingItem.offset + (draggingItem.size / 2) + dragOffset
            val targetItem = layoutInfo.visibleItemsInfo.find { item ->
                item.key != draggingId && item.key is String && itemCenter > item.offset && itemCenter < item.offset + item.size
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

        if (viewModel.showColorPicker) {
            ColorPickerDialog(
                title = "Pick ${viewModel.colorTarget} Color",
                initialColor = if (viewModel.colorTarget == "Top Bar") (viewModel.appBarColor ?: Color(0xFF6750A4)) else (viewModel.listBackgroundColor ?: Color.White),
                savedCustomColors = viewModel.savedCustomColors,
                viewModel = viewModel,
                onDismiss = { viewModel.updateShowColorPicker(show = false) },
                onColorSelected = { selected ->
                    if (viewModel.colorTarget == "Top Bar") viewModel.updateAppBarColor(selected) else viewModel.updateListBackgroundColor(selected)
                    viewModel.updateShowColorPicker(show = false)
                }
            )
        }

        val effectiveCheckmarkColor = if (viewModel.isCheckmarkHighContrast) listItemContentColor else (viewModel.checkmarkColor ?: listItemContentColor)
        val effectiveCrossOutColor = if (viewModel.isCheckmarkHighContrast) listItemContentColor else (viewModel.crossOutColor ?: effectiveCheckmarkColor)

        if (viewModel.currentScreen == Screen.Settings) {
            SettingsPage(
                viewModel = viewModel,
                contentColor = contentColor,
                onBack = { viewModel.updateCurrentScreen(Screen.List) }
            )
        } else {
            Scaffold(
                topBar = {
                    Column(modifier = Modifier.background(viewModel.appBarColor ?: MaterialTheme.colorScheme.primary).statusBarsPadding()) {
                        CenterAlignedTopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = contentColor, navigationIconContentColor = contentColor, actionIconContentColor = contentColor),
                            title = {
                                if (viewModel.isSearchMode) {
                                    BasicTextField(
                                        value = viewModel.searchQuery,
                                        onValueChange = { viewModel.updateSearchQuery(it) },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = contentColor),
                                        cursorBrush = Brush.verticalGradient(listOf(contentColor, contentColor)),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                if (viewModel.searchQuery.isEmpty()) {
                                                    Text("Search...", color = contentColor.copy(alpha = 0.7f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                } else if (viewModel.isEditingTitle) {
                                    BasicTextField(
                                        value = viewModel.listTitle,
                                        onValueChange = { viewModel.updateListTitle(it) },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = contentColor),
                                        cursorBrush = Brush.verticalGradient(listOf(contentColor, contentColor)),
                                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                        decorationBox = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { innerTextField() }
                                        }
                                    )
                                } else {
                                    Text(text = viewModel.listTitle, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                            },
                            navigationIcon = {
                                if (viewModel.isSearchMode) {
                                    IconButton(onClick = { viewModel.updateSearchMode(false); viewModel.updateSearchQuery("") }) { Icon(Icons.Default.Close, contentDescription = "Close Search", modifier = Modifier.size(32.dp)) }
                                } else if (viewModel.isEditingTitle) {
                                    IconButton(onClick = { viewModel.updateEditingTitle(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Edit", modifier = Modifier.size(32.dp)) }
                                } else {
                                    IconButton(onClick = { /* Could exit app or handle deep nav */ }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(32.dp)) }
                                }
                            },
                            actions = {
                                if (viewModel.isCompactUi && !viewModel.isSearchMode) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { if (viewModel.isEditingTitle) { if (viewModel.listTitle != viewModel.previousTitle) undoRedoManager.add(RenameAction(viewModel.previousTitle, viewModel.listTitle) { viewModel.updateListTitle(it) }); viewModel.updateEditingTitle(false) } else { viewModel.updatePreviousTitle(viewModel.listTitle); viewModel.updateEditingTitle(true) } }) { Icon(if (viewModel.isEditingTitle) Icons.Default.Check else Icons.Default.Edit, contentDescription = "Edit", tint = contentColor) }
                                        Box {
                                            var showListSwitcher by remember { mutableStateOf(false) }
                                            if (showListSwitcher) {
                                                ListSwitcherDialog(viewModel = viewModel, onDismiss = { showListSwitcher = false })
                                            }
                                            IconButton(onClick = { viewModel.updateMenuExpanded(true) }) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = contentColor) }
                                            DropdownMenu(expanded = viewModel.isMenuExpanded, onDismissRequest = { viewModel.updateMenuExpanded(false) }) {
                                                DropdownMenuItem(text = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, onClick = { viewModel.updateSearchMode(true); viewModel.updateMenuExpanded(false) })
                                                DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { viewModel.updateCurrentScreen(Screen.Settings); viewModel.updateMenuExpanded(false) })
                                                
                                                DropdownMenuItem(text = { Text("Switch List") }, leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) }, onClick = { showListSwitcher = true; viewModel.updateMenuExpanded(false) })
                                                HorizontalDivider()
                                                DropdownMenuItem(text = { Text("Top Bar Color") }, leadingIcon = { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(viewModel.appBarColor ?: MaterialTheme.colorScheme.primary).border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)) }, onClick = { viewModel.updateColorTarget("Top Bar"); viewModel.updateShowColorPicker(true); viewModel.updateMenuExpanded(false) })
                                                DropdownMenuItem(text = { Text("List Background") }, leadingIcon = { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(viewModel.listBackgroundColor ?: Color.White).border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)) }, onClick = { viewModel.updateColorTarget("List Background"); viewModel.updateShowColorPicker(true); viewModel.updateMenuExpanded(false) })
                                            }
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.width(68.dp))
                                }
                            }
                        )
                        if (!viewModel.isSearchMode && !viewModel.isCompactUi) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                                if (viewModel.showUndo) {
                                    IconButton(onClick = { undoRedoManager.undo() }, enabled = canUndo, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(36.dp), tint = if (canUndo) contentColor else contentColor.copy(alpha = 0.38f)) }
                                }
                                if (viewModel.showRedo) {
                                    IconButton(onClick = { undoRedoManager.redo() }, enabled = canRedo, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(36.dp), tint = if (canRedo) contentColor else contentColor.copy(alpha = 0.38f)) }
                                }
                                IconButton(onClick = { if (viewModel.isEditingTitle) { if (viewModel.listTitle != viewModel.previousTitle) undoRedoManager.add(RenameAction(viewModel.previousTitle, viewModel.listTitle) { viewModel.updateListTitle(it) }); viewModel.updateEditingTitle(false) } else { viewModel.updatePreviousTitle(viewModel.listTitle); viewModel.updateEditingTitle(true) } }, modifier = Modifier.size(48.dp)) { Icon(if (viewModel.isEditingTitle) Icons.Default.Check else Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(36.dp), tint = contentColor) }
                                Box {
                                    var showListSwitcher by remember { mutableStateOf(false) }
                                    if (showListSwitcher) {
                                        ListSwitcherDialog(viewModel = viewModel, onDismiss = { showListSwitcher = false })
                                    }
                                    IconButton(onClick = { viewModel.updateMenuExpanded(true) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = contentColor) }
                                    DropdownMenu(expanded = viewModel.isMenuExpanded, onDismissRequest = { viewModel.updateMenuExpanded(false) }) {
                                        DropdownMenuItem(text = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, onClick = { viewModel.updateSearchMode(true); viewModel.updateMenuExpanded(false) })
                                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { viewModel.updateCurrentScreen(Screen.Settings); viewModel.updateMenuExpanded(false) })

                                        DropdownMenuItem(text = { Text("Switch List") }, leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) }, onClick = { showListSwitcher = true; viewModel.updateMenuExpanded(false) })
                                        HorizontalDivider()
                                        DropdownMenuItem(text = { Text("Top Bar Color") }, leadingIcon = { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(viewModel.appBarColor ?: MaterialTheme.colorScheme.primary).border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)) }, onClick = { viewModel.updateColorTarget("Top Bar"); viewModel.updateShowColorPicker(true); viewModel.updateMenuExpanded(false) })
                                        DropdownMenuItem(text = { Text("List Background") }, leadingIcon = { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(viewModel.listBackgroundColor ?: Color.White).border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)) }, onClick = { viewModel.updateColorTarget("List Background"); viewModel.updateShowColorPicker(true); viewModel.updateMenuExpanded(false) })
                                    }
                                }
                            }
                        }
                    }
                },
                content = { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding).background(if (viewModel.isDarkMode) Color.Black else (viewModel.listBackgroundColor ?: MaterialTheme.colorScheme.surface))) {
                        // Custom Tab System
                        if (viewModel.separateCompletedList) {
                            val notCompletedCount = items.count { !it.isChecked }
                            val completedCount = items.count { it.isChecked }
                            Row(modifier = Modifier.fillMaxWidth().background(contentColor.copy(alpha = 0.05f))) {
                                Box(modifier = Modifier.weight(1f).clickable { viewModel.selectedListTab = ListTab.NotCompleted }.background(if (viewModel.selectedListTab == ListTab.NotCompleted) contentColor.copy(alpha = 0.1f) else Color.Transparent).padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("Not Completed ($notCompletedCount)", fontWeight = if (viewModel.selectedListTab == ListTab.NotCompleted) FontWeight.Bold else FontWeight.Normal, color = if (viewModel.selectedListTab == ListTab.NotCompleted) listItemContentColor else listItemContentColor.copy(alpha = 0.6f))
                                }
                                Box(modifier = Modifier.weight(1f).clickable { viewModel.selectedListTab = ListTab.Completed }.background(if (viewModel.selectedListTab == ListTab.Completed) contentColor.copy(alpha = 0.1f) else Color.Transparent).padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("Completed ($completedCount)", fontWeight = if (viewModel.selectedListTab == ListTab.Completed) FontWeight.Bold else FontWeight.Normal, color = if (viewModel.selectedListTab == ListTab.Completed) listItemContentColor else listItemContentColor.copy(alpha = 0.6f))
                                }
                            }
                        }
                        
                        if (viewModel.showMetadata) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(text = "Date created: ${formatTimestamp(viewModel.createdTimestamp)}", style = MaterialTheme.typography.labelSmall, color = listItemContentColor.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Last modified: ${formatTimestamp(viewModel.lastModifiedTimestamp)}", style = MaterialTheme.typography.labelSmall, color = listItemContentColor.copy(alpha = 0.6f))
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = listItemContentColor.copy(alpha = 0.2f))
                        }
                        var showAddDialog by remember { mutableStateOf(false) }
                        var addAtTop by remember { mutableStateOf(false) }
                        var editingItem by remember { mutableStateOf<ListItem?>(null) }
                        
                        if (showAddDialog) {
                            AddItemDialog(
                                onAdd = { text, isSpreader -> 
                                    if (viewModel.currentListId.value.isNotEmpty()) {
                                        scope.launch { 
                                            val pos = viewModel.getNextPosition(addAtTop)
                                            val now = Clock.System.now().toEpochMilliseconds()
                                            val newItem = ListItem(
                                                id = (now + (0..1000).random()).toString(), 
                                                text = text, 
                                                isSpreader = isSpreader, 
                                                timestamp = now, 
                                                position = pos
                                            )
                                            undoRedoManager.add(AddAction(newItem, viewModel))
                                            viewModel.addItemDirectly(newItem)
                                            viewModel.updateLastModifiedTimestamp() 
                                        }
                                    }
                                    showAddDialog = false 
                                }, 
                                onDismiss = { showAddDialog = false }
                            )
                        }
                        if (editingItem != null) {
                            EditItemDialog(item = editingItem!!, onConfirm = { newText -> viewModel.updateItemText(editingItem!!, newText); editingItem = null }, onDismiss = { editingItem = null })
                        }
                        LazyColumn(state = lazyListState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!viewModel.separateCompletedList || viewModel.selectedListTab == ListTab.NotCompleted) {
                                item { 
                                    AddItemTile(
                                        contentColor = if (viewModel.currentListId.value.isEmpty()) listItemContentColor.copy(alpha = 0.3f) else listItemContentColor, 
                                        onClick = { 
                                            if (viewModel.currentListId.value.isNotEmpty()) {
                                                addAtTop = true
                                                showAddDialog = true 
                                            }
                                        }
                                    ) 
                                }
                            }
                            
                            val filteredItems = items.filter { item ->
                                val matchesSearch = item.text.contains(viewModel.searchQuery, ignoreCase = true)
                                val matchesTab = if (viewModel.separateCompletedList) {
                                    if (viewModel.selectedListTab == ListTab.Completed) item.isChecked else !item.isChecked
                                } else true
                                matchesSearch && (item.isSpreader || matchesTab)
                            }
                            
                            items(filteredItems, key = { it.id }) { item ->
                                val isDragging = viewModel.draggingItemId == item.id
                                var initialDragIdx by remember { mutableStateOf<Int?>(null) }
                                ListItemRow(
                                    item = item, contentColor = listItemContentColor, fontSize = viewModel.fontSize, zoomLevel = viewModel.zoomLevel, fontStyle = viewModel.fontStyle, isBold = viewModel.isBold, isItalic = viewModel.isItalic, isUnderlined = viewModel.isUnderlined, checkmarkStyle = viewModel.checkmarkStyle, checkmarkColor = effectiveCheckmarkColor, checkmarkPosition = viewModel.checkmarkPosition, showCheckmarkBox = viewModel.showCheckmarkBox, crossOutOptions = viewModel.crossOutOptions, crossOutColor = effectiveCrossOutColor, wavyWavelength = viewModel.wavyWavelength, wavyExtraHeight = viewModel.wavyExtraHeight, scribbleIntensity = viewModel.scribbleIntensity, undulationFrequency = viewModel.undulationFrequency, crossOutOpacity = viewModel.crossOutOpacity,
                                    straightThickness = viewModel.straightThickness, grayOutChecked = viewModel.grayOutChecked, tdmPosition = viewModel.tdmPosition,
                                    onToggle = { undoRedoManager.add(ToggleAction(item.id, item.isChecked, !item.isChecked, viewModel)); viewModel.toggleItem(item); viewModel.updateLastModifiedTimestamp() },
                                    onDelete = { undoRedoManager.add(DeleteAction(item, viewModel)); viewModel.deleteItem(item); viewModel.updateLastModifiedTimestamp() },
                                    onEdit = { editingItem = item },
                                    isEditMode = viewModel.isEditingTitle,
                                    modifier = Modifier.animateItem(placementSpec = null).zIndex(if (isDragging) 1f else 0f).graphicsLayer { translationY = if (isDragging) dragOffset else 0f; alpha = if (isDragging) 0.8f else 1.0f },
                                    handleModifier = Modifier.pointerInput(item.id) { 
                                        detectDragGestures(
                                            onDragStart = { 
                                                viewModel.updateDraggingItemId(item.id)
                                                // Correctly identify starting index in the FULL list for Undo
                                                initialDragIdx = items.indexOfFirst { it.id == item.id }
                                            }, 
                                            onDrag = { change, dragAmount -> 
                                                change.consume()
                                                dragOffset += dragAmount.y 
                                            }, 
                                            onDragEnd = { 
                                                if (viewModel.draggingItemId != null && initialDragIdx != null) { 
                                                    val finalIndex = items.indexOfFirst { it.id == viewModel.draggingItemId }
                                                    if (finalIndex != -1 && finalIndex != initialDragIdx) { 
                                                        undoRedoManager.add(MoveAction(initialDragIdx!!, finalIndex, viewModel))
                                                        viewModel.updateLastModifiedTimestamp() 
                                                    } 
                                                } 
                                                viewModel.updateDraggingItemId(null)
                                                dragOffset = 0f 
                                            }, 
                                            onDragCancel = { 
                                                viewModel.updateDraggingItemId(null)
                                                dragOffset = 0f 
                                            }
                                        ) 
                                    }
                                )
                            }
                            if (!viewModel.separateCompletedList || viewModel.selectedListTab == ListTab.NotCompleted) {
                                item { 
                                    AddItemTile(
                                        contentColor = if (viewModel.currentListId.value.isEmpty()) listItemContentColor.copy(alpha = 0.3f) else listItemContentColor, 
                                        onClick = { 
                                            if (viewModel.currentListId.value.isNotEmpty()) {
                                                addAtTop = false
                                                showAddDialog = true 
                                            }
                                        }
                                    ) 
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsPage(viewModel: ListViewModel, contentColor: Color, onBack: () -> Unit) {
    var showCheckColorPicker by remember { mutableStateOf(false) }
    var showCrossColorPicker by remember { mutableStateOf(false) }
    val showResetConfirm = remember { mutableStateOf(false) }
    
    if (showResetConfirm.value) {
        AlertDialog(
            onDismissRequest = { showResetConfirm.value = false },
            title = { Text("Confirm Reset") },
            text = { Text("Are you sure you want to reset all settings to default?") },
            confirmButton = { Button(onClick = { viewModel.resetToDefault(); showResetConfirm.value = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetConfirm.value = false }) { Text("Cancel") } }
        )
    }

    if (showCheckColorPicker) { ColorPickerDialog(title = "Pick Checkmark Color", initialColor = viewModel.checkmarkColor ?: contentColor, savedCustomColors = viewModel.savedCustomColors, viewModel = viewModel, onDismiss = { showCheckColorPicker = false }, onColorSelected = { sel -> viewModel.updateCheckmarkColor(sel); showCheckColorPicker = false }) }
    if (showCrossColorPicker) { ColorPickerDialog(title = "Pick Cross-out Color", initialColor = viewModel.crossOutColor ?: contentColor, savedCustomColors = viewModel.savedCustomColors, viewModel = viewModel, onDismiss = { showCrossColorPicker = false }, onColorSelected = { sel -> viewModel.updateCrossOutColor(sel); showCrossColorPicker = false }) }
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(viewModel.appBarColor ?: MaterialTheme.colorScheme.primary).statusBarsPadding()) {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = contentColor, navigationIconContentColor = contentColor),
                    title = { Text(text = "Settings", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(32.dp)) } },
                    actions = { Box(modifier = Modifier.width(68.dp)) }
                )
            }
        },
        content = { padding ->
            val previewBgColor = remember(viewModel.checkmarkColor, contentColor) { val base = viewModel.checkmarkColor ?: contentColor; val luminance = (0.299 * base.red) + (0.587 * base.green) + (0.114 * base.blue); if (luminance > 0.8) Color.Black.copy(alpha = 0.05f) else Color.Transparent }
            val settingsPageBg = if (viewModel.isDarkMode) Color.Black else MaterialTheme.colorScheme.surface
            val settingsPageContentColor = if (viewModel.isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface

            @Composable
            fun LabeledSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Switch(checked = checked, onCheckedChange = onCheckedChange)
                    Text(text = if (checked) "ON" else "OFF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = settingsPageContentColor.copy(alpha = 0.8f))
                }
            }

            @Composable
            fun HighContrastButton(
                onClick: () -> Unit,
                enabled: Boolean,
                selectedColor: Color?,
                text: String
            ) {
                val containerColor = if (!enabled) {
                    if (viewModel.isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f)
                } else {
                    selectedColor ?: MaterialTheme.colorScheme.primary
                }
                val buttonContentColor = if (!enabled) {
                    settingsPageContentColor.copy(alpha = 0.5f)
                } else {
                    if (selectedColor != null) {
                        val luminance = (0.299 * selectedColor.red) + (0.587 * selectedColor.green) + (0.114 * selectedColor.blue)
                        if (luminance > 0.5) Color.Black else Color.White
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                }
                val border = if (enabled && selectedColor != null) {
                    val luminance = (0.299 * selectedColor.red) + (0.587 * selectedColor.green) + (0.114 * selectedColor.blue)
                    val bgLuminance = if (viewModel.isDarkMode) 0f else 1f
                    if (abs(luminance - bgLuminance) < 0.25) BorderStroke(1.dp, settingsPageContentColor.copy(alpha = 0.5f)) else null
                } else if (!enabled && viewModel.isDarkMode) {
                    // Always add a subtle border for disabled buttons in Dark Mode to define shape
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                } else null

                Button(
                    onClick = onClick,
                    enabled = enabled,
                    border = border,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = buttonContentColor,
                        disabledContainerColor = containerColor,
                        disabledContentColor = buttonContentColor
                    )
                ) {
                    Text(text)
                }
            }

            Column(modifier = Modifier.fillMaxSize().padding(padding).background(settingsPageBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) { Text(text = "Dark Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settingsPageContentColor); Spacer(modifier = Modifier.height(4.dp)); Text(text = "Forces the entire app to use a Black background with White text.", style = MaterialTheme.typography.bodySmall, color = settingsPageContentColor.copy(alpha = 0.7f)) }
                    LabeledSwitch(checked = viewModel.isDarkMode, onCheckedChange = { viewModel.updateDarkMode(it) })
                }
                HorizontalDivider(color = settingsPageContentColor.copy(alpha = 0.12f))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) { Text(text = "Hide Metadata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settingsPageContentColor); Spacer(modifier = Modifier.height(4.dp)); Text(text = "Hide date labels.", style = MaterialTheme.typography.bodySmall, color = settingsPageContentColor.copy(alpha = 0.7f)) }
                    LabeledSwitch(checked = !viewModel.showMetadata, onCheckedChange = { viewModel.updateShowMetadata(!it) })
                }
                HorizontalDivider(color = settingsPageContentColor.copy(alpha = 0.12f))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) { Text(text = "Separate list for completed items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settingsPageContentColor); Spacer(modifier = Modifier.height(4.dp)); Text(text = "Automatically move all checkmarked items to the \"Completed\" tab.", style = MaterialTheme.typography.bodySmall, color = settingsPageContentColor.copy(alpha = 0.7f)) }
                    LabeledSwitch(checked = viewModel.separateCompletedList, onCheckedChange = { viewModel.updateSeparateCompletedList(it) })
                }
                HorizontalDivider(color = settingsPageContentColor.copy(alpha = 0.12f))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "UI Visibility & Layout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                    
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Show Undo Button", color = settingsPageContentColor)
                        LabeledSwitch(checked = viewModel.showUndo, onCheckedChange = { viewModel.updateShowUndo(it) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Show Redo Button", color = settingsPageContentColor)
                        LabeledSwitch(checked = viewModel.showRedo, onCheckedChange = { viewModel.updateShowRedo(it) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Compact UI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                            Text(text = "Hides undo/redo and moves essential tools to the top bar.", style = MaterialTheme.typography.bodySmall, color = settingsPageContentColor.copy(alpha = 0.7f))
                        }
                        LabeledSwitch(checked = viewModel.isCompactUi, onCheckedChange = { viewModel.updateCompactUi(it) })
                    }
                }
                HorizontalDivider(color = settingsPageContentColor.copy(alpha = 0.12f))
                Column {
                    Text(text = "Font Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SearchableFontPicker(
                        selectedFont = viewModel.fontStyle,
                        onFontSelected = { viewModel.updateFontStyle(it) },
                        contentColor = settingsPageContentColor,
                        backgroundColor = settingsPageBg
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = viewModel.isBold, onCheckedChange = { viewModel.updateBold(it) }, colors = CheckboxDefaults.colors(checkedColor = settingsPageContentColor))
                            Text("Bold", color = settingsPageContentColor)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = viewModel.isItalic, onCheckedChange = { viewModel.updateItalic(it) }, colors = CheckboxDefaults.colors(checkedColor = settingsPageContentColor))
                            Text("Italic", color = settingsPageContentColor)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = viewModel.isUnderlined, onCheckedChange = { viewModel.updateUnderlined(it) }, colors = CheckboxDefaults.colors(checkedColor = settingsPageContentColor))
                            Text("Underlined", color = settingsPageContentColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).background(previewBgColor, RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                        ListItemRow(item = ListItem("preview_font", "Font Style Preview.", isChecked = false), contentColor = settingsPageContentColor, fontSize = viewModel.fontSize, zoomLevel = viewModel.zoomLevel, fontStyle = viewModel.fontStyle, isBold = viewModel.isBold, isItalic = viewModel.isItalic, isUnderlined = viewModel.isUnderlined, checkmarkStyle = viewModel.checkmarkStyle, checkmarkColor = if (viewModel.isCheckmarkHighContrast) settingsPageContentColor else (viewModel.checkmarkColor ?: settingsPageContentColor), checkmarkPosition = viewModel.checkmarkPosition, showCheckmarkBox = viewModel.showCheckmarkBox, crossOutOptions = emptyList(), crossOutColor = if (viewModel.isCrossOutHighContrast) settingsPageContentColor else (viewModel.crossOutColor ?: settingsPageContentColor), crossOutOpacity = viewModel.crossOutOpacity, wavyWavelength = viewModel.wavyWavelength, wavyExtraHeight = viewModel.wavyExtraHeight, scribbleIntensity = viewModel.scribbleIntensity, undulationFrequency = viewModel.undulationFrequency, straightThickness = viewModel.straightThickness, grayOutChecked = viewModel.grayOutChecked, tdmPosition = viewModel.tdmPosition, onToggle = {}, onDelete = {}, onEdit = {}, isEditMode = false)
                    }
                }
                HorizontalDivider(color = settingsPageContentColor.copy(alpha = 0.12f))
                Column {
                    Text(text = "Sizing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Font Size (6-78):", style = MaterialTheme.typography.labelMedium, color = settingsPageContentColor)
                        var fontSizeText by remember(viewModel.fontSize) { mutableStateOf(viewModel.fontSize.toInt().toString()) }
                        OutlinedTextField(
                            value = fontSizeText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }
                                if (filtered.length <= 2) {
                                    fontSizeText = filtered
                                    filtered.toIntOrNull()?.let { size ->
                                        if (size in 6..78) viewModel.updateFontSize(size.toFloat())
                                    }
                                }
                            },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = settingsPageContentColor,
                                unfocusedTextColor = settingsPageContentColor,
                                focusedBorderColor = settingsPageContentColor,
                                unfocusedBorderColor = settingsPageContentColor.copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Zoom Level: ${(viewModel.zoomLevel * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = settingsPageContentColor)
                    Slider(value = viewModel.zoomLevel, onValueChange = { viewModel.updateZoomLevel(it) }, valueRange = 0.5f..2.5f, colors = SliderDefaults.colors(thumbColor = settingsPageContentColor, activeTrackColor = settingsPageContentColor))
                }
                HorizontalDivider(color = settingsPageContentColor.copy(alpha = 0.12f))
                Column {
                    Text(text = "Checkmark Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).background(previewBgColor, RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                        ListItemRow(item = ListItem("preview_check", "Checkmark Preview.", isChecked = true), contentColor = settingsPageContentColor, fontSize = viewModel.fontSize, zoomLevel = viewModel.zoomLevel, fontStyle = viewModel.fontStyle, isBold = viewModel.isBold, isItalic = viewModel.isItalic, isUnderlined = viewModel.isUnderlined, checkmarkStyle = viewModel.checkmarkStyle, checkmarkColor = if (viewModel.isCheckmarkHighContrast) settingsPageContentColor else (viewModel.checkmarkColor ?: settingsPageContentColor), checkmarkPosition = viewModel.checkmarkPosition, showCheckmarkBox = viewModel.showCheckmarkBox, crossOutOptions = emptyList(), crossOutColor = if (viewModel.isCrossOutHighContrast) settingsPageContentColor else (viewModel.crossOutColor ?: settingsPageContentColor), crossOutOpacity = viewModel.crossOutOpacity, wavyWavelength = viewModel.wavyWavelength, wavyExtraHeight = viewModel.wavyExtraHeight, scribbleIntensity = viewModel.scribbleIntensity, undulationFrequency = viewModel.undulationFrequency, straightThickness = viewModel.straightThickness, grayOutChecked = viewModel.grayOutChecked, tdmPosition = viewModel.tdmPosition, onToggle = {}, onDelete = {}, onEdit = {}, isEditMode = false)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Checkmark Position", style = MaterialTheme.typography.labelMedium, color = settingsPageContentColor)
                        var positionMenuExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = positionMenuExpanded,
                            onExpandedChange = { positionMenuExpanded = !positionMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = viewModel.checkmarkPosition,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = positionMenuExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = settingsPageContentColor,
                                    unfocusedTextColor = settingsPageContentColor,
                                    focusedBorderColor = settingsPageContentColor,
                                    unfocusedBorderColor = settingsPageContentColor.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = positionMenuExpanded,
                                onDismissRequest = { positionMenuExpanded = false }
                            ) {
                                listOf("Left", "Right", "None").forEach { pos ->
                                    DropdownMenuItem(
                                        text = { Text(pos) },
                                        onClick = {
                                            viewModel.updateCheckmarkPosition(pos)
                                            positionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Three Dot Menu Position", style = MaterialTheme.typography.labelMedium, color = settingsPageContentColor)
                        var tdmPositionMenuExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = tdmPositionMenuExpanded,
                            onExpandedChange = { tdmPositionMenuExpanded = !tdmPositionMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = viewModel.tdmPosition,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tdmPositionMenuExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = settingsPageContentColor,
                                    unfocusedTextColor = settingsPageContentColor,
                                    focusedBorderColor = settingsPageContentColor,
                                    unfocusedBorderColor = settingsPageContentColor.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = tdmPositionMenuExpanded,
                                onDismissRequest = { tdmPositionMenuExpanded = false }
                            ) {
                                listOf("Left", "Right").forEach { pos ->
                                    DropdownMenuItem(
                                        text = { Text(pos) },
                                        onClick = {
                                            viewModel.updateTdmPosition(pos)
                                            tdmPositionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "High Contrast", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                            Text(text = "Disable High Contrast to allow custom color selection.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = settingsPageContentColor.copy(alpha = 0.8f))
                        }
                        LabeledSwitch(checked = viewModel.isCheckmarkHighContrast, onCheckedChange = { viewModel.updateCheckmarkHighContrast(it) })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("Show Border", color = settingsPageContentColor); LabeledSwitch(checked = viewModel.showCheckmarkBox, onCheckedChange = { viewModel.updateShowCheckmarkBox(it) }) }
                    val checkStyles = listOf("Checkmark", "X", "Star", "Circle", "Fill")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { checkStyles.forEach { style -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.updateCheckmarkStyle(style) }) { RadioButton(selected = viewModel.checkmarkStyle == style, onClick = { viewModel.updateCheckmarkStyle(style) }, colors = RadioButtonDefaults.colors(selectedColor = settingsPageContentColor)); Text(style, color = settingsPageContentColor) } } }
                    HighContrastButton(onClick = { showCheckColorPicker = true }, enabled = !viewModel.isCheckmarkHighContrast, selectedColor = viewModel.checkmarkColor, text = "Change Color")
                }
                HorizontalDivider(color = settingsPageContentColor.copy(alpha = 0.12f))
                Column {
                    Text(text = "Cross-out Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).background(previewBgColor, RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                        ListItemRow(item = ListItem("preview_cross", "Cross-out Preview.", isChecked = true), contentColor = settingsPageContentColor, fontSize = viewModel.fontSize, zoomLevel = viewModel.zoomLevel, fontStyle = viewModel.fontStyle, isBold = viewModel.isBold, isItalic = viewModel.isItalic, isUnderlined = viewModel.isUnderlined, checkmarkStyle = viewModel.checkmarkStyle, checkmarkColor = if (viewModel.isCheckmarkHighContrast) settingsPageContentColor else (viewModel.checkmarkColor ?: settingsPageContentColor), checkmarkPosition = viewModel.checkmarkPosition, showCheckmarkBox = viewModel.showCheckmarkBox, crossOutOptions = viewModel.crossOutOptions, crossOutColor = if (viewModel.isCrossOutHighContrast) settingsPageContentColor else (viewModel.crossOutColor ?: (viewModel.checkmarkColor ?: settingsPageContentColor)), crossOutOpacity = viewModel.crossOutOpacity, wavyWavelength = viewModel.wavyWavelength, wavyExtraHeight = viewModel.wavyExtraHeight, scribbleIntensity = viewModel.scribbleIntensity, undulationFrequency = viewModel.undulationFrequency, straightThickness = viewModel.straightThickness, grayOutChecked = viewModel.grayOutChecked, tdmPosition = viewModel.tdmPosition, onToggle = {}, onDelete = {}, onEdit = {}, isEditMode = false)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Gray-out Checked Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                        }
                        LabeledSwitch(checked = viewModel.grayOutChecked, onCheckedChange = { viewModel.updateGrayOutChecked(it) })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "High Contrast", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = settingsPageContentColor)
                            Text(text = "Disable High Contrast to allow custom color selection.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = settingsPageContentColor.copy(alpha = 0.8f))
                        }
                        LabeledSwitch(checked = viewModel.isCrossOutHighContrast, onCheckedChange = { viewModel.updateCrossOutHighContrast(it) })
                    }
                    HighContrastButton(onClick = { showCrossColorPicker = true }, enabled = !viewModel.isCheckmarkHighContrast, selectedColor = viewModel.crossOutColor, text = "Change Color")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Opacity", style = MaterialTheme.typography.labelSmall, color = settingsPageContentColor)
                    Slider(value = viewModel.crossOutOpacity, onValueChange = { viewModel.updateCrossOutOpacity(it) }, valueRange = 0.05f..1.0f, colors = SliderDefaults.colors(thumbColor = settingsPageContentColor, activeTrackColor = settingsPageContentColor))
                    val crossStyles = listOf("Straight", "Wavy", "Scribble")
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) { crossStyles.forEach { style -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.toggleCrossOutOption(style) }) { Checkbox(checked = viewModel.crossOutOptions.contains(style), onCheckedChange = { viewModel.toggleCrossOutOption(style) }, colors = CheckboxDefaults.colors(checkedColor = settingsPageContentColor)); Text(style, color = settingsPageContentColor) } } }
                    
                    if (viewModel.crossOutOptions.contains("Straight") || viewModel.crossOutOptions.contains("Wavy") || viewModel.crossOutOptions.contains("Scribble")) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(12.dp)
                        ) {
                            if (viewModel.crossOutOptions.contains("Straight")) {
                                Text(text = "Straight Thickness", style = MaterialTheme.typography.labelSmall, color = settingsPageContentColor)
                                Slider(value = viewModel.straightThickness, onValueChange = { viewModel.updateStraightThickness(it) }, valueRange = 0.5f..10.0f, colors = SliderDefaults.colors(thumbColor = settingsPageContentColor, activeTrackColor = settingsPageContentColor))
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (viewModel.crossOutOptions.contains("Wavy") || viewModel.crossOutOptions.contains("Scribble")) {
                                Text(text = "Undulation Frequency (Pattern Density)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = settingsPageContentColor)
                                Slider(value = viewModel.undulationFrequency, onValueChange = { viewModel.updateUndulationFrequency(it) }, valueRange = 0.5f..5.0f, colors = SliderDefaults.colors(thumbColor = settingsPageContentColor, activeTrackColor = settingsPageContentColor))
                            }
                        }
                    }

                    if (viewModel.crossOutOptions.contains("Wavy")) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(12.dp)
                        ) {
                            Text(text = "Wavy Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = settingsPageContentColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Base Wavelength", style = MaterialTheme.typography.bodySmall, color = settingsPageContentColor)
                            Slider(value = viewModel.wavyWavelength, onValueChange = { viewModel.updateWavyWavelength(it) }, valueRange = 5f..300f, colors = SliderDefaults.colors(thumbColor = settingsPageContentColor, activeTrackColor = settingsPageContentColor))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(text = "Extra Height (Cross out entire tile)", style = MaterialTheme.typography.bodySmall, color = settingsPageContentColor)
                                LabeledSwitch(checked = viewModel.wavyExtraHeight, onCheckedChange = { viewModel.updateWavyExtraHeight(it) })
                            }
                        }
                    }

                    if (viewModel.crossOutOptions.contains("Scribble")) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(12.dp)
                        ) {
                            Text(text = "Scribble Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = settingsPageContentColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Intensity (Scraping density)", style = MaterialTheme.typography.bodySmall, color = settingsPageContentColor)
                            Slider(value = viewModel.scribbleIntensity, onValueChange = { viewModel.updateScribbleIntensity(it) }, valueRange = 0.1f..1.0f, colors = SliderDefaults.colors(thumbColor = settingsPageContentColor, activeTrackColor = settingsPageContentColor))
                        }
                    }
                }
                Button(onClick = { showResetConfirm.value = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Reset all settings to default") }
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    )
}

fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val day = @Suppress("DEPRECATION") dateTime.dayOfMonth.toString().padStart(2, '0')
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
    fontSize: Float,
    zoomLevel: Float,
    fontStyle: String,
    isBold: Boolean,
    isItalic: Boolean,
    isUnderlined: Boolean,
    checkmarkStyle: String,
    checkmarkColor: Color,
    checkmarkPosition: String,
    showCheckmarkBox: Boolean,
    crossOutOptions: List<String>,
    crossOutColor: Color,
    crossOutOpacity: Float,
    wavyWavelength: Float,
    wavyExtraHeight: Boolean,
    scribbleIntensity: Float,
    undulationFrequency: Float,
    straightThickness: Float,
    grayOutChecked: Boolean,
    tdmPosition: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val alpha = if (grayOutChecked && item.isChecked) 0.4f else 1.0f
    val clipboardManager = @Suppress("DEPRECATION") LocalClipboardManager.current

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        if (item.isSpreader) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = (8 * zoomLevel).dp), contentAlignment = Alignment.Center) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f), thickness = (2 * zoomLevel).dp, color = contentColor.copy(alpha = 0.3f))
                    if (isEditMode) {
                        IconButton(onClick = onDelete, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Spreader", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } else {
            var menuExpanded by remember { mutableStateOf(false) }

            val tdmMenu = @Composable {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    // Content Preview Header
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }, onClick = { menuExpanded = false; onDelete() })
                    DropdownMenuItem(text = { Text("Copy to Clipboard") }, leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }, onClick = { menuExpanded = false; clipboardManager.setText(AnnotatedString(item.text)) })
                }
            }

            Row(modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = { onToggle() },
                onLongClick = { if (!isEditMode) menuExpanded = true }
            ).padding(vertical = (4 * zoomLevel).dp), verticalAlignment = Alignment.CenterVertically) {
                val checkmarkBox = @Composable {
                    if (checkmarkPosition != "None") {
                        Box(modifier = Modifier.size((32 * zoomLevel).dp).graphicsLayer(alpha = alpha), contentAlignment = Alignment.Center) {
                            if (showCheckmarkBox && checkmarkStyle != "Fill") {
                                Box(modifier = Modifier.fillMaxSize().border((1.5 * zoomLevel).dp, checkmarkColor.copy(alpha = 0.6f), RoundedCornerShape((4 * zoomLevel).dp)))
                            }
                            val iconSize = (24 * zoomLevel).dp
                            when (checkmarkStyle) {
                                "X" -> if (item.isChecked) Icon(Icons.Default.Close, contentDescription = null, tint = checkmarkColor, modifier = Modifier.size(iconSize))
                                "Star" -> if (item.isChecked) Icon(Icons.Default.Star, contentDescription = null, tint = checkmarkColor, modifier = Modifier.size(iconSize))
                                "Circle" -> {
                                    if (item.isChecked) Box(modifier = Modifier.size((20 * zoomLevel).dp).background(checkmarkColor, CircleShape))
                                    else Box(modifier = Modifier.size((20 * zoomLevel).dp).border((1.5 * zoomLevel).dp, checkmarkColor.copy(alpha = 0.6f), CircleShape))
                                }
                                "Fill" -> Box(modifier = Modifier.size((20 * zoomLevel).dp).background(if (item.isChecked) checkmarkColor else contentColor.copy(alpha = 0.1f), RoundedCornerShape((4 * zoomLevel).dp)).border(1.dp, checkmarkColor.copy(alpha = 0.3f), RoundedCornerShape((4 * zoomLevel).dp)))
                                else -> if (item.isChecked) Icon(Icons.Default.Check, contentDescription = null, tint = checkmarkColor, modifier = Modifier.size(iconSize))
                            }
                        }
                    }
                }

                val tdm = @Composable {
                    if (!isEditMode && tdmPosition != "None") {
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size((40 * zoomLevel).dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Item Menu", tint = contentColor.copy(alpha = 0.7f), modifier = Modifier.size((24 * zoomLevel).dp))
                            }
                            tdmMenu()
                        }
                    }
                }

                Row(modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tdmPosition == "Left") {
                        tdm()
                        Spacer(modifier = Modifier.width((8 * zoomLevel).dp))
                    }
                    
                    if (checkmarkPosition == "Left") {
                        checkmarkBox()
                        Spacer(modifier = Modifier.width((12 * zoomLevel).dp))
                    }
                    
                    Text(
                        text = item.text,
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier.weight(1f).graphicsLayer(clip = false, alpha = alpha).drawCrossOut(options = if (item.isChecked) crossOutOptions else emptyList(), color = crossOutColor, opacity = crossOutOpacity, wavelength = wavyWavelength, extraHeight = wavyExtraHeight, intensity = scribbleIntensity, undulationFrequency = undulationFrequency, straightThickness = straightThickness, textLayoutResult = textLayoutResult),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = contentColor,
                            fontWeight = if (isBold) FontWeight.Bold else getFontWeight(fontStyle),
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (isUnderlined) TextDecoration.Underline else TextDecoration.None,
                            fontSize = (fontSize * zoomLevel).sp,
                            lineHeight = (fontSize * zoomLevel * 1.5f).sp,
                            fontFamily = getFontFamily(fontStyle)
                        )
                    )

                    if (checkmarkPosition == "Right") {
                        Spacer(modifier = Modifier.width((12 * zoomLevel).dp))
                        checkmarkBox()
                    }

                    if (tdmPosition == "Right") {
                        Spacer(modifier = Modifier.width((8 * zoomLevel).dp))
                        tdm()
                    }
                }
                
                // If TDM is None, we still need to render the menu somewhere for long-press to work
                if (!isEditMode && tdmPosition == "None") {
                    Box { tdmMenu() }
                }

                if (isEditMode) {
                    Box(modifier = handleModifier.width(64.dp).fillMaxHeight().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(24.dp), tint = contentColor.copy(alpha = 0.7f))
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(24.dp).offset(y = (-12).dp), tint = contentColor.copy(alpha = 0.7f))
                        }
                    }
                    Box(modifier = Modifier.width(64.dp).clickable { onDelete() }.fillMaxHeight().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun EditItemDialog(item: ListItem, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(item.text) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item") },
        text = {
            Column {
                TextField(value = text, onValueChange = { text = it }, modifier = Modifier.focusRequester(focusRequester), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddItemDialog(onAdd: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Item") },
        text = {
            Column {
                TextField(value = text, onValueChange = { text = it }, placeholder = { Text("Item name...") }, modifier = Modifier.focusRequester(focusRequester), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = { onAdd("", true) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Menu, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Spreader Line")
                }
            }
        },
        confirmButton = { Button(onClick = { onAdd(text, false); text = "" }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddItemTile(contentColor: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = contentColor.copy(alpha = 0.05f)), border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Add, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Add item", style = MaterialTheme.typography.bodyLarge, color = contentColor)
        }
    }
}

@Composable
fun ColorPickerDialog(title: String, initialColor: Color, savedCustomColors: MutableList<Color?>, viewModel: ListViewModel, onDismiss: () -> Unit, onColorSelected: (Color) -> Unit) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    val rainbowColors = listOf(Color.Red, Color(0xFFFFA500), Color.Yellow, Color.Green, Color.Blue, Color(0xFF800080), Color(0xFFFFC0CB), Color.Black, Color.Gray, Color.White)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rainbowColors.take(5).forEach { color -> Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(color).border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)).clickable { selectedColor = color }) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rainbowColors.drop(5).forEach { color -> Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(color).border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)).clickable { selectedColor = color }) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth().height(240.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorWheel(modifier = Modifier.weight(1f), initialColor = selectedColor, onColorChange = { newCol -> val currentHsv = colorToHsv(selectedColor); val newHsv = colorToHsv(newCol); selectedColor = Color.hsv(newHsv[0], newHsv[1], currentHsv[2]) })
                    Spacer(modifier = Modifier.width(16.dp))
                    BrightnessSlider(modifier = Modifier.width(36.dp).fillMaxHeight(), initialColor = selectedColor, onColorChange = { sel -> selectedColor = sel })
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(selectedColor).border(2.dp, Color.Black, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        val luminance = 0.299 * selectedColor.red + 0.587 * selectedColor.green + 0.114 * selectedColor.blue
                        Text("Preview", style = MaterialTheme.typography.labelSmall, color = if (luminance > 0.5) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    val hexCode = remember(selectedColor) { val r = (selectedColor.red * 255).roundToInt(); val g = (selectedColor.green * 255).roundToInt(); val b = (selectedColor.blue * 255).roundToInt(); "#" + r.toString(16).padStart(2, '0').uppercase() + g.toString(16).padStart(2, '0').uppercase() + b.toString(16).padStart(2, '0').uppercase() }
                    OutlinedTextField(value = hexCode, onValueChange = { input -> val clean = input.removePrefix("#").trim(); if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { try { val r = clean.substring(0, 2).toInt(16); val g = clean.substring(2, 4).toInt(16); val b = clean.substring(4, 6).toInt(16); selectedColor = Color(r, g, b) } catch (_: Exception) {} } }, label = { Text("Hex Code") }, singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center), modifier = Modifier.width(120.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Saved custom colors", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
                var showOverwriteConfirm by remember { mutableStateOf<Int?>(null) }
                if (showDeleteConfirm != null) {
                    val colorToDelete = savedCustomColors[showDeleteConfirm!!]
                    ColorDeleteDialog(colorToDelete = colorToDelete, onConfirm = { viewModel.setCustomColor(showDeleteConfirm!!, null); showDeleteConfirm = null }, onDismiss = { showDeleteConfirm = null })
                }
                if (showOverwriteConfirm != null) {
                    val existingColor = savedCustomColors[showOverwriteConfirm!!]
                    ColorOverwriteDialog(existingColor = existingColor, selectedColor = selectedColor, onConfirm = { viewModel.setCustomColor(showOverwriteConfirm!!, selectedColor); showOverwriteConfirm = null }, onUseSaved = { selectedColor = existingColor ?: selectedColor; showOverwriteConfirm = null }, onDismiss = { showOverwriteConfirm = null })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0 until 7) {
                        val color = savedCustomColors.getOrNull(i)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.size(38.dp).aspectRatio(1f).clip(CircleShape).background(color ?: Color.Transparent).border(width = 1.dp, color = if (color != null) Color.LightGray else Color.Gray.copy(alpha = 0.3f), shape = CircleShape).clickable { if (color == null) viewModel.setCustomColor(i, selectedColor) else if (color != selectedColor) showOverwriteConfirm = i })
                            if (color != null) IconButton(onClick = { showDeleteConfirm = i }, modifier = Modifier.size(40.dp).padding(top = 4.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.size(24.dp)) } else Spacer(modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { 
            onColorSelected(selectedColor)
            if (title.contains("Top Bar")) viewModel.updateAppBarColor(selectedColor)
            else if (title.contains("Background")) viewModel.updateListBackgroundColor(selectedColor)
            else if (title.contains("Checkmark")) viewModel.updateCheckmarkColor(selectedColor)
            else if (title.contains("Cross-out")) viewModel.updateCrossOutColor(selectedColor)
        }) { Text("Select") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ColorOverwriteDialog(
    existingColor: Color?,
    selectedColor: Color,
    onConfirm: () -> Unit,
    onUseSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Overwrite Color?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Overwrite slot?")
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(existingColor ?: Color.Transparent).border(1.dp, Color.LightGray, CircleShape))
                    Spacer(modifier = Modifier.width(24.dp))
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(24.dp))
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(selectedColor).border(1.dp, Color.LightGray, CircleShape))
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm() }) { Text("Overwrite") } },
        dismissButton = { TextButton(onClick = { onUseSaved() }) { Text("Use Saved") } }
    )
}

@Composable
fun ColorDeleteDialog(
    colorToDelete: Color?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Delete this saved color?")
                Spacer(modifier = Modifier.height(20.dp))
                Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(colorToDelete ?: Color.Transparent).border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Cancel") } }
    )
}

@Composable
fun ColorWheel(modifier: Modifier = Modifier, initialColor: Color, onColorChange: (Color) -> Unit) {
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val radius = constraints.maxWidth / 2f
        val center = Offset(radius, radius)
        var hsv by remember(initialColor) { mutableStateOf(colorToHsv(initialColor)) }
        val thumbOffset = remember(hsv, radius) { val angle = (hsv[0] * PI.toFloat() / 180f); val dist = hsv[1] * radius; Offset(x = center.x + dist * cos(angle), y = center.y + dist * sin(angle)) }
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { val down = awaitFirstDown(); val dx = down.position.x - center.x; val dy = down.position.y - center.y; val dist = min(sqrt((dx * dx + dy * dy).toDouble()), radius.toDouble()).toFloat(); var angle = atan2(dy, dx) * 180f / PI.toFloat(); if (angle < 0) angle += 360f; val saturation = dist / radius; onColorChange(Color.hsv(angle, saturation, hsv[2])); drag(down.id) { change -> change.consume(); val t = change.position; val dxDrag = t.x - center.x; val dyDrag = t.y - center.y; val distDrag = min(sqrt((dxDrag * dxDrag + dyDrag * dyDrag).toDouble()), radius.toDouble()).toFloat(); var angleDrag = atan2(dyDrag, dxDrag) * 180f / PI.toFloat(); if (angleDrag < 0) angleDrag += 360f; val saturationDrag = distDrag / radius; onColorChange(Color.hsv(angleDrag, saturationDrag, hsv[2])) } } }) {
            val hueColors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
            drawCircle(brush = Brush.sweepGradient(hueColors, center))
            drawCircle(brush = Brush.radialGradient(colors = listOf(Color.White, Color.Transparent), center = center, radius = radius))
            drawCircle(color = Color.Black, radius = 10.dp.toPx(), center = thumbOffset, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = Color.White, radius = 8.dp.toPx(), center = thumbOffset, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

fun colorToHsv(color: Color): FloatArray {
    val r = color.red; val g = color.green; val b = color.blue
    val max = max(r, max(g, b)); val min = min(r, min(g, b)); val delta = max - min
    var h = 0f
    if (delta != 0f) { h = when (max) { r -> (g - b) / delta + (if (g < b) 6 else 0); g -> (b - r) / delta + 2; else -> (r - g) / delta + 4 }; h /= 6f }
    val s = if (max == 0f) 0f else delta / max
    return floatArrayOf(h * 360f, s, max)
}

@Composable
fun BrightnessSlider(modifier: Modifier = Modifier, initialColor: Color, onColorChange: (Color) -> Unit) {
    val hsv = remember(initialColor) { colorToHsv(initialColor) }
    BoxWithConstraints(modifier = modifier) {
        val height = constraints.maxHeight.toFloat()
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { val down = awaitFirstDown(); val y = down.position.y.coerceIn(0f, height); val brightness = 1f - (y / height); onColorChange(Color.hsv(hsv[0], hsv[1], brightness)); drag(down.id) { change -> change.consume(); val yDrag = change.position.y.coerceIn(0f, height); val brightnessDrag = 1f - (yDrag / height); onColorChange(Color.hsv(hsv[0], hsv[1], brightnessDrag)) } } }) {
            val brush = Brush.verticalGradient(colors = listOf(Color.hsv(hsv[0], hsv[1], 1f), Color.Black))
            drawRect(brush = brush, size = size)
            drawRect(color = Color.Black, size = size, style = Stroke(width = 1.dp.toPx()))
            val thumbY = (1f - hsv[2]) * size.height
            drawRect(color = Color.White, topLeft = Offset(0f, thumbY - 4.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 8.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
            drawRect(color = Color.Black, topLeft = Offset(0f, thumbY - 5.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 10.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

fun Modifier.drawCrossOut(options: List<String>, color: Color, opacity: Float = 1.0f, wavelength: Float = 20f, extraHeight: Boolean = false, intensity: Float = 0.5f, undulationFrequency: Float = 1.0f, straightThickness: Float = 2.0f, textLayoutResult: TextLayoutResult? = null): Modifier = this.drawWithContent {
    drawContent()
    if (options.isEmpty()) return@drawWithContent
    val layout = textLayoutResult ?: return@drawWithContent
    val effectiveColor = color.copy(alpha = opacity)
    for (i in 0 until layout.lineCount) {
        val lineTop = layout.getLineTop(i); val lineBottom = layout.getLineBottom(i); val lineCenterY = (lineTop + lineBottom) / 2f
        val lineRight = layout.getLineRight(i); val lineLeft = layout.getLineLeft(i); val lineWidth = lineRight - lineLeft
        if (options.contains("Straight")) drawLine(color = effectiveColor, start = Offset(lineLeft, lineCenterY), end = Offset(lineRight, lineCenterY), strokeWidth = straightThickness.dp.toPx())
        if (options.contains("Wavy")) {
            val path = Path(); path.moveTo(lineLeft, lineCenterY)
            val lineWeightWavelength = wavelength / undulationFrequency
            val lineHeight = lineBottom - lineTop; val amplitude = if (extraHeight) lineHeight * 0.7f else 12f
            val waveCount = (lineWidth / lineWeightWavelength).toInt() + 1
            repeat(waveCount) { path.relativeQuadraticTo(lineWeightWavelength / 4f, -amplitude, lineWeightWavelength / 2f, 0f); path.relativeQuadraticTo(lineWeightWavelength / 4f, amplitude, lineWeightWavelength / 2f, 0f) }
            drawPath(path = path, color = effectiveColor, style = Stroke(width = if (extraHeight) 3.5.dp.toPx() else 2.5.dp.toPx()))
        }
        if (options.contains("Scribble")) {
            val path = Path(); path.moveTo(lineLeft, lineCenterY)
            val stepSize = (12f - (intensity * 10f)) / undulationFrequency
            val steps = (lineWidth / stepSize).toInt(); val lineHeight = lineBottom - lineTop
            for (idx in 0 until steps) {
                val x = lineLeft + idx * stepSize; val scribbleHeight = if (extraHeight) lineHeight * 0.9f else lineHeight * 0.6f; val jitter = (Random.nextFloat() * scribbleHeight) - (scribbleHeight / 2f)
                path.lineTo(x + (Random.nextFloat() * (stepSize * 1.5f) - (stepSize * 0.75f)), lineCenterY + jitter)
            }
            drawPath(path = path, color = effectiveColor, style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

val fontStyles = listOf(
    "Aptos", "Calibri", "Roboto", "Inter", "Arial", "Open Sans", "Montserrat", "Times New Roman", "Lato", "Georgia",
    "Segoe UI", "Verdana", "Tahoma", "Trebuchet MS", "Poppins", "Work Sans", "Source Sans 3", "Public Sans", "Nunito Sans", "DM Sans",
    "Helvetica Neue", "Franklin Gothic", "Gill Sans MT", "Futura", "Raleway", "Questrial", "Catamaran", "Heebo", "Mulish", "Oxygen",
    "Prompt", "Outfit", "Plus Jakarta Sans", "Manrope", "EB Garamond", "Cambria", "Libre Baskerville", "Playfair Display", "Merriweather", "Lora",
    "Palatino", "Book Antiqua", "Century Schoolbook", "Crimson Text", "PT Serif", "Spectral", "Cormorant Garamond", "Bitter", "Old Standard TT", "Cinzel",
    "Cardo", "Domine", "Bodoni Moda", "Alegreya", "Faustina", "Zilla Slab", "BioRhyme", "Fraunces", "Impact", "Bebas Neue",
    "Oswald", "Abril Fatface", "Anton", "Righteous", "Comfortaa", "Lobster", "Pacifico", "Bowlby One SC", "Alumni Sans", "Archivo Black",
    "Syncopate", "Staatliches", "Bungee", "Alfa Slab One", "Titan One", "Patua One", "Fredoka One", "Special Elite", "Courier New", "Consolas",
    "Roboto Mono", "Source Code Pro", "Inconsolata", "Space Mono", "Ubuntu Mono", "Fira Code", "JetBrains Mono", "Caveat", "Dancing Script", "Shadows Into Light",
    "Great Vibes", "Satisfy", "Indie Flower", "Permanent Marker", "Sacramento", "Kalam", "Yellowtail", "Default", "Serif", "Monospace"
).sorted()

@Composable
fun SearchableFontPicker(
    selectedFont: String,
    onFontSelected: (String) -> Unit,
    contentColor: Color,
    backgroundColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val filteredFonts = remember(searchQuery) {
        fontStyles.filter { font ->
            val lowercaseFont = font.lowercase()
            val matchesSearch = font.contains(searchQuery, ignoreCase = true)
            
            val matchesCursive = searchQuery.lowercase() == "cursive" && 
                (lowercaseFont.contains("script") || lowercaseFont.contains("cursive") || lowercaseFont.contains("vibes") || 
                 lowercaseFont.contains("marker") || lowercaseFont.contains("sacramento") || lowercaseFont.contains("kalam") || 
                 lowercaseFont.contains("yellowtail") || lowercaseFont.contains("satisfy") || lowercaseFont.contains("indie"))
            
            val matchesBold = searchQuery.lowercase() == "bold" &&
                (lowercaseFont.contains("bold") || lowercaseFont.contains("black") || lowercaseFont.contains("heavy") || 
                 lowercaseFont.contains("impact") || lowercaseFont.contains("staatliches") || lowercaseFont.contains("archivo"))

            matchesSearch || matchesCursive || matchesBold
        }
    }

    var menuWidth by remember { mutableStateOf(0) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
            menuWidth = coordinates.size.width
        }
    ) {
        OutlinedTextField(
            value = selectedFont,
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Font Style") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = contentColor,
                unfocusedTextColor = contentColor,
                focusedLabelColor = contentColor,
                unfocusedLabelColor = contentColor,
                focusedBorderColor = contentColor,
                unfocusedBorderColor = contentColor.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        val density = androidx.compose.ui.platform.LocalDensity.current
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
            modifier = Modifier
                .background(backgroundColor)
                .border(1.dp, contentColor.copy(alpha = 0.2f))
                .width(with(density) { menuWidth.toDp() })
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search fonts...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor
                )
            )
            
            LaunchedEffect(expanded) {
                if (expanded) {
                    delay(300) // Settle delay
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.12f))
            Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                filteredFonts.forEach { font ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = font,
                                color = contentColor,
                                style = LocalTextStyle.current.copy(
                                    fontWeight = getFontWeight(font),
                                    fontFamily = getFontFamily(font)
                                )
                            )
                        },
                        onClick = {
                            onFontSelected(font)
                            expanded = false
                            searchQuery = ""
                        }
                    )
                }
            }
        }
    }
}

fun getFontWeight(style: String): FontWeight {
    return when {
        style.contains("Thin") || style == "Lato" -> FontWeight.Thin
        style.contains("Extra Light") || style == "Open Sans" -> FontWeight.ExtraLight
        style.contains("Light") || style == "Raleway" -> FontWeight.Light
        style.contains("Semi Bold") || style == "Inter" -> FontWeight.SemiBold
        style.contains("Extra Bold") || style == "Montserrat" || style == "Aptos" -> FontWeight.ExtraBold
        style.contains("Black") || style == "Archivo Black" -> FontWeight.Black
        style == "Impact" || style == "Rockwell" || style == "Anton" -> FontWeight.Bold
        style == "Bebas Neue" || style == "Staatliches" -> FontWeight.Bold
        else -> FontWeight.Medium
    }
}

fun getFontFamily(style: String): FontFamily {
    val serifFonts = listOf("Georgia", "Times New Roman", "Garamond", "Palatino", "Baskerville", "Didot", "Cambria", "Book Antiqua", "Century Schoolbook", "PT Serif", "Spectral", "Cormorant Garamond", "Bitter", "Old Standard TT", "Cinzel", "Cardo", "Domine", "Bodoni Moda", "Alegreya", "Faustina", "Zilla Slab", "BioRhyme", "Fraunces", "Serif")
    val monoFonts = listOf("Courier New", "Lucida Console", "Consolas", "Roboto Mono", "Source Code Pro", "Inconsolata", "Space Mono", "Ubuntu Mono", "Fira Code", "JetBrains Mono", "Monospace")
    val cursiveFonts = listOf("Caveat", "Dancing Script", "Shadows Into Light", "Great Vibes", "Satisfy", "Indie Flower", "Permanent Marker", "Sacramento", "Kalam", "Yellowtail", "Comic Sans MS", "Cursive")
    
    return when {
        serifFonts.any { style.contains(it) } -> FontFamily.Serif
        monoFonts.any { style.contains(it) } -> FontFamily.Monospace
        cursiveFonts.any { style.contains(it) } -> FontFamily.Cursive
        style.contains("Sans-Serif") || style == "Arial" || style == "Helvetica" || style == "Verdana" || style == "Tahoma" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
}

@Composable
fun ListSwitcherDialog(viewModel: ListViewModel, onDismiss: () -> Unit) {
    val allLists by viewModel.allLists.collectAsStateWithLifecycle()
    val currentId by viewModel.currentListId.collectAsStateWithLifecycle()
    
    var sortType by remember { mutableStateOf("Last Modified") }
    val sortedLists = remember(allLists, sortType) {
        when (sortType) {
            "Alphabetical" -> allLists.sortedBy { it.title }
            "Created Date" -> allLists.sortedByDescending { it.createdTimestamp }
            else -> allLists.sortedByDescending { it.lastModifiedTimestamp }
        }
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Your Lists", style = MaterialTheme.typography.titleLarge)
                    
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Sort")
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
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card(onClick = { viewModel.createNewList("New List"); onDismiss() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            text = { Text("Are you sure you want to delete '${list.title}' and all its items? This cannot be undone.") },
                            confirmButton = {
                                Button(
                                    onClick = { 
                                        viewModel.deleteListById(list.id)
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
                        onClick = { viewModel.selectList(list.id); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        border = if (list.id == currentId) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = list.title, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete List", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("Close") } }
    )
}
