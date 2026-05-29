package pl.rkarpinski.fiszkiwbiegu

import kotlinx.coroutines.flow.StateFlow

interface NetworkChecker {
    val isOnline: StateFlow<Boolean>
    fun release() {}
}
