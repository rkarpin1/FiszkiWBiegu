package pl.rkarpinski.fiszkiwbiegu

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Na web zakładamy stałe połączenie — nie ma tu trybu nauki (poza zakresem MVP),
 * więc status sieci nie steruje żadną krytyczną funkcją.
 */
class WebNetworkChecker : NetworkChecker {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
