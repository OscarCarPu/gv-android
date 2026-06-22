package com.gv.app.ui.rutas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.local.db.ConcelloMarkEntity
import com.gv.app.data.repository.RutasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class GeoState {
    data object Loading : GeoState()
    data class Loaded(val concellos: List<Concello>) : GeoState()
    data object Error : GeoState()
}

class RutasViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: RutasRepository = app.container.rutasRepository

    private val _geo = MutableStateFlow<GeoState>(GeoState.Loading)
    val geo: StateFlow<GeoState> = _geo.asStateFlow()

    val marks: StateFlow<Map<String, ConcelloMarkEntity>> =
        repo.marks()
            .map { list -> list.associateBy { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _activeProvince = MutableStateFlow<String?>(null)
    val activeProvince: StateFlow<String?> = _activeProvince.asStateFlow()

    /** Name of the concello whose mark sheet is open, or null. */
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    init {
        loadGeometry()
        viewModelScope.launch { repo.reconcile() }
    }

    private fun loadGeometry() {
        viewModelScope.launch {
            _geo.value = try {
                GeoState.Loaded(withContext(Dispatchers.Default) { GaliciaGeo.load(getApplication()) })
            } catch (_: Exception) {
                GeoState.Error
            }
        }
    }

    fun setProvince(province: String?) {
        _activeProvince.value = if (_activeProvince.value == province) null else province
    }

    fun select(name: String) { _selected.value = name }
    fun clearSelection() { _selected.value = null }

    fun saveMark(name: String, visitedOn: String, description: String) {
        viewModelScope.launch { repo.saveMark(name, visitedOn, description) }
        clearSelection()
    }

    fun removeMark(name: String) {
        viewModelScope.launch { repo.removeMark(name) }
        clearSelection()
    }
}
