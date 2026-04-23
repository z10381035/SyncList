package org.example.synclist

import androidx.compose.runtime.*

interface UndoRedoAction {
    fun undo()
    fun redo()
}

class UndoRedoManager {
    private val undoStack = mutableStateListOf<UndoRedoAction>()
    private val redoStack = mutableStateListOf<UndoRedoAction>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun execute(action: UndoRedoAction) {
        action.redo() // Execute the action first
        undoStack.add(action)
        redoStack.clear()
        if (undoStack.size > 50) undoStack.removeAt(0)
    }

    // Version that only records an action already executed
    fun add(action: UndoRedoAction) {
        undoStack.add(action)
        redoStack.clear()
        if (undoStack.size > 50) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack.size - 1)
            action.undo()
            redoStack.add(action)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeAt(redoStack.size - 1)
            action.redo()
            undoStack.add(action)
        }
    }
}

class ToggleAction(
    private val item: ListItem,
    private val viewModel: ListViewModel
) : UndoRedoAction {
    override fun undo() = viewModel.toggleItem(item)
    override fun redo() = viewModel.toggleItem(item)
}

class MoveAction(
    private val fromIndex: Int,
    private val toIndex: Int,
    private val viewModel: ListViewModel
) : UndoRedoAction {
    override fun undo() = viewModel.moveItem(toIndex, fromIndex)
    override fun redo() = viewModel.moveItem(fromIndex, toIndex)
}

class RenameAction(
    private val oldTitle: String,
    private val newTitle: String,
    private val onRename: (String) -> Unit
) : UndoRedoAction {
    override fun undo() = onRename(oldTitle)
    override fun redo() = onRename(newTitle)
}

class AddAction(
    private val item: ListItem,
    private val viewModel: ListViewModel
) : UndoRedoAction {
    override fun undo() = viewModel.deleteItem(item)
    override fun redo() = viewModel.restoreItem(item)
}

class DeleteAction(
    private val item: ListItem,
    private val viewModel: ListViewModel
) : UndoRedoAction {
    override fun undo() = viewModel.restoreItem(item)
    override fun redo() = viewModel.deleteItem(item)
}
