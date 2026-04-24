package org.example.synclist

import androidx.compose.runtime.*

interface UndoRedoAction {
    fun undo()
    fun redo()
}

/**
 * Global state variables that Compose can watch from anywhere in the app.
 */
var globalCanUndo by mutableStateOf(false)
var globalCanRedo by mutableStateOf(false)

/**
 * A central, global history system that tracks the last 100 actions.
 */
object GlobalUndoRedoManager {
    private val undoStack = mutableListOf<UndoRedoAction>()
    private val redoStack = mutableListOf<UndoRedoAction>()

    fun add(action: UndoRedoAction) {
        undoStack.add(action)
        redoStack.clear()
        if (undoStack.size > 100) {
            undoStack.removeAt(0)
        }
        updateFlags()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack.size - 1)
            action.undo()
            redoStack.add(action)
            updateFlags()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeAt(redoStack.size - 1)
            action.redo()
            undoStack.add(action)
            updateFlags()
        }
    }

    private fun updateFlags() {
        globalCanUndo = undoStack.isNotEmpty()
        globalCanRedo = redoStack.isNotEmpty()
    }
}

class ToggleAction(
    private val itemId: String,
    private val oldState: Boolean,
    private val newState: Boolean,
    private val viewModel: ListViewModel
) : UndoRedoAction {
    override fun undo() = viewModel.setItemChecked(itemId, oldState)
    override fun redo() = viewModel.setItemChecked(itemId, newState)
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
    override fun redo() = viewModel.addItemDirectly(item)
}

class DeleteAction(
    private val item: ListItem,
    private val viewModel: ListViewModel
) : UndoRedoAction {
    override fun undo() = viewModel.addItemDirectly(item)
    override fun redo() = viewModel.deleteItem(item)
}
