package org.example.synclist

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class ListRepository {
    private val firestore = Firebase.firestore
    private val collection = firestore.collection("items")

    fun getItems(): Flow<List<ListItem>> {
        return collection.orderBy("position").snapshots.map { snapshot ->
            snapshot.documents.map { doc ->
                doc.data<ListItem>().copy(id = doc.id)
            }
        }
    }

    suspend fun addItem(text: String) {
        val maxPosition = collection.orderBy("position", Direction.DESCENDING)
            .limit(1)
            .get()
            .documents
            .firstOrNull()
            ?.data<ListItem>()
            ?.position ?: 0.0

        val newItem = ListItem(
            text = text, 
            timestamp = Clock.System.now().toEpochMilliseconds(),
            position = maxPosition + 1.0
        )
        collection.add(newItem)
    }

    suspend fun toggleItem(item: ListItem) {
        collection.document(item.id).update("isChecked" to !item.isChecked)
    }

    suspend fun deleteItem(item: ListItem) {
        collection.document(item.id).delete()
    }
}
