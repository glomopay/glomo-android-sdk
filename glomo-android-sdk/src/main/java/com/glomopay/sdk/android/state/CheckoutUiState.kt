package com.glomopay.sdk.android.state

public sealed interface CheckoutUiState {
    public data object Loading : CheckoutUiState
    public data class LoadingProgress public constructor(public val progress: Int) : CheckoutUiState
    public data object Content : CheckoutUiState
}

internal fun CheckoutUiState.withLoadingProgress(progress: Int): CheckoutUiState {
    if (this is CheckoutUiState.Content) return this

    val boundedProgress = progress.coerceIn(0, 100)
    return if (boundedProgress == 100) {
        CheckoutUiState.Content
    } else {
        CheckoutUiState.LoadingProgress(boundedProgress)
    }
}
