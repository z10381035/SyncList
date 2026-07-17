package org.example.synclist

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class ListRepository {
    private val firestore = Firebase.firestore
    private val listsCollection = firestore.collection("lists")

    fun getAllLists(): Flow<List<ListMetadata>> {
        return listsCollection.snapshots.map { snapshot ->
            snapshot.documents.map { doc ->
                doc.data<ListMetadata>().copy(id = doc.id)
            }
        }
    }

    suspend fun createList(title: String): String {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val metadata = ListMetadata(
            title = title,
            createdTimestamp = now,
            lastModifiedTimestamp = now,
        )
        val doc = listsCollection.add(metadata)
        return doc.id
    }

    suspend fun updateListMetadata(listId: String, title: String) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        listsCollection.document(listId).update(
            "title" to title,
            "lastModifiedTimestamp" to now,
        )
    }

    suspend fun updateLastModified(listId: String) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        listsCollection.document(listId).update("lastModifiedTimestamp" to now)
    }

    suspend fun deleteListWithItems(listId: String) {
        val itemsCollection = getItemsCollection(listId)
        val items = itemsCollection.get().documents
        val batch = firestore.batch()
        items.forEach { doc ->
            batch.delete(itemsCollection.document(doc.id))
        }
        batch.delete(listsCollection.document(listId))
        batch.commit()
    }

    suspend fun deleteList(listId: String) {
        // Note: Sub-collections are not automatically deleted in Firestore
        // For a production app, we would delete items first.
        listsCollection.document(listId).delete()
    }

    private fun getItemsCollection(listId: String) = 
        if (listId.isEmpty()) {
            // Return a dummy collection to avoid crashing, but operations will fail/be ignored
            listsCollection.document("invalid_id").collection("items")
        } else {
            listsCollection.document(listId).collection("items")
        }

    fun getItems(listId: String): Flow<List<ListItem>> {
        if (listId.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return getItemsCollection(listId).orderBy("position").snapshots.map { snapshot ->
            snapshot.documents.map { doc ->
                doc.data<ListItem>().copy(id = doc.id)
            }
        }
    }

    suspend fun saveItem(listId: String, item: ListItem) {
        if (listId.isEmpty()) return
        getItemsCollection(listId).document(item.id).set(item)
        updateLastModified(listId)
    }

    suspend fun getMinPosition(listId: String): Double {
        if (listId.isEmpty()) return 0.0
        return getItemsCollection(listId).orderBy("position", Direction.ASCENDING)
            .limit(1)
            .get()
            .documents
            .firstOrNull()
            ?.data<ListItem>()
            ?.position ?: 0.0
    }

    suspend fun getMaxPosition(listId: String): Double {
        if (listId.isEmpty()) return 0.0
        return getItemsCollection(listId).orderBy("position", Direction.DESCENDING)
            .limit(1)
            .get()
            .documents
            .firstOrNull()
            ?.data<ListItem>()
            ?.position ?: 0.0
    }

    suspend fun setItemChecked(listId: String, itemId: String, isChecked: Boolean) {
        if (listId.isEmpty()) return
        getItemsCollection(listId).document(itemId).update("isChecked" to isChecked)
        updateLastModified(listId)
    }

    suspend fun updateItemPosition(listId: String, itemId: String, newPosition: Double) {
        if (listId.isEmpty()) return
        getItemsCollection(listId).document(itemId).update("position" to newPosition)
        updateLastModified(listId)
    }

    suspend fun toggleItem(listId: String, item: ListItem) {
        if (listId.isEmpty()) return
        getItemsCollection(listId).document(item.id).update("isChecked" to !item.isChecked)
        updateLastModified(listId)
    }

    suspend fun deleteItem(listId: String, item: ListItem) {
        if (listId.isEmpty()) return
        getItemsCollection(listId).document(item.id).delete()
        updateLastModified(listId)
    }

    suspend fun updateItemPositions(listId: String, items: List<ListItem>) {
        if (listId.isEmpty()) return
        val batch = firestore.batch()
        val collection = getItemsCollection(listId)
        items.forEachIndexed { index, item ->
            batch.update(collection.document(item.id), "position" to index.toDouble())
        }
        batch.commit()
        updateLastModified(listId)
    }
}
