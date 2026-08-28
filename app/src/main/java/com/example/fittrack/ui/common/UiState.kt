package com.example.fittrack.ui.common

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    /** Loaded successfully, but there is nothing to show. */
    object Empty : UiState<Nothing>()
    data class Error(val message: String) : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
}
