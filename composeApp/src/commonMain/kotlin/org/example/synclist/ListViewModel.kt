package org.example.synclist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListViewModel(private val repository: ListRepository) : ViewModel() {
    val items: StateFlow<List<ListItem>> = repository.getItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addItem(text: String, onAdded: ((ListItem) -> Unit)? = null) {
        viewModelScope.launch {
            val item = repository.addItem(text)
            onAdded?.invoke(item)
        }
    }

    fun addItemDirectly(item: ListItem) {
        viewModelScope.launch {
            repository.restoreItem(item)
        }
    }

    fun toggleItem(item: ListItem) {
        viewModelScope.launch {
            repository.toggleItem(item)
        }
    }

    fun setItemChecked(id: String, isChecked: Boolean) {
        viewModelScope.launch {
            repository.setItemChecked(id, isChecked)
        }
    }

    fun deleteItem(item: ListItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun restoreItem(item: ListItem) {
        viewModelScope.launch {
            repository.restoreItem(item)
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentItems = items.value.toMutableList()
            if (fromIndex !in currentItems.indices || toIndex !in currentItems.indices) return@launch

            val item = currentItems.removeAt(fromIndex)
            currentItems.add(toIndex, item)

            repository.updateItemPositions(currentItems)
        }
    }
}
