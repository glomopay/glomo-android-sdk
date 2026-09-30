package com.glomopay.sdk.android.ui

/** Separate provider class avoids merging with the merchant's FileProvider. */
internal class CheckoutFileProvider : androidx.core.content.FileProvider()
