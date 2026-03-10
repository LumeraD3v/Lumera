package com.lumera.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dao: AddonDao
) : ViewModel() {

    fun updateNavPosition(profileId: Int, position: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(navPosition = position))
        }
    }

    fun updateRoundCorners(profileId: Int, roundCorners: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(roundCorners = roundCorners))
        }
    }

    fun updateHubRoundCorners(profileId: Int, hubRoundCorners: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(hubRoundCorners = hubRoundCorners))
        }
    }

    fun updateSplashEnabled(profileId: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(splashEnabled = enabled))
        }
    }

    fun updateContinueWatchingShape(profileId: Int, shape: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(continueWatchingShape = shape))
        }
    }

    fun updateTunnelingEnabled(profileId: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(tunnelingEnabled = enabled))
        }
    }

    fun updateMapDV7ToHevc(profileId: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(mapDV7ToHevc = enabled))
        }
    }

    fun updateDecoderPriority(profileId: Int, priority: Int) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(decoderPriority = priority))
        }
    }

    fun updateFrameRateMatching(profileId: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(frameRateMatching = enabled))
        }
    }

    fun updatePlayerPreference(profileId: Int, preference: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(playerPreference = preference))
        }
    }

    fun updateAutoplayNextEpisode(profileId: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(autoplayNextEpisode = enabled))
        }
    }

    fun updateAutoSelectSource(profileId: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(autoSelectSource = enabled))
        }
    }

    fun updateRememberSourceSelection(profileId: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(rememberSourceSelection = enabled))
        }
    }

    fun updateSkipIntro(profileId: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(skipIntro = enabled))
        }
    }

    fun updateAutoplayThresholdMode(profileId: Int, mode: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(autoplayThresholdMode = mode))
        }
    }

    fun updateAutoplayThresholdPercent(profileId: Int, percent: Int) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(autoplayThresholdPercent = percent))
        }
    }

    fun updateAutoplayThresholdSeconds(profileId: Int, seconds: Int) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(autoplayThresholdSeconds = seconds))
        }
    }

    fun updatePreferredAudioLanguage(profileId: Int, language: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(preferredAudioLanguage = language))
        }
    }

    fun updatePreferredAudioLanguageSecondary(profileId: Int, language: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(preferredAudioLanguageSecondary = language))
        }
    }

    fun updatePreferredSubtitleLanguage(profileId: Int, language: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(preferredSubtitleLanguage = language))
        }
    }

    fun updatePreferredSubtitleLanguageSecondary(profileId: Int, language: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(preferredSubtitleLanguageSecondary = language))
        }
    }

    fun updateSubtitleSize(profileId: Int, size: Int) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(subtitleSize = size))
        }
    }

    fun updateSubtitleOffset(profileId: Int, offset: Int) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(subtitleOffset = offset))
        }
    }

    fun updateSubtitleTextColor(profileId: Int, color: Long) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(subtitleTextColor = color))
        }
    }

    fun updateSubtitleBackgroundColor(profileId: Int, color: Long) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val profile = dao.getProfileById(profileId)
            if (profile != null) dao.insertProfile(profile.copy(subtitleBackgroundColor = color))
        }
    }
}