package com.lumera.app.ui.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.profile.ProfileConfigurationManager
import com.lumera.app.data.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

sealed class AddonEvent {
    data object InstallationSuccess : AddonEvent()
    data class InstallationFailure(val message: String) : AddonEvent()
}

data class AddonInstallConfig(
    val url: String,
    val addonName: String,
    val catalogCount: Int
)

@HiltViewModel
class AddonsViewModel @Inject constructor(
    private val repository: AddonRepository,
    private val profileConfigurationManager: ProfileConfigurationManager
) : ViewModel() {

    private fun persistProfileState() {
        viewModelScope.launch { profileConfigurationManager.saveActiveRuntimeState() }
    }

    data class UiState(
        val addons: List<AddonEntity> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val pendingInstall: AddonInstallConfig? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<AddonEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { loadAddons() }

    private fun loadAddons() {
        viewModelScope.launch {
            repository.getAddons().collect { addonList ->
                _uiState.value = _uiState.value.copy(addons = addonList)
            }
        }
    }

    fun prepareInstall(url: String) {
        if (url.isBlank()) return
        val scheme = android.net.Uri.parse(url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            _uiState.value = _uiState.value.copy(error = "Only HTTP/HTTPS addon URLs are supported")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val validUrl = if (url.endsWith("manifest.json")) url else "$url/manifest.json"
                val manifest = repository.fetchManifest(validUrl)
                val displayableCatalogs = manifest.catalogs.orEmpty().filter { catalog ->
                    val isStandardType = catalog.type == "movie" || catalog.type == "series" || catalog.type == "channel" || catalog.type == "tv"
                    isStandardType
                }
                if (displayableCatalogs.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pendingInstall = AddonInstallConfig(validUrl, manifest.name, displayableCatalogs.size)
                    )
                } else {
                    confirmInstall(validUrl, false, false, false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _events.send(AddonEvent.InstallationFailure(e.message ?: "Error"))
            }
        }
    }

    fun confirmInstall(url: String, home: Boolean, movies: Boolean, series: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, pendingInstall = null)
            try {
                repository.installAddonWithConfig(url, home, movies, series)
                persistProfileState()
                profileConfigurationManager.resetStartupCapture()
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.send(AddonEvent.InstallationSuccess)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun cancelInstall() {
        _uiState.value = _uiState.value.copy(pendingInstall = null)
    }

    fun deleteAddon(transportUrl: String) { viewModelScope.launch { repository.deleteAddon(transportUrl); persistProfileState() } }
    fun renameAddon(transportUrl: String, newName: String) { viewModelScope.launch { repository.renameAddon(transportUrl, newName); persistProfileState() } }

    fun moveAddon(addon: AddonEntity, direction: Int) {
        val currentList = _uiState.value.addons.toMutableList()
        val index = currentList.indexOfFirst { it.transportUrl == addon.transportUrl }
        if (index == -1) return
        val newIndex = index + direction
        if (newIndex !in currentList.indices) return

        Collections.swap(currentList, index, newIndex)
        val updated = currentList.mapIndexed { i, item -> item.copy(sortOrder = i) }
        _uiState.value = _uiState.value.copy(addons = updated)
        viewModelScope.launch { repository.updateAddons(updated); persistProfileState() }
    }
}
