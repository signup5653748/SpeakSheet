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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Unsaved changes state flow
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    // 2. Debounced search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Entry>>(emptyList())
    val searchResults: StateFlow<List<Entry>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null

    // Flattened, cached list of all entries in memory (rebuilt only when database structure changes)
    private var allEntriesCache: List<Entry> = emptyList()

    // 3. Fast O(1) in-memory lookups for tree navigation and operations
    private var groupByIdMap: Map<UUID, Group> = emptyMap()
    private var parentUuidMap: Map<UUID, UUID> = emptyMap()

    /**
     * Set or load a KeePass database, rebuilding all caches and maps in O(N) once,
     * then consolidating UI thread updates.
     */
    fun setDatabase(database: KeePassDatabase) {
        viewModelScope.launch(Dispatchers.Default) {
            rebuildLookupMaps(database)
            val targetGroupId = _currentGroup.value?.uuid ?: database.content.group.uuid
            val targetGroup = groupByIdMap[targetGroupId] ?: database.content.group

            withContext(Dispatchers.Main) {
                _database.value = database
                _currentGroup.value = targetGroup
                _currentGroupEntries.value = targetGroup.entries
                _currentSubGroups.value = targetGroup.groups
                _hasUnsavedChanges.value = false
            }

            if (_searchQuery.value.isNotBlank()) {
                executeSearch(_searchQuery.value)
            }
        }
    }

    /**
     * Rebuilds O(1) lookup maps and flattened entry cache when database changes.
     */
    private fun rebuildLookupMaps(db: KeePassDatabase) {
        val groups = mutableMapOf<UUID, Group>()
        val parents = mutableMapOf<UUID, UUID>()
        val entries = mutableListOf<Entry>()

        fun traverse(current: Group, parentUuid: UUID?) {
            groups[current.uuid] = current
            if (parentUuid != null) {
                parents[current.uuid] = parentUuid
            }
            for (entry in current.entries) {
                parents[entry.uuid] = current.uuid
                entries.add(entry)
            }
            for (subGroup in current.groups) {
                traverse(subGroup, current.uuid)
            }
        }

        traverse(db.content.group, null)
        groupByIdMap = groups
        parentUuidMap = parents
        allEntriesCache = entries
    }

    /**
     * Select a group by its UUID with O(1) map lookup.
     */
    fun selectGroup(groupUuid: UUID) {
        val group = groupByIdMap[groupUuid] ?: return
        _currentGroup.value = group
        _currentGroupEntries.value = group.entries
        _currentSubGroups.value = group.groups
    }

    /**
     * Select a group directly.
     */
    fun selectGroup(group: Group) {
        selectGroup(group.uuid)
    }

    /**
     * Navigate up to the parent group using O(1) parent mapping.
     */
    fun navigateUp(): Boolean {
        val curGroup = _currentGroup.value ?: return false
        val parentUuid = parentUuidMap[curGroup.uuid] ?: return false
        selectGroup(parentUuid)
        return true
    }

    /**
     * O(1) lookup for group by UUID.
     */
    fun findGroupById(uuid: UUID): Group? = groupByIdMap[uuid]

    /**
     * O(1) lookup for parent group UUID of an entry.
     */
    fun findParentGroupUuidForEntry(childUuid: UUID): UUID? = parentUuidMap[childUuid]

    /**
     * Update search query - debounced via Flow to avoid tree traversal on every keystroke.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            flowOf(query)
                .debounce(300L)
                .collectLatest { debouncedQuery ->
                    executeSearch(debouncedQuery)
                }
        }
    }

    /**
     * Fast search filtering against flattened in-memory cache without tree traversal.
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

    // --- CRUD Operations with Consolidated Main-Thread Updates ---

    /**
     * Create an entry in target group, rebuild cache, and apply state in a single withContext block.
     */
    fun createEntry(targetGroupUuid: UUID, entry: Entry) {
        viewModelScope.launch(Dispatchers.Default) {
            val db = _database.value ?: return@launch
            val newDb = db.modifyGroup(targetGroupUuid) {
                copy(entries = entries + entry)
            }
            rebuildLookupMaps(newDb)
            val curGroup = groupByIdMap[_currentGroup.value?.uuid] ?: newDb.content.group

            withContext(Dispatchers.Main) {
                _database.value = newDb
                _currentGroup.value = curGroup
                _currentGroupEntries.value = curGroup.entries
                _currentSubGroups.value = curGroup.groups
                _hasUnsavedChanges.value = true
            }

            if (_searchQuery.value.isNotBlank()) {
                executeSearch(_searchQuery.value)
            }
        }
    }

    /**
     * Update an existing entry and consolidate state updates in a single withContext block.
     */
    fun updateEntry(entry: Entry) {
        viewModelScope.launch(Dispatchers.Default) {
            val db = _database.value ?: return@launch
            val newDb = db.modifyEntry(entry.uuid) { entry }
            rebuildLookupMaps(newDb)
            val curGroup = groupByIdMap[_currentGroup.value?.uuid] ?: newDb.content.group

            withContext(Dispatchers.Main) {
                _database.value = newDb
                _currentGroup.value = curGroup
                _currentGroupEntries.value = curGroup.entries
                _currentSubGroups.value = curGroup.groups
                _hasUnsavedChanges.value = true
            }

            if (_searchQuery.value.isNotBlank()) {
                executeSearch(_searchQuery.value)
            }
        }
    }

    /**
     * Delete an entry by UUID and consolidate state updates in a single withContext block.
     */
    fun deleteEntry(entryUuid: UUID) {
        viewModelScope.launch(Dispatchers.Default) {
            val db = _database.value ?: return@launch
            val newDb = db.removeEntry(entryUuid)
            rebuildLookupMaps(newDb)
            val curGroup = groupByIdMap[_currentGroup.value?.uuid] ?: newDb.content.group

            withContext(Dispatchers.Main) {
                _database.value = newDb
                _currentGroup.value = curGroup
                _currentGroupEntries.value = curGroup.entries
                _currentSubGroups.value = curGroup.groups
                _hasUnsavedChanges.value = true
            }

            if (_searchQuery.value.isNotBlank()) {
                executeSearch(_searchQuery.value)
            }
        }
    }

    /**
     * Move an entry to a target group and consolidate state updates in a single withContext block.
     */
    fun moveEntry(entryUuid: UUID, targetGroupUuid: UUID) {
        viewModelScope.launch(Dispatchers.Default) {
            val db = _database.value ?: return@launch
            val newDb = db.moveEntry(entryUuid, targetGroupUuid)
            rebuildLookupMaps(newDb)
            val curGroup = groupByIdMap[_currentGroup.value?.uuid] ?: newDb.content.group

            withContext(Dispatchers.Main) {
                _database.value = newDb
                _currentGroup.value = curGroup
                _currentGroupEntries.value = curGroup.entries
                _currentSubGroups.value = curGroup.groups
                _hasUnsavedChanges.value = true
            }

            if (_searchQuery.value.isNotBlank()) {
                executeSearch(_searchQuery.value)
            }
        }
    }
}
