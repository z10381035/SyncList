package org.example.synclist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListViewModel(private val repository: ListRepository) : ViewModel() {
    val items: StateFlow<List<ListItem>> = repository.getItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addItemDirectly(item: ListItem) {
        viewModelScope.launch(Dispatchers.Main) {
            repository.saveItem(item)
        }
    }

    suspend fun getNextPosition(atTop: Boolean): Double = withContext(Dispatchers.Main) {
        if (atTop) {
            repository.getMinPosition() - 1.0
        } else {
            repository.getMaxPosition() + 1.0
        }
    }

    fun toggleItem(item: ListItem) {
        viewModelScope.launch(Dispatchers.Main) {
            repository.toggleItem(item)
        }
    }

    fun setItemChecked(id: String, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            repository.setItemChecked(id, isChecked)
        }
    }

    fun deleteItem(item: ListItem) {
        viewModelScope.launch(Dispatchers.Main) {
            repository.deleteItem(item)
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentItems = items.value.toMutableList()
            if (fromIndex !in currentItems.indices || toIndex !in currentItems.indices) return@launch

            val item = currentItems.removeAt(fromIndex)
            currentItems.add(toIndex, item)

            repository.updateItemPositions(currentItems)
        }
    }
}
