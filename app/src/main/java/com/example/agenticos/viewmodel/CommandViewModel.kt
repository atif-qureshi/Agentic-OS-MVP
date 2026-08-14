package com.example.agenticos.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.agenticos.model.DecisionResult

/**
 * MVC — State Holder (optional, for configuration-change survival)
 *
 * In this MVC architecture the Controller handles all logic.
 * The ViewModel's only job is to survive configuration changes
 * (screen rotation) by holding the last result so the View can
 * restore its state without re-running the network call.
 *
 * Flow:
 *   Controller → ViewModel.saveResult() → View observes & re-renders
 */
class CommandViewModel : ViewModel() {

    private val _lastDecision = MutableLiveData<DecisionResult?>()
    val lastDecision: LiveData<DecisionResult?> = _lastDecision

    private val _lastError = MutableLiveData<String?>()
    val lastError: LiveData<String?> = _lastError

    fun saveResult(decision: DecisionResult) {
        _lastDecision.value = decision
        _lastError.value    = null
    }

    fun saveError(message: String) {
        _lastError.value    = message
        _lastDecision.value = null
    }

    fun clear() {
        _lastDecision.value = null
        _lastError.value    = null
    }
}
