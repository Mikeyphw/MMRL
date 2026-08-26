package com.dergoogler.mmrl.ui.remember

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.PlatformManager.moduleManager
import com.dergoogler.mmrl.platform.content.NullableBoolean

@Composable
fun <T> PlatformManager.remember(
    fallback: T,
    block: PlatformManager.() -> T,
): State<T> {
    // Read Compose-backed liveness during composition so Binder death/reconnect invalidates
    // this producer. Keep the concrete service identity as a key as well so a fast
    // false -> true reconnect still refreshes values from the newly published Binder.
    val alive = isAlive
    val service = mServiceOrNull
    return produceState(initialValue = fallback, fallback, alive, service) {
        value = if (alive) get(fallback, block) else fallback
    }
}

val versionName: String
    @Composable get() {
        val v: String by PlatformManager.remember("") {
            with(moduleManager) { version }
        }
        return v
    }

val versionCode: Int
    @Composable get() {
        val v: Int by PlatformManager.remember(-1) {
            with(moduleManager) { versionCode }
        }
        return v
    }

val isLkmMode: Boolean?
    @Composable get() {
        val v: NullableBoolean by PlatformManager.remember(NullableBoolean(null)) {
            with(moduleManager) { isLkmMode }
        }
        return v.value
    }

val seLinuxContext: String
    @Composable get() {
        val v: String by PlatformManager.remember("Failed") {
            seLinuxContext
        }
        return v
    }

val superUserCount: Int
    @Composable get() {
        val v: Int by PlatformManager.remember(-1) {
            with(moduleManager) {
                superUserCount
            }
        }
        return v
    }
