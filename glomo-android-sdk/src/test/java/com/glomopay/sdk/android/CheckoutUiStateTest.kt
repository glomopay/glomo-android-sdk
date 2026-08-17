package com.glomopay.sdk.android

import com.glomopay.sdk.android.state.CheckoutUiState
import com.glomopay.sdk.android.state.withLoadingProgress
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckoutUiStateTest {
    @Test
    fun progress_reveals_content_when_page_reaches_100_percent() {
        val state = CheckoutUiState.LoadingProgress(95).withLoadingProgress(100)

        assertEquals(CheckoutUiState.Content, state)
    }

    @Test
    fun late_progress_callback_does_not_restore_overlay_after_page_finished() {
        val state = CheckoutUiState.Content.withLoadingProgress(100)

        assertEquals(CheckoutUiState.Content, state)
    }

    @Test
    fun progress_is_bounded_while_page_is_loading() {
        val state = CheckoutUiState.Loading.withLoadingProgress(-1)

        assertEquals(CheckoutUiState.LoadingProgress(0), state)
    }
}
