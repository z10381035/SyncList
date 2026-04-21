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

    fun addItem(text: String) {
        viewModelScope.launch {
            repository.addItem(text)
        }
    }

    fun toggleItem(item: ListItem) {
        viewModelScope.launch {
            repository.toggleItem(item)
        }
    }

    fun deleteItem(item: ListItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }
}
