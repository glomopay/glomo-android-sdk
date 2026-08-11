package com.glomopay.sdk.android.state

import com.glomopay.sdk.android.ConnectionError

public sealed interface CheckoutUiState {
    public data object Loading : CheckoutUiState
    public data class LoadingProgress public constructor(public val progress: Int) : CheckoutUiState
    public data object Content : CheckoutUiState
    public data class Error public constructor(
        public val connectionError: ConnectionError,
    ) : CheckoutUiState
}
