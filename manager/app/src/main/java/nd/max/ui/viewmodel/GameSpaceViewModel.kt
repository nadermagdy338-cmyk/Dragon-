/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import nd.max.core.gamespace.GameProfileRepository
import nd.max.core.gamespace.GameSpaceRepository
import javax.inject.Inject

@HiltViewModel
class GameSpaceViewModel @Inject constructor(private val repository: GameSpaceRepository) : ViewModel() {
    val library = repository.state
    val profiles = GameProfileRepository.state
    init { refresh() }
    fun refresh() { viewModelScope.launch { repository.refresh(); GameProfileRepository.load() } }
    fun membership(pkg: String, include: Boolean) { viewModelScope.launch { repository.membership(pkg, include) } }
    fun favorite(pkg: String, enabled: Boolean) { viewModelScope.launch { repository.favorite(pkg, enabled) } }
}
