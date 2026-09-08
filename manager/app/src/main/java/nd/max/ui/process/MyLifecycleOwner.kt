/*
 * Minimal Lifecycle/SavedState owner used to host a Compose hierarchy inside a
 * raw WindowManager overlay (e.g. FloatingActivityService), where there is no
 * Activity/Fragment to provide one automatically.
 *
 * This was referenced (import nd.max.ui.process.MyLifecycleOwner) but never
 * defined anywhere in the project - this file fills that gap.
 */
package nd.max.ui.process

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class MyLifecycleOwner : SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun performRestore(savedState: android.os.Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
