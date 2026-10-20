package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.keemobile.kotpass.constants.BasicField
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.modifyEntry
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.database.modifiers.moveEntry
import app.keemobile.kotpass.database.modifiers.removeEntry
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.Group
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(FlowPreview::class)
class KeePassViewModel : ViewModel() {

    // 1. Internal database reference - NOT exposed directly to UI to prevent full-screen recomposition storms
    private val _database = MutableStateFlow<KeePassDatabase?>(null)

    // Current group state
    private val _currentGroup = MutableStateFlow<Group?>(null)
    val currentGroup: StateFlow<Group?> = _currentGroup.asStateFlow()

    // Fine-grained state flows for UI observation
    private val _currentGroupEntries = MutableStateFlow<List<Entry>>(emptyList())
    val currentGroupEntries: StateFlow<List<Entry>> = _currentGroupEntries.asStateFlow()

    private val _currentSubGroups = MutableStateFlow<List<Group>>(emptyList())
    val currentSubGroups: StateFlow<List<Group>> = _currentSubGroups.asStateFlow()

    // 2. Debounced search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Entry>>(emptyList())
    val searchResults: StateFlow<List<Entry>> = _searchResults.asStateFlow()

    // Flattened, cached list of all entries in memory (updated only when database tree changes)
    private var allEntriesCache: List<Entry> = emptyList()

    // 3. Fast O(1) in-memory lookups for tree navigation and operations
    private val groupMap = mutableMapOf<UUID, Group>()
    private val parentGroupMap = mutableMapOf<UUID, UUID>()
    private val entryParentMap = mutableMapOf<UUID, UUID>()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .distinctUntilChanged()
                .collect { query ->
                    executeSearch(query)
                }
        }
    }

    /**
     * Set or load a KeePass database, rebuilding all caches and maps in O(N) only once upon load.
     */
    fun setDatabase(database: KeePassDatabase) {
        _database.value = database
        rebuildTreeCache(database.content.group)
        val targetGroupId = _currentGroup.value?.uuid ?: database.content.group.uuid
        updateCurrentGroup(targetGroupId)
        if (_searchQuery.value.isNotBlank()) {
            executeSearch(_searchQuery.value)
        }
    }

    /**
     * Rebuilds O(1) lookup maps and flattened entry cache when database changes.
     */
    private fun rebuildTreeCache(rootGroup: Group?) {
        groupMap.clear()
        parentGroupMap.clear()
        entryParentMap.clear()
        val entries = mutableListOf<Entry>()

        if (rootGroup != null) {
            fun traverse(current: Group, parentUuid: UUID?) {
                groupMap[current.uuid] = current
                if (parentUuid != null) {
                    parentGroupMap[current.uuid] = parentUuid
                }
                for (entry in current.entries) {
                    entryParentMap[entry.uuid] = current.uuid
                    entries.add(entry)
                }
                for (subGroup in current.groups) {
                    traverse(subGroup, current.uuid)
                }
            }
            traverse(rootGroup, null)
        }

        allEntriesCache = entries
    }

    /**
     * Select a group by its UUID with O(1) map lookup.
     */
    fun selectGroup(groupUuid: UUID) {
        updateCurrentGroup(groupUuid)
    }

    /**
     * Select a group directly.
     */
    fun selectGroup(group: Group) {
        updateCurrentGroup(group.uuid)
    }

    /**
     * Navigate up to the parent group using O(1) parent mapping.
     */
    fun navigateUp(): Boolean {
        val curGroup = _currentGroup.value ?: return false
        val parentUuid = parentGroupMap[curGroup.uuid] ?: return false
        updateCurrentGroup(parentUuid)
        return true
    }

    /**
     * O(1) lookup for group by UUID.
     */
    fun findGroupById(uuid: UUID): Group? = groupMap[uuid]

    /**
     * O(1) lookup for parent group UUID of an entry.
     */
    fun findParentGroupUuidForEntry(entryUuid: UUID): UUID? = entryParentMap[entryUuid]

    /**
     * Update the current group and emit small, granular sub-flows to prevent UI recomposition storms.
     */
    private fun updateCurrentGroup(groupUuid: UUID?) {
        val group = groupUuid?.let { groupMap[it] } ?: _database.value?.content?.group
        _currentGroup.value = group
        _currentGroupEntries.value = group?.entries ?: emptyList()
        _currentSubGroups.value = group?.groups ?: emptyList()
    }

    /**
     * Update search query - debounced via Flow to avoid tree traversal on every keystroke.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Fast search filtering against flattened in-memory cache.
     */
    private fun executeSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        val q = query.trim()
        _searchResults.value = allEntriesCache.filter { entry ->
            val title = entry.get(BasicField.Title)?.content ?: ""
            val username = entry.get(BasicField.UserName)?.content ?: ""
            val url = entry.get(BasicField.Url)?.content ?: ""
            val notes = entry.get(BasicField.Notes)?.content ?: ""
            title.contains(q, ignoreCase = true) ||
                username.contains(q, ignoreCase = true) ||
                url.contains(q, ignoreCase = true) ||
                notes.contains(q, ignoreCase = true) ||
                entry.fields.values.any { it.content.contains(q, ignoreCase = true) }
        }
    }

    // --- CRUD Operations ---

    /**
     * Create an entry in target group and update database state.
     */
    fun createEntry(targetGroupUuid: UUID, entry: Entry) {
        val db = _database.value ?: return
        val newDb = db.modifyGroup(targetGroupUuid) {
            copy(entries = entries + entry)
        }
        setDatabase(newDb)
    }

    /**
     * Update an existing entry and refresh state.
     */
    fun updateEntry(entry: Entry) {
        val db = _database.value ?: return
        val newDb = db.modifyEntry(entry.uuid) { entry }
        setDatabase(newDb)
    }

    /**
     * Delete an entry by UUID and refresh state.
     */
    fun deleteEntry(entryUuid: UUID) {
        val db = _database.value ?: return
        val newDb = db.removeEntry(entryUuid)
        setDatabase(newDb)
    }

    /**
     * Move an entry to a target group using O(1) lookups and kotpass moveEntry modifier.
     */
    fun moveEntry(entryUuid: UUID, targetGroupUuid: UUID) {
        val db = _database.value ?: return
        val newDb = db.moveEntry(entryUuid, targetGroupUuid)
        setDatabase(newDb)
    }
}
