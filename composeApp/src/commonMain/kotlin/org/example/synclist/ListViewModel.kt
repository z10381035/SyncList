package org.example.synclist

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ListTab {
    NotCompleted, Completed
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(private val repository: ListRepository) : ViewModel() {
    private val settings = SettingsProvider.get()

    // --- List Selection & Metadata ---
    
    val allLists: StateFlow<List<ListMetadata>> = repository.getAllLists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _currentListId = MutableStateFlow(settings.getString("currentListId", ""))
    val currentListId: StateFlow<String> = _currentListId

    val items: StateFlow<List<ListItem>> = _currentListId
        .flatMapLatest { id ->
            if (id.isEmpty()) {
                MutableStateFlow(emptyList())
            } else {
                repository.getItems(id)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val currentListMetadata: StateFlow<ListMetadata?> = combine(allLists, _currentListId) { lists, id ->
        lists.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- Persistent Visual States (Studio Personalization) ---
    
    private var _appBarColor by mutableStateOf(loadColor("appBarColor"))
    var appBarColor: Color?
        get() = _appBarColor
        private set(value) { _appBarColor = value }

    private var _listBackgroundColor by mutableStateOf(loadColor("listBackgroundColor"))
    var listBackgroundColor: Color?
        get() = _listBackgroundColor
        private set(value) { _listBackgroundColor = value }

    private var _isDarkMode by mutableStateOf(settings.getBoolean("isDarkMode", defaultValue = false))
    var isDarkMode: Boolean
        get() = _isDarkMode
        private set(value) { _isDarkMode = value }

    private var _zoomLevel by mutableStateOf(settings.getFloat("zoomLevel", 1f))
    var zoomLevel: Float
        get() = _zoomLevel
        private set(value) { _zoomLevel = value }

    private var _fontSize by mutableStateOf(settings.getFloat("fontSize", 16f))
    var fontSize: Float
        get() = _fontSize
        private set(value) { _fontSize = value }

    private var _fontStyle by mutableStateOf(settings.getString("fontStyle", "Default"))
    var fontStyle: String
        get() = _fontStyle
        private set(value) { _fontStyle = value }

    private var _isBold by mutableStateOf(settings.getBoolean("isBold", false))
    var isBold: Boolean
        get() = _isBold
        private set(value) { _isBold = value }

    private var _isItalic by mutableStateOf(settings.getBoolean("isItalic", false))
    var isItalic: Boolean
        get() = _isItalic
        private set(value) { _isItalic = value }

    private var _isUnderlined by mutableStateOf(settings.getBoolean("isUnderlined", false))
    var isUnderlined: Boolean
        get() = _isUnderlined
        private set(value) { _isUnderlined = value }

    private var _checkmarkStyle by mutableStateOf(settings.getString("checkmarkStyle", "Checkmark"))
    var checkmarkStyle: String
        get() = _checkmarkStyle
        private set(value) { _checkmarkStyle = value }

    private var _checkmarkColor by mutableStateOf(loadColor("checkmarkColor"))
    var checkmarkColor: Color?
        get() = _checkmarkColor
        private set(value) { _checkmarkColor = value }

    private var _checkmarkPosition by mutableStateOf(settings.getString("checkmarkPosition", "Left"))
    var checkmarkPosition: String
        get() = _checkmarkPosition
        private set(value) { _checkmarkPosition = value }

    private var _showCheckmarkBox by mutableStateOf(settings.getBoolean("showCheckmarkBox", true))
    var showCheckmarkBox: Boolean
        get() = _showCheckmarkBox
        private set(value) { _showCheckmarkBox = value }

    private var _isCheckmarkHighContrast by mutableStateOf(settings.getBoolean("isCheckmarkHighContrast", true))
    var isCheckmarkHighContrast: Boolean
        get() = _isCheckmarkHighContrast
        private set(value) { _isCheckmarkHighContrast = value }

    private var _crossOutColor by mutableStateOf(loadColor("crossOutColor"))
    var crossOutColor: Color?
        get() = _crossOutColor
        private set(value) { _crossOutColor = value }

    private var _isCrossOutHighContrast by mutableStateOf(settings.getBoolean("isCrossOutHighContrast", true))
    var isCrossOutHighContrast: Boolean
        get() = _isCrossOutHighContrast
        private set(value) { _isCrossOutHighContrast = value }

    private var _crossOutOpacity by mutableStateOf(settings.getFloat("crossOutOpacity", 0.5f))
    var crossOutOpacity: Float
        get() = _crossOutOpacity
        private set(value) { _crossOutOpacity = value }

    val crossOutOptions: SnapshotStateList<String> = mutableStateListOf<String>().apply {
        val saved = settings.getString("crossOutOptions", "")
        if (saved.isNotEmpty()) {
            addAll(saved.split(","))
        }
    }
    
    private var _straightThickness by mutableStateOf(settings.getFloat("straightThickness", 2.0f))
    var straightThickness: Float
        get() = _straightThickness
        private set(value) { _straightThickness = value }

    private var _grayOutChecked by mutableStateOf(settings.getBoolean("grayOutChecked", false))
    var grayOutChecked: Boolean
        get() = _grayOutChecked
        private set(value) { _grayOutChecked = value }

    private var _wavyWavelength by mutableStateOf(settings.getFloat("wavyWavelength", 20f))
    var wavyWavelength: Float
        get() = _wavyWavelength
        private set(value) { _wavyWavelength = value }

    private var _wavyExtraHeight by mutableStateOf(settings.getBoolean("wavyExtraHeight", false))
    var wavyExtraHeight: Boolean
        get() = _wavyExtraHeight
        private set(value) { _wavyExtraHeight = value }

    private var _scribbleIntensity by mutableStateOf(settings.getFloat("scribbleIntensity", 0.5f))
    var scribbleIntensity: Float
        get() = _scribbleIntensity
        private set(value) { _scribbleIntensity = value }

    private var _undulationFrequency by mutableStateOf(settings.getFloat("undulationFrequency", 1.0f))
    var undulationFrequency: Float
        get() = _undulationFrequency
        private set(value) { _undulationFrequency = value }

    private var _separateCompletedList by mutableStateOf(settings.getBoolean("separateCompletedList", false))
    var separateCompletedList: Boolean
        get() = _separateCompletedList
        private set(value) { _separateCompletedList = value }

    private var _tdmPosition by mutableStateOf(settings.getString("tdmPosition", "Right"))
    var tdmPosition: String
        get() = _tdmPosition
        private set(value) { _tdmPosition = value }

    private var _showUndo by mutableStateOf(settings.getBoolean("showUndo", true))
    var showUndo: Boolean
        get() = _showUndo
        private set(value) { _showUndo = value }

    private var _showRedo by mutableStateOf(settings.getBoolean("showRedo", true))
    var showRedo: Boolean
        get() = _showRedo
        private set(value) { _showRedo = value }

    private var _isCompactUi by mutableStateOf(settings.getBoolean("isCompactUi", false))
    var isCompactUi: Boolean
        get() = _isCompactUi
        private set(value) { _isCompactUi = value }

    var selectedListTab by mutableStateOf(ListTab.NotCompleted)

    // --- UI Session States (Survive Rotation) ---
    var isEditingTitle by mutableStateOf(false)
        private set

    private var _editingTitleText by mutableStateOf("")
    
    val listTitle: String
        get() = if (isEditingTitle) {
            _editingTitleText
        } else {
            // Priority: Local text (if set), then metadata title, then default
            _editingTitleText.ifEmpty { 
                currentListMetadata.value?.title ?: "SyncList"
            }
        }

    var previousTitle by mutableStateOf("")
        private set
    
    var currentScreen by mutableStateOf(Screen.List)
        private set
    var isMenuExpanded by mutableStateOf(false)
        private set
    var isSearchMode by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set

    private var _showMetadata by mutableStateOf(settings.getBoolean("showMetadata", true))
    var showMetadata: Boolean
        get() = _showMetadata
        private set(value) { _showMetadata = value }
    
    var showColorPicker by mutableStateOf(false)
        private set
    var colorTarget by mutableStateOf("Top Bar")
        private set

    val savedCustomColors: SnapshotStateList<Color?> = mutableStateListOf<Color?>().apply {
        val saved = settings.getString("savedCustomColors", "")
        if (saved.isNotEmpty()) {
            val list = saved.split(",").map { 
                if (it == "null") null 
                else {
                    try {
                        Color(it.toLong().toInt())
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            addAll(list)
        } else {
            addAll(listOf(null, null, null, null, null, null, null))
        }
    }

    val createdTimestamp: Long
        get() = currentListMetadata.value?.createdTimestamp ?: 0L
    val lastModifiedTimestamp: Long
        get() = currentListMetadata.value?.lastModifiedTimestamp ?: 0L

    // Drag-and-Drop Session State
    var draggingItemId by mutableStateOf<String?>(null)
        private set

    private var _lastMetadataTitle: String? = null
    private var _lastListId: String? = null

    init {
        // Initialize first list immediately if we have a saved ID
        val savedId = settings.getString("currentListId", "")
        if (savedId.isNotEmpty()) {
            _currentListId.value = savedId
        }

        // Wait for the flow to emit the first set of lists to handle fresh installs or syncs
        viewModelScope.launch {
            allLists.collect { lists ->
                if (_currentListId.value.isEmpty()) {
                    if (lists.isNotEmpty()) {
                        selectList(lists.first().id)
                    } else {
                        val id = repository.createList("SyncList")
                        selectList(id)
                    }
                }
                // Stop collecting once we have a valid list ID
                if (_currentListId.value.isNotEmpty()) {
                    return@collect
                }
            }
        }

        // Sync local title state with metadata changes when NOT editing
        viewModelScope.launch {
            currentListMetadata.collect { metadata ->
                if (metadata != null) {
                    val idChanged = metadata.id != _lastListId
                    val externalTitleChange = metadata.title != _lastMetadataTitle
                    
                    if (idChanged || (externalTitleChange && !isEditingTitle)) {
                        _editingTitleText = metadata.title
                    }
                    
                    _lastMetadataTitle = metadata.title
                    _lastListId = metadata.id
                }
            }
        }
    }

    // --- Persistence Methods ---

    fun selectList(id: String) {
        _currentListId.value = id
        settings.saveString("currentListId", id)
    }

    fun syncWithSettings() {
        val savedId = settings.getString("currentListId", "")
        if (savedId != _currentListId.value) {
            _currentListId.value = savedId
        }
    }

    fun createNewList(title: String) {
        viewModelScope.launch {
            val id = repository.createList(title)
            selectList(id)
        }
    }

    fun deleteListById(id: String) {
        viewModelScope.launch {
            repository.deleteListWithItems(id)
            if (_currentListId.value == id) {
                val remaining = allLists.value.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    selectList(remaining.first().id)
                } else {
                    val newId = repository.createList("SyncList")
                    selectList(newId)
                }
            }
        }
    }

    fun updateDarkMode(enabled: Boolean) {
        isDarkMode = enabled
        settings.saveBoolean("isDarkMode", enabled)
    }

    fun updateShowMetadata(show: Boolean) {
        showMetadata = show
        settings.saveBoolean("showMetadata", show)
    }

    fun updateZoomLevel(level: Float) {
        zoomLevel = level
        settings.saveFloat("zoomLevel", level)
    }

    fun updateFontSize(size: Float) {
        fontSize = size
        settings.saveFloat("fontSize", size)
    }

    fun updateFontStyle(style: String) {
        fontStyle = style
        settings.saveString("fontStyle", style)
    }

    fun updateBold(bold: Boolean) {
        isBold = bold
        settings.saveBoolean("isBold", bold)
    }

    fun updateItalic(italic: Boolean) {
        isItalic = italic
        settings.saveBoolean("isItalic", italic)
    }

    fun updateUnderlined(underlined: Boolean) {
        isUnderlined = underlined
        settings.saveBoolean("isUnderlined", underlined)
    }

    fun updateShowCheckmarkBox(show: Boolean) {
        showCheckmarkBox = show
        settings.saveBoolean("showCheckmarkBox", show)
    }

    fun updateCheckmarkHighContrast(highContrast: Boolean) {
        isCheckmarkHighContrast = highContrast
        settings.saveBoolean("isCheckmarkHighContrast", highContrast)
    }

    fun updateCrossOutHighContrast(highContrast: Boolean) {
        isCrossOutHighContrast = highContrast
        settings.saveBoolean("isCrossOutHighContrast", highContrast)
    }

    fun updateCrossOutOpacity(opacity: Float) {
        crossOutOpacity = opacity
        settings.saveFloat("crossOutOpacity", opacity)
    }

    fun updateStraightThickness(thickness: Float) {
        straightThickness = thickness
        settings.saveFloat("straightThickness", thickness)
    }

    fun updateGrayOutChecked(grayOut: Boolean) {
        grayOutChecked = grayOut
        settings.saveBoolean("grayOutChecked", grayOut)
    }

    fun updateWavyWavelength(wavelength: Float) {
        wavyWavelength = wavelength
        settings.saveFloat("wavyWavelength", wavelength)
    }

    fun updateWavyExtraHeight(extra: Boolean) {
        wavyExtraHeight = extra
        settings.saveBoolean("wavyExtraHeight", extra)
    }

    fun updateScribbleIntensity(intensity: Float) {
        scribbleIntensity = intensity
        settings.saveFloat("scribbleIntensity", intensity)
    }

    fun updateUndulationFrequency(frequency: Float) {
        undulationFrequency = frequency
        settings.saveFloat("undulationFrequency", frequency)
    }

    fun updateSeparateCompletedList(separate: Boolean) {
        separateCompletedList = separate
        settings.saveBoolean("separateCompletedList", separate)
    }

    fun updateTdmPosition(position: String) {
        tdmPosition = position
        settings.saveString("tdmPosition", position)
    }

    fun updateShowUndo(show: Boolean) {
        showUndo = show
        settings.saveBoolean("showUndo", show)
    }

    fun updateShowRedo(show: Boolean) {
        showRedo = show
        settings.saveBoolean("showRedo", show)
    }

    fun updateCompactUi(compact: Boolean) {
        isCompactUi = compact
        settings.saveBoolean("isCompactUi", compact)
    }

    fun updateEditingTitle(editing: Boolean) {
        if (editing) {
            _editingTitleText = listTitle
            previousTitle = listTitle
        }
        isEditingTitle = editing
    }

    fun updateListTitle(title: String) {
        _editingTitleText = title
        val id = _currentListId.value
        if (id.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.updateListMetadata(id, title)
                // Update _lastMetadataTitle to prevent the collector from overwriting the local edit
                _lastMetadataTitle = title
            } catch (_: Exception) {
                // Fail silently
            }
        }
    }

    fun updateAppBarColor(color: Color?) {
        appBarColor = color
        saveColor("appBarColor", color)
    }

    fun updateListBackgroundColor(color: Color?) {
        listBackgroundColor = color
        saveColor("listBackgroundColor", color)
    }

    fun updateCheckmarkColor(color: Color?) {
        checkmarkColor = color
        saveColor("checkmarkColor", color)
    }

    fun updateCrossOutColor(color: Color?) {
        crossOutColor = color
        saveColor("crossOutColor", color)
    }

    fun updateCheckmarkStyle(style: String) {
        checkmarkStyle = style
        settings.saveString("checkmarkStyle", style)
    }

    fun updateCheckmarkPosition(position: String) {
        checkmarkPosition = position
        settings.saveString("checkmarkPosition", position)
    }

    fun toggleCrossOutOption(style: String) {
        if (crossOutOptions.contains(style)) {
            crossOutOptions.remove(style)
        } else {
            crossOutOptions.add(style)
        }
        settings.saveString("crossOutOptions", crossOutOptions.joinToString(","))
    }

    fun setCustomColor(index: Int, color: Color?) {
        if (index in savedCustomColors.indices) {
            savedCustomColors[index] = color
            val data = savedCustomColors.joinToString(",") { it?.toArgb()?.toLong()?.toString() ?: "null" }
            settings.saveString("savedCustomColors", data)
        }
    }

    fun updatePreviousTitle(title: String) {
        previousTitle = title
    }

    fun updateCurrentScreen(screen: Screen) {
        currentScreen = screen
    }

    fun updateMenuExpanded(expanded: Boolean) {
        isMenuExpanded = expanded
    }

    fun updateSearchMode(search: Boolean) {
        isSearchMode = search
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun updateShowColorPicker(show: Boolean) {
        showColorPicker = show
    }

    fun updateColorTarget(target: String) {
        colorTarget = target
    }

    fun updateDraggingItemId(id: String?) {
        draggingItemId = id
    }

    private fun saveColor(key: String, color: Color?) {
        if (color == null) {
            settings.saveString(key, "null")
        } else {
            // Save as Long to prevent overflow issues with ARGB Ints
            settings.saveString(key, color.toArgb().toLong().toString())
        }
    }

    private fun loadColor(key: String): Color? {
        val saved = settings.getString(key, "null")
        if (saved == "null") return null
        return try {
            // Try parsing as Long then to Int to handle negative/overflowing values
            Color(saved.toLong().toInt())
        } catch (_: Exception) {
            null
        }
    }

    fun resetToDefault() {
        updateAppBarColor(null)
        updateListBackgroundColor(null)
        updateDarkMode(false)
        updateZoomLevel(1f)
        updateFontSize(16f)
        updateFontStyle("Default")
        updateBold(false)
        updateItalic(false)
        updateUnderlined(false)
        updateCheckmarkStyle("Checkmark")
        updateCheckmarkPosition("Left")
        updateCheckmarkColor(null)
        updateShowCheckmarkBox(true)
        updateCheckmarkHighContrast(true)
        updateCrossOutHighContrast(true)
        updateCrossOutColor(null)
        updateStraightThickness(2.0f)
        updateGrayOutChecked(false)
        updateWavyWavelength(20f)
        updateWavyExtraHeight(false)
        updateScribbleIntensity(0.5f)
        updateUndulationFrequency(1.0f)
        updateCrossOutOpacity(0.5f)
        updateSeparateCompletedList(false)
        updateTdmPosition("Right")
        updateShowUndo(true)
        updateShowRedo(true)
        updateCompactUi(false)
        crossOutOptions.clear()
        settings.saveString("crossOutOptions", "")
    }

    // --- Repository Methods ---

    fun addItemDirectly(item: ListItem) {
        val id = _currentListId.value
        if (id.isEmpty()) return
        viewModelScope.launch(Dispatchers.Main) {
            repository.saveItem(id, item)
        }
    }

    fun updateItemText(item: ListItem, newText: String) {
        val id = _currentListId.value
        if (id.isEmpty()) return
        viewModelScope.launch(Dispatchers.Main) {
            repository.saveItem(id, item.copy(text = newText))
        }
    }

    fun updateLastModifiedTimestamp() {
        val id = _currentListId.value
        if (id.isEmpty()) return
        viewModelScope.launch {
            repository.updateLastModified(id)
        }
    }

    suspend fun getNextPosition(atTop: Boolean): Double = withContext(Dispatchers.Main) {
        val id = _currentListId.value
        if (id.isEmpty()) return@withContext 0.0
        
        if (atTop) {
            repository.getMinPosition(id) - 1.0
        } else {
            repository.getMaxPosition(id) + 1.0
        }
    }

    fun toggleItem(item: ListItem) {
        val id = _currentListId.value
        if (id.isEmpty()) return
        viewModelScope.launch(Dispatchers.Main) {
            repository.toggleItem(id, item)
        }
    }

    fun setItemChecked(id: String, isChecked: Boolean) {
        val listId = _currentListId.value
        if (listId.isEmpty()) return
        viewModelScope.launch(Dispatchers.Main) {
            repository.setItemChecked(listId, id, isChecked)
        }
    }

    fun deleteItem(item: ListItem) {
        val id = _currentListId.value
        if (id.isEmpty()) return
        viewModelScope.launch(Dispatchers.Main) {
            repository.deleteItem(id, item)
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentItems = items.value.toMutableList()
            if ((fromIndex !in currentItems.indices) || (toIndex !in currentItems.indices)) return@launch
            if (fromIndex == toIndex) return@launch

            val item = currentItems.removeAt(fromIndex)
            currentItems.add(toIndex, item)

            // Fractional Indexing Optimization
            val newPos: Double = when {
                toIndex == 0 -> currentItems[1].position - 1.0
                toIndex == currentItems.size - 1 -> currentItems[currentItems.size - 2].position + 1.0
                else -> {
                    val prevPos = currentItems[toIndex - 1].position
                    val nextPos = currentItems[toIndex + 1].position
                    
                    // Precision Guard: If the gap is too small, rebalance the entire list
                    if (kotlin.math.abs(prevPos - nextPos) < 1e-10) {
                        repository.updateItemPositions(_currentListId.value, currentItems)
                        return@launch
                    }
                    (prevPos + nextPos) / 2.0
                }
            }

            repository.updateItemPosition(_currentListId.value, item.id, newPos)
        }
    }
}
