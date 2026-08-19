package com.dergoogler.mmrl.platform

import android.app.ActivityThread
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dergoogler.mmrl.platform.PlatformManager.isAlive
import com.dergoogler.mmrl.platform.PlatformManager.isAliveDeferred
import com.dergoogler.mmrl.platform.PlatformManager.isAliveFlow
import com.dergoogler.mmrl.platform.PlatformManager.mService
import com.dergoogler.mmrl.platform.PlatformManager.proxyBy
import com.dergoogler.mmrl.platform.PlatformManager.serviceOrNull
import com.dergoogler.mmrl.platform.PlatformManager.state
import com.dergoogler.mmrl.platform.content.IService
import com.dergoogler.mmrl.platform.content.Service
import com.dergoogler.mmrl.platform.hiddenApi.HiddenPackageManager
import com.dergoogler.mmrl.platform.hiddenApi.HiddenUserManager
import com.dergoogler.mmrl.platform.model.IProvider
import com.dergoogler.mmrl.platform.stub.IFileManager
import com.dergoogler.mmrl.platform.stub.IModuleManager
import com.dergoogler.mmrl.platform.stub.IServiceManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.FileDescriptor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

/**
 * Manages the connection to the underlying platform service (`IServiceManager`).
 *
 * This object provides a centralized way to initialize, access, and interact with
 * the core platform services. It handles both synchronous and asynchronous
 * initialization and provides convenient accessors for various managers
 * (e.g., `IModuleManager`, `IFileManager`).
 *
 * Key functionalities include:
 * - **Initialization**: Offers `init` methods for both suspending (synchronous-like)
 *   and asynchronous (returning a `Deferred`) initialization of the `IServiceManager`.
 *   This allows flexibility in how the service is obtained.
 * - **Service Access**: Provides `mService` (non-null, throws if not initialized) and
 *   `mServiceOrNull` (nullable) properties to access the `IServiceManager`.
 * - **Liveness Tracking**:
 *     - `isAlive`: A `Boolean` property (backed by `mutableStateOf` for Compose UI updates)
 *       indicating if the `IServiceManager` is currently initialized and available.
 *     - `isAliveDeferred`: A `CompletableDeferred<Boolean>` that completes when the service
 *       becomes alive. Useful for waiting for initialization.
 *     - `isAliveFlow`: A `StateFlow<Boolean>` emitting the liveness state, suitable for
 *       reactive programming.
 * - **Service Retrieval from Providers**:
 *     - `get(IProvider)`: Suspends until the `IServiceManager` is obtained from the given
 *       `IProvider` via a `ServiceConnection`, with a timeout.
 *     - `from(IProvider)`: Checks provider availability and authorization before attempting
 *       to get the service using `get(IProvider)`.
 * - **Manager Accessors**: Provides direct access to sub-managers like `moduleManager`,
 *   `fileManager`, `packageManager`, and `userManager` if the `IServiceManager` is alive.
 * - **Platform Information**: Exposes `platform` type (e.g., Root, NonRoot) and `seLinuxContext`.
 * - **Utility Functions**:
 *     - `state()`: Updates and returns the current liveness state.
 *     - `get(fallback, block)`: Executes a block if alive, otherwise returns a fallback.
 */
object PlatformManager {
    const val TAG = "PlatformManager"
    const val TIMEOUT_MILLIS = 15000L
    private const val FRAMEWORK_RECONNECT_GRACE_MS = 2_000L
    private const val RECONNECT_POLL_MS = 25L

    private val initMutex = Mutex()
    private val providerBindMutex = Mutex()
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionStateLock = Any()

    @Volatile
    var preferredPlatform: Platform = Platform.Unknown
        private set

    @Volatile
    var detectedPlatform: Platform = Platform.Unknown
        private set

    @Volatile
    private var activeProvider: IProvider? = null

    @Volatile
    private var activeConnection: ServiceConnection? = null

    @Volatile
    private var activeBinder: IBinder? = null

    @Volatile
    private var activeDeathRecipient: IBinder.DeathRecipient? = null

    /** Monotonic identity for each published platform Binder generation. */
    @Volatile
    internal var serviceGeneration: Long = 0L
        private set

    @Volatile
    var mServiceOrNull: IServiceManager? = null
    val mService
        get() =
            checkNotNull(mServiceOrNull) {
                "IServiceManager has not been initialized or has been released."
            }

    /**
     * Indicates whether the [IServiceManager] is currently initialized and considered "alive".
     * This property is observable by Compose and will trigger recomposition when its value changes.
     * It is updated by the [state] function.
     *
     * @see state
     * @see isAliveFlow
     * @see isAliveDeferred
     */
    var isAlive by mutableStateOf(false)
        private set

    /**
     * A [CompletableDeferred] that completes with `true` when the [PlatformManager]
     * successfully initializes and establishes a connection with its underlying service.
     * This can be awaited to ensure the manager is ready before performing operations
     * that depend on it.
     *
     * If initialization fails or the service is not available, this deferred
     * might not complete, or it might complete exceptionally if such logic is added
     * in the future.
     */
    val isAliveDeferred = CompletableDeferred<Boolean>()

    private val _isAliveFlow = MutableStateFlow(false)

    /**
     * A [StateFlow] that emits `true` if the [IServiceManager] is initialized and alive,
     * `false` otherwise. This is useful for observing the liveness state in a reactive way.
     */
    val isAliveFlow get() = _isAliveFlow.asStateFlow()

    /**
     * Initializes the [PlatformManager] synchronously with the provided [IServiceManager] instance.
     * This function attempts to set the internal service manager instance (`mServiceOrNull`)
     * using the result of the [provider] lambda.
     *
     * If the [PlatformManager] is already alive (i.e., `isAlive` is true), this function
     * returns `true` immediately without re-initializing.
     *
     * The [provider] lambda is executed within the context of [PlatformManager], allowing
     * access to its members. It should return an instance of [IServiceManager] or `null`.
     *
     * After attempting to initialize, it calls [state] to update the alive status
     * of the [PlatformManager].
     *
     * Catches any [Exception] during the provider execution, sets `mServiceOrNull` to `null`,
     * logs the error, and then updates the state.
     *
     * @param provider A suspendable lambda function that, when invoked, returns an instance
     *                 of [IServiceManager] or `null`. This lambda is executed to obtain the
     *                 service manager.
     * @return `true` if the initialization was successful (i.e., `mServiceOrNull` is not null after
     *         the provider execution) or if the manager was already alive. Returns `false` if
     *         the provider returns `null` or if an exception occurs during initialization.
     */
    /** Selects the requested working mode without treating it as detected root evidence. */
    fun selectPreferred(platform: Platform) {
        preferredPlatform = platform
    }

    /** Explicitly release the active provider/binder before changing working modes. */
    suspend fun release() {
        initMutex.withLock { clearServiceState(unbind = true) }
    }

    /**
     * Serializes provider initialization so callers cannot race two root bindings into state.
     * Readiness requires a live binder and a root platform independently detected by the root service.
     */
    suspend fun init(provider: suspend PlatformManager.() -> IServiceManager?): Boolean =
        initMutex.withLock {
            if (state()) return@withLock true
            try {
                Log.d(TAG, "Starting serialized initialization for preferred=$preferredPlatform")
                val candidate = provider()
                val detected = if (candidate != null) {
                    runCatching { Platform.from(candidate.currentPlatform()) }
                        .getOrElse {
                            Log.e(TAG, "Unable to query detected root platform", it)
                            Platform.Unknown
                        }
                } else {
                    Platform.Unknown
                }
                synchronized(connectionStateLock) {
                    mServiceOrNull = candidate
                    detectedPlatform = detected
                    if (candidate != null && activeBinder == null) {
                        activeBinder = candidate.asBinder()
                        serviceGeneration += 1L
                    }
                }
                state()
            } catch (error: Exception) {
                Log.e(TAG, "Failed to initialize service manager", error)
                clearServiceState()
                false
            }
        }

    fun init(
        scope: CoroutineScope,
        provider: suspend PlatformManager.() -> IServiceManager?,
    ): Deferred<Boolean> = scope.async(Dispatchers.IO) { init(provider) }

    private fun activeServiceFor(provider: IProvider): IServiceManager? {
        val snapshot = synchronized(connectionStateLock) {
            if (activeProvider !== provider) return@synchronized null
            Triple(mServiceOrNull, activeBinder, activeConnection)
        } ?: return null
        val binder = snapshot.second ?: return null
        if (!binder.isBinderAlive || !binder.pingBinder()) return null
        return snapshot.first ?: IServiceManager.Stub.asInterface(binder)
    }

    private fun hasTrackedBinding(provider: IProvider): Boolean =
        synchronized(connectionStateLock) { activeProvider === provider && activeConnection != null }

    private fun isTrackedConnection(provider: IProvider, connection: ServiceConnection): Boolean =
        synchronized(connectionStateLock) { activeProvider === provider && activeConnection === connection }

    private suspend fun awaitFrameworkReconnect(provider: IProvider): IServiceManager? =
        withTimeoutOrNull(FRAMEWORK_RECONNECT_GRACE_MS) {
            while (hasTrackedBinding(provider)) {
                activeServiceFor(provider)?.let { return@withTimeoutOrNull it }
                delay(RECONNECT_POLL_MS)
            }
            null
        }

    /**
     * Asynchronously retrieves an [IServiceManager] instance from the given [provider].
     *
     * This function attempts to bind to the service provided by the [IProvider].
     * It uses a [suspendCancellableCoroutine] to bridge the callback-based service connection
     * with coroutine-based asynchronous programming.
     *
     * The binding process is subject to a [timeoutMillis]. If the connection is not established
     * within this timeout, or if any other error occurs during binding (e.g., service disconnected,
     * binding died), the coroutine will resume with an exception.
     *
     * If the coroutine is cancelled while waiting for the service to connect, it will attempt
     * to unbind from the provider.
     *
     * @param provider The [IProvider] implementation that will be used to bind to the service.
     * @param timeoutMillis The maximum time in milliseconds to wait for the service to connect.
     *                      Defaults to [TIMEOUT_MILLIS].
     * @return An instance of [IServiceManager] if the connection is successful.
     * @throws TimeoutCancellationException if the binding process times out.
     * @throws IllegalStateException if the service disconnects or the binding dies.
     * @throws Exception for other errors that might occur during the binding process.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun get(
        provider: IProvider,
        timeoutMillis: Long = TIMEOUT_MILLIS,
    ): IServiceManager = providerBindMutex.withLock {
        withTimeout(timeoutMillis) {
            activeServiceFor(provider)?.let { return@withTimeout it }
            when (BinderLifecyclePolicy.acquireAction(false, hasTrackedBinding(provider))) {
                BinderLifecyclePolicy.AcquireAction.REUSE_LIVE ->
                    activeServiceFor(provider)?.let { return@withTimeout it }
                BinderLifecyclePolicy.AcquireAction.WAIT_FOR_TRACKED_RECONNECT -> {
                    awaitFrameworkReconnect(provider)?.let { return@withTimeout it }
                    clearServiceState(unbind = true)
                }
                BinderLifecyclePolicy.AcquireAction.BIND_NEW -> Unit
            }

            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val cancelled = AtomicBoolean(false)
                    val initialDelivered = AtomicBoolean(false)
                    val bindingReleased = AtomicBoolean(false)
                    val binderRef = AtomicReference<IBinder?>(null)
                    val candidateRef = AtomicReference<BinderCandidateGate?>(null)
                    lateinit var connection: ServiceConnection

                    fun unbindOnce() {
                        if (bindingReleased.compareAndSet(false, true)) {
                            runCatching { provider.unbind(connection) }
                        }
                    }

                    fun failInitial(message: String, cause: Throwable? = null) {
                        unbindOnce()
                        if (!continuation.isActive) return
                        val error = cause ?: IllegalStateException(message)
                        continuation.resumeWithException(error)
                    }

                    connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            if (bindingReleased.get()) {
                                runCatching { provider.unbind(this) }
                                return
                            }
                            Log.d(TAG, "Service connected: $name")

                            val candidate = BinderCandidateGate()
                            candidateRef.set(candidate)
                            binderRef.set(binder)
                            val service = IServiceManager.Stub.asInterface(binder)

                            lateinit var deathRecipient: IBinder.DeathRecipient
                            deathRecipient = IBinder.DeathRecipient {
                                candidate.lose {
                                    if (handleServiceLoss(
                                            provider = provider,
                                            connection = this,
                                            binder = binder,
                                            reason = BinderLifecyclePolicy.LossReason.BINDER_DIED,
                                        )
                                    ) {
                                        bindingReleased.set(true)
                                    }
                                }
                            }

                            try {
                                binder.linkToDeath(deathRecipient, 0)
                            } catch (error: RemoteException) {
                                failInitial("${provider.name} Binder was already dead", error)
                                return
                            }

                            val detected = runCatching { Platform.from(service.currentPlatform()) }
                                .getOrElse { Platform.Unknown }
                            val initial = !initialDelivered.get()
                            val published = candidate.publish(
                                eligible = {
                                    !bindingReleased.get() &&
                                        BinderLifecyclePolicy.canPublish(initial, isTrackedConnection(provider, this)) &&
                                        (!initial || (!cancelled.get() && continuation.isActive)) &&
                                        detected.isPrivilegedRoot &&
                                        binder.isBinderAlive && binder.pingBinder()
                                },
                            ) {
                                synchronized(connectionStateLock) {
                                    activeProvider = provider
                                    activeConnection = this
                                    activeBinder = binder
                                    activeDeathRecipient = deathRecipient
                                    serviceGeneration += 1L
                                    detectedPlatform = detected
                                }
                            }

                            if (!published) {
                                runCatching { binder.unlinkToDeath(deathRecipient, 0) }
                                if (initial) {
                                    failInitial("${provider.name} did not provide a live, detected root backend")
                                }
                                return
                            }

                            if (initial) {
                                initialDelivered.set(true)
                                continuation.resume(service) { _ ->
                                    cancelled.set(true)
                                    candidate.cancel {
                                        bindingReleased.set(true)
                                        if (!handleServiceLoss(
                                                provider = provider,
                                                connection = this,
                                                binder = binder,
                                                reason = BinderLifecyclePolicy.LossReason.CANCELLED,
                                            )
                                        ) {
                                            runCatching { provider.unbind(this) }
                                        }
                                    }
                                }
                            } else {
                                // The same ServiceConnection can reconnect with a new Binder after
                                // onServiceDisconnected. Adopt only this newly published generation.
                                val reconnectedConnection = this
                                reconnectScope.launch {
                                    initMutex.withLock {
                                        if (isActiveGeneration(reconnectedConnection, binder)) {
                                            mServiceOrNull = service
                                            detectedPlatform = detected
                                            state()
                                        }
                                    }
                                }
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            Log.w(TAG, "Service disconnected: $name")
                            val binder = binderRef.getAndSet(null)
                            if (!initialDelivered.get()) {
                                failInitial("IServiceManager ($name) disconnected before initial connection")
                                return
                            }
                            if (binder != null) {
                                handleServiceLoss(
                                    provider = provider,
                                    connection = this,
                                    binder = binder,
                                    reason = BinderLifecyclePolicy.LossReason.DISCONNECTED,
                                )
                            }
                        }

                        override fun onBindingDied(name: ComponentName?) {
                            Log.e(TAG, "Binding died for service: $name")
                            bindingReleased.set(true)
                            if (!handleServiceLoss(
                                    provider = provider,
                                    connection = this,
                                    binder = null,
                                    reason = BinderLifecyclePolicy.LossReason.BINDING_DIED,
                                )
                            ) {
                                runCatching { provider.unbind(this) }
                            }
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("IServiceManager ($name) binding died"))
                            }
                        }

                        override fun onNullBinding(name: ComponentName?) {
                            Log.e(TAG, "Null root binding from: $name")
                            unbindOnce()
                            handleServiceLoss(
                                provider = provider,
                                connection = this,
                                binder = null,
                                reason = BinderLifecyclePolicy.LossReason.NULL_BINDING,
                            )
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("IServiceManager ($name) returned null binding"))
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        cancelled.set(true)
                        val binder = binderRef.get()
                        val candidate = candidateRef.get()
                        val cleaned = candidate?.cancel {
                            bindingReleased.set(true)
                            if (!handleServiceLoss(
                                    provider = provider,
                                    connection = connection,
                                    binder = binder,
                                    reason = BinderLifecyclePolicy.LossReason.CANCELLED,
                                )
                            ) {
                                runCatching { provider.unbind(connection) }
                            }
                        } == true
                        if (!cleaned) unbindOnce()
                    }

                    runCatching { provider.bind(connection) }
                        .onFailure { error -> failInitial("Unable to bind ${provider.name}", error) }
                }
            }
        }
    }

    private fun isActiveGeneration(connection: ServiceConnection, binder: IBinder): Boolean =
        synchronized(connectionStateLock) {
            activeConnection === connection && activeBinder === binder
        }

    private data class DetachedBinding(
        val provider: IProvider?,
        val connection: ServiceConnection?,
        val binder: IBinder?,
        val deathRecipient: IBinder.DeathRecipient?,
    )

    /** Detach only the Binder generation observed by the callback. */
    private fun detachBinding(
        expectedConnection: ServiceConnection? = null,
        expectedBinder: IBinder? = null,
        releaseBinding: Boolean,
    ): DetachedBinding? = synchronized(connectionStateLock) {
        if (expectedConnection != null && activeConnection !== expectedConnection) return@synchronized null
        if (expectedBinder != null && activeBinder !== expectedBinder) return@synchronized null

        val snapshot = DetachedBinding(activeProvider, activeConnection, activeBinder, activeDeathRecipient)
        mServiceOrNull = null
        activeBinder = null
        activeDeathRecipient = null
        detectedPlatform = Platform.Unknown
        if (releaseBinding) {
            activeProvider = null
            activeConnection = null
        }
        snapshot
    }

    private fun handleServiceLoss(
        provider: IProvider,
        connection: ServiceConnection,
        binder: IBinder?,
        reason: BinderLifecyclePolicy.LossReason,
    ): Boolean {
        val releaseBinding = BinderLifecyclePolicy.shouldReleaseBinding(reason)
        val shouldRebind = BinderLifecyclePolicy.shouldRebind(reason)
        // Detach synchronously so a cancellation/death callback cannot return while the dead
        // generation is still globally visible. Slow unbind/rebind work remains off-callback.
        val detached = detachBinding(
            expectedConnection = connection,
            expectedBinder = binder,
            releaseBinding = releaseBinding,
        ) ?: return false

        reconnectScope.launch {
            detached.binder?.let { oldBinder ->
                detached.deathRecipient?.let { recipient ->
                    runCatching { oldBinder.unlinkToDeath(recipient, 0) }
                }
            }
            if (releaseBinding && detached.connection != null) {
                withContext(Dispatchers.Main.immediate) {
                    runCatching { (detached.provider ?: provider).unbind(detached.connection) }
                }
            }
            publishAlive(false)

            if (shouldRebind && preferredPlatform.isPrivilegedRoot) {
                runCatching { init { from(provider) } }
                    .onFailure { Log.e(TAG, "Automatic root-service rebind failed", it) }
            }
        }
        return true
    }

    private suspend fun clearServiceState(unbind: Boolean = true) {
        val detached = detachBinding(releaseBinding = unbind)
        detached?.binder?.let { binder ->
            detached.deathRecipient?.let { recipient ->
                runCatching { binder.unlinkToDeath(recipient, 0) }
            }
        }
        if (unbind && detached?.provider != null && detached.connection != null) {
            withContext(Dispatchers.Main.immediate) {
                runCatching { detached.provider.unbind(detached.connection) }
            }
        }
        if (detached == null) {
            synchronized(connectionStateLock) {
                mServiceOrNull = null
                detectedPlatform = Platform.Unknown
            }
        }
        publishAlive(false)
    }

    /**
     * Attempts to retrieve an [IServiceManager] from the given [provider].
     *
     * This function checks if the provider is available and authorized before attempting to connect.
     * It operates on the [Dispatchers.Main] context.
     *
     * @param provider The [IProvider] to get the service from.
     * @param timeoutMillis The maximum time in milliseconds to wait for the service connection.
     *                      Defaults to [TIMEOUT_MILLIS].
     * @return The connected [IServiceManager].
     * @throws IllegalStateException if the provider is not available, not authorized,
     * or if the connection times out or fails for other reasons.
     */
    @Throws(IllegalStateException::class)
    suspend fun from(
        provider: IProvider,
        timeoutMillis: Long = TIMEOUT_MILLIS,
    ): IServiceManager {
        if (!provider.isAvailable()) throw IllegalStateException("${provider.name} not available")
        if (!provider.isAuthorized()) throw IllegalStateException("${provider.name} not authorized for root")
        return get(provider, timeoutMillis)
    }

    /**
     * Provides access to the module management functionalities.
     *
     * This property is a delegate to the `moduleManager` property of the underlying [IServiceManager].
     * It allows interaction with modules, such as listing, enabling, or disabling them.
     *
     * @throws IllegalStateException if [IServiceManager] has not been initialized or has been released.
     * @see IModuleManager
     * @see mService
     */
    val moduleManager: IModuleManager get() = mService.moduleManager

    /**
     * Provides access to the file management functionalities.
     * This property delegates to the `fileManager` of the underlying `IServiceManager`.
     *
     * @return An instance of [IFileManager] for interacting with the file system.
     * @throws IllegalStateException if `IServiceManager` has not been initialized.
     */
    val fileManager: IFileManager get() = mService.fileManager

    internal val fileManagerOrNull: IFileManager? get() = mServiceOrNull?.fileManager

    /**
     * Provides access to a [HiddenPackageManager] instance, which allows interaction
     * with package management functionalities that might otherwise be restricted
     * by Android's hidden API policies.
     *
     * This property leverages the underlying [mService] (an [IServiceManager])
     * to facilitate these operations.
     *
     * @throws IllegalStateException if the [mService] has not been initialized.
     * @return A [HiddenPackageManager] instance.
     */
    val packageManager: HiddenPackageManager get() = HiddenPackageManager(this.mService)

    /**
     * Provides access to user-related operations through a [HiddenUserManager].
     * This manager allows interaction with user management functionalities that
     * might be otherwise restricted by standard Android APIs.
     *
     * It requires the [mService] to be initialized and available.
     *
     * @return An instance of [HiddenUserManager] for interacting with user services.
     * @throws IllegalStateException if [mService] has not been initialized.
     */
    val userManager: HiddenUserManager get() = HiddenUserManager(this.mService)

    /**
     * Retrieves the SELinux context of the remote service.
     * This can be useful for diagnostics and security-related checks.
     *
     * @return The SELinux context string if the service is alive and provides it,
     * otherwise behavior might depend on the `mService` implementation (e.g., throw exception).
     * @throws IllegalStateException if `mService` is null (i.e., [isAlive] is false).
     */
    val seLinuxContext: String get() = mService.seLinuxContext

    /**
     * Checks if SELinux is enabled on the system.
     *
     * This property queries the underlying `IServiceManager` to determine the SELinux status.
     *
     * @return `true` if SELinux is enabled, `false` if it is disabled, or `null` if the
     *         status cannot be determined (e.g., if the service is not available or the
     *         method is not implemented by the service).
     * @throws IllegalStateException if `mService` is null (i.e., [isAlive] is false),
     *         depending on the `mService` implementation.
     */
    val isSELinuxEnabled: Boolean get() = mService.isSELinuxEnabled()

    /**
     * Indicates whether SELinux is currently in enforcing mode on the device,
     * as reported by the underlying service.
     *
     * This property delegates to the `isSELinuxEnforced()` method of the [mService].
     *
     * @return `true` if SELinux is enforced, `false` if it's permissive or disabled,
     *         or `null` if the state cannot be determined (e.g., service not connected
     *         or an error occurs during the call).
     * @throws IllegalStateException if `mService` is null (i.e., [isAlive] is false),
     *         unless the underlying `mService.isSELinuxEnforced()` implementation
     *         handles this gracefully (which is not guaranteed by the interface).
     */
    val isSELinuxEnforced: Boolean get() = mService.isSELinuxEnforced()

    /**
     * Gets the current platform information.
     * This property attempts to retrieve the platform details from the underlying service.
     * If the service is not available or an error occurs during the retrieval,
     * it defaults to [Platform.Unknown].
     *
     * @return The current [Platform], or [Platform.Unknown] if an error occurs or the service is unavailable.
     */
    val platform: Platform get() = detectedPlatform

    val type get() = platform.type

    /**
     * Updates the internal state of the PlatformManager based on whether the service manager (`mServiceOrNull`) is initialized.
     * This function should be called after any operation that might change the service manager's status (e.g., initialization, release).
     *
     * - Sets the `_isAliveFlow` value, which is a StateFlow that external components can observe.
     * - Updates the `isAlive` mutable state, which is primarily used for Compose UI updates.
     * - Completes the `isAliveDeferred` if it's not already completed and the service is alive. This is useful for one-time await operations.
     *
     * @return `true` if the service manager is initialized (alive), `false` otherwise.
     */
    suspend fun state(): Boolean {
        val service = mServiceOrNull
        val binderAlive = service?.asBinder()?.let { it.isBinderAlive && it.pingBinder() } == true
        val aliveStatus = PlatformReadinessPolicy.isReady(
            authorized = service != null,
            binderAlive = binderAlive,
            detected = detectedPlatform,
        )
        if (!aliveStatus && service != null) {
            clearServiceState(unbind = true)
            return false
        }
        publishAlive(aliveStatus)
        return aliveStatus
    }

    private suspend fun publishAlive(aliveStatus: Boolean) {
        _isAliveFlow.value = aliveStatus
        withContext(Dispatchers.Main.immediate) { isAlive = aliveStatus }
        if (aliveStatus && !isAliveDeferred.isCompleted) isAliveDeferred.complete(true)
        Log.d(TAG, "State updated. isAlive=$aliveStatus detected=$detectedPlatform preferred=$preferredPlatform")
    }

    /**
     * Executes a block of code with the PlatformManager as its receiver if the service is alive,
     * otherwise returns a fallback value.
     *
     * This function provides a safe way to interact with PlatformManager features that
     * depend on the underlying service being active. If `isAlive` is true, the `block`
     * is executed in the context of `PlatformManager` (i.e., `this` inside the block
     * refers to `PlatformManager`), and its result is returned. If `isAlive` is false,
     * the `fallback` value is returned directly without executing the block.
     *
     * @param T The type of the value to be returned.
     * @param fallback The value to return if the PlatformManager's service is not alive.
     * @param block A lambda function that takes `PlatformManager` as its receiver and returns a value of type `T`.
     *              This block will only be executed if the service is alive.
     * @return The result of the `block` if the service is alive, or the `fallback` value otherwise.
     */
    fun <T> get(
        fallback: T,
        block: PlatformManager.() -> T,
    ): T = if (isAlive) block(this) else fallback

    /**
     * Asynchronously executes a block of code if the [PlatformManager] is alive.
     *
     * This function launches a new coroutine in the provided [scope] using [Dispatchers.IO].
     * If [PlatformManager.isAlive] is true, the [block] is executed.
     * If the [block] throws an exception, it is caught, logged, and the [fallback] value is returned.
     * If [PlatformManager.isAlive] is false, the [fallback] value is returned directly.
     *
     * This function is useful for performing operations that depend on the [PlatformManager]
     * being initialized and available, without blocking the calling thread. The
     * `@DisallowComposableCalls` annotation ensures this function is not called from
     * a Composable context where suspension might not be handled correctly.
     *
     * @param T The type of the value returned by the [block] and the [fallback].
     * @param scope The [CoroutineScope] in which to launch the asynchronous operation.
     * @param fallback The value to return if the [PlatformManager] is not alive or if the [block] throws an exception.
     * @param block A suspendable lambda function that will be executed if the [PlatformManager] is alive.
     *              It receives the [PlatformManager] instance as its receiver.
     * @return A [Deferred] representing the future result of the asynchronous operation.
     *         The deferred will complete with the result of the [block] or the [fallback] value.
     */
    inline fun <T> getAsyncDeferred(
        scope: CoroutineScope,
        fallback: T,
        crossinline block: @DisallowComposableCalls suspend PlatformManager.() -> T,
    ): Deferred<T> =
        scope.async(Dispatchers.IO) {
            if (isAlive) {
                try {
                    block()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in getAsyncDeferred block execution", e)
                    fallback
                }
            } else {
                fallback
            }
        }

    /**
     * Sets Hidden API exemptions using [HiddenApiBypass.addHiddenApiExemptions].
     *
     * This function attempts to bypass Android's restrictions on accessing non-SDK interfaces (hidden APIs)
     * by adding the specified signature prefixes to an exemption list. This is primarily useful on
     * Android P (API level 28) and above, where these restrictions are enforced more strictly.
     *
     * On SDK versions below P, this function does nothing and returns `true` as exemptions are not needed.
     *
     * @param signaturePrefixes A vararg array of strings, where each string is a prefix of a hidden API
     *                          signature to be exempted. For example, "Landroid/app/ActivityThread;"
     *                          must exactly match one of MMRL's approved framework descriptors.
     *                          Empty, broad, and unknown prefixes are rejected before calling the
     *                          hidden-API bypass library.
     * @return `true` if the exemptions were successfully added (or not needed for the current SDK version),
     *         `false` otherwise (e.g., if `HiddenApiBypass.addHiddenApiExemptions` returns `false`).
     * @see HiddenApiBypass.addHiddenApiExemptions
     */
    fun setHiddenApiExemptions(vararg signaturePrefixes: String = HiddenApiPolicy.DEFAULT_PREFIXES): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!HiddenApiPolicy.areNarrow(signaturePrefixes.asList())) {
                Log.e(TAG, "Refusing non-allowlisted hidden-API exemption prefix")
                return false
            }
            Log.d(
                TAG,
                "Setting Hidden API exemptions with prefixes: ${signaturePrefixes.joinToString()}",
            )
            runCatching { HiddenApiBypass.addHiddenApiExemptions(*signaturePrefixes) }
                .onFailure { Log.e(TAG, "Hidden API exemption setup failed", it) }
                .getOrDefault(false)
        } else {
            Log.d(TAG, "Hidden API exemptions not needed on SDK < P.")
            true
        }

    fun <T : IBinder> T.proxyBy(service: IServiceManager): IBinder =
        object : IBinder {
            private val originalBinder: IBinder = this@proxyBy
            private val serviceBinder: IBinder = service.asBinder()

            override fun getInterfaceDescriptor(): String? = originalBinder.interfaceDescriptor

            override fun pingBinder(): Boolean = originalBinder.pingBinder() && serviceBinder.pingBinder()

            override fun isBinderAlive(): Boolean = originalBinder.isBinderAlive && serviceBinder.isBinderAlive

            override fun queryLocalInterface(descriptor: String): IInterface? = null

            override fun dump(
                fd: FileDescriptor,
                args: Array<out String>?,
            ) = originalBinder.dump(fd, args)

            override fun dumpAsync(
                fd: FileDescriptor,
                args: Array<out String>?,
            ) = originalBinder.dumpAsync(fd, args)

            override fun linkToDeath(
                recipient: IBinder.DeathRecipient,
                flags: Int,
            ) {
                originalBinder.linkToDeath(recipient, flags)
                try {
                    serviceBinder.linkToDeath(recipient, flags)
                } catch (error: RemoteException) {
                    runCatching { originalBinder.unlinkToDeath(recipient, flags) }
                    throw error
                }
            }

            override fun unlinkToDeath(
                recipient: IBinder.DeathRecipient,
                flags: Int,
            ): Boolean {
                val original = runCatching { originalBinder.unlinkToDeath(recipient, flags) }.getOrDefault(false)
                val service = runCatching { serviceBinder.unlinkToDeath(recipient, flags) }.getOrDefault(false)
                return original || service
            }

            override fun transact(
                code: Int,
                data: Parcel,
                reply: Parcel?,
                flags: Int,
            ): Boolean {
                if (!serviceBinder.isBinderAlive || !serviceBinder.pingBinder()) {
                    Log.e(TAG, "Proxy transact: ServiceManager is dead.")
                    return false
                }
                val newData = Parcel.obtain()
                var result = false
                try {
                    newData.apply {
                        writeInterfaceToken(IServiceManager.DESCRIPTOR)
                        writeStrongBinder(originalBinder)
                        writeInt(code)
                        writeInt(flags)
                        if (data.dataSize() > 0) {
                            appendFrom(data, 0, data.dataSize())
                        }
                    }

                    result = serviceBinder.transact(BINDER_TRANSACTION, newData, reply, 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during proxy transact", e)
                    throw e
                } finally {
                    newData.recycle()
                }
                return result
            }
        }

    /**
     * Adds a service defined by its class to the PlatformManager.
     *
     * This function attempts to add the specified service to the underlying `IServiceManager`.
     * If the `IServiceManager` is not initialized (i.e., `mServiceOrNull` is null),
     * this function will return `null` without attempting to add the service.
     *
     * @param T The type of the service, which must extend [IService].
     * @param clazz The [Class] object representing the service to be added.
     * @return An [IBinder] representing the added service if successful and the PlatformManager is initialized,
     *         otherwise `null`.
     * @see serviceOrNull
     * @see Service
     */
    fun <T : IService> addService(clazz: Class<T>): IBinder? =
        serviceOrNull {
            addService(Service<T>(clazz.name))
        }

    /**
     * Adds a service to the IServiceManager.
     *
     * This function attempts to add the provided service to the IServiceManager.
     * If the IServiceManager is not initialized (mServiceOrNull is null), this function
     * will return null. Otherwise, it delegates the call to the IServiceManager's
     * addService method.
     *
     * @param T The type of the service, which must extend IService.
     * @param service The Service object containing the service to be added.
     * @return An IBinder representing the added service if successful, or null otherwise
     *         (e.g., if the IServiceManager is not initialized or the addition fails).
     * @see IServiceManager.addService
     * @see serviceOrNull
     */
    fun <T : IService> addService(service: Service<T>): IBinder? =
        serviceOrNull {
            addService(service)
        }

    fun <T : IInterface> addService(
        name: String,
        service: T,
    ) {
        serviceOrNull {
            addServiceBinder(name, service.asBinder())
        }
    }

    /**
     * Extension function for `Class<T>` where `T` is an `IService`.
     * Adds an instance of this class as a service through the PlatformManager's IServiceManager.
     *
     * This is a convenience function that wraps the class in a `Service` object before adding it.
     *
     * @receiver The class of the service to be added.
     * @return An `IBinder` representing the added service if the `IServiceManager` is available and the operation succeeds, otherwise `null`.
     * @see PlatformManager.addService
     * @see Service
     */
    fun <T : IService> Class<T>.addAsService(): IBinder? =
        serviceOrNull {
            addService(Service<T>(this@addAsService.name))
        }

    /**
     * Retrieves a system service by its name.
     *
     * This function attempts to get the IBinder for a system service identified by the `name` parameter.
     * It relies on the underlying `IServiceManager` to perform the lookup.
     *
     * @param name The name of the system service to retrieve (e.g., "activity", "package").
     * @return The IBinder interface for the service if found and the `IServiceManager` is available,
     *         otherwise `null`.
     */
    fun getService(name: String): IBinder? =
        serviceOrNull {
            getService(name)
        }

    fun <T : IInterface> T.proxyBy(service: IServiceManager): IBinder = this.asBinder().proxyBy(service)

    /**
     * Retrieves a system service by its name and wraps it in a proxy.
     *
     * This function uses the `android.os.ServiceManager` to get the raw `IBinder` for the requested system service.
     * If the service is found (i.e., `systemServiceBinder` is not null), it then proxies this binder
     * through the `PlatformManager`'s `IServiceManager` instance (`this@PlatformManager.mService`).
     * This proxying mechanism is typically used to route binder calls through a central service manager,
     * potentially for security, logging, or other cross-cutting concerns.
     *
     * @param T The type of the `IInterface` that this function is an extension for.
     *          While the receiver `T` is not directly used to query the service, it provides the context
     *          for this extension function.
     * @param name The string name of the system service to retrieve (e.g., "activity", "package").
     * @return The proxied `IBinder` for the requested system service if found and the `PlatformManager`'s
     *         service is available, otherwise `null`.
     * @see android.os.ServiceManager.getService
     * @see proxyBy
     */
    fun <T : IInterface> T.getSystemService(name: String): IBinder? {
        val systemServiceBinder = android.os.ServiceManager.getService(name)
        return systemServiceBinder?.proxyBy(this@PlatformManager.mService)
    }

    fun <T> serviceOrNull(
        default: T,
        block: IServiceManager.() -> T,
    ): T = mServiceOrNull?.let { block(it) } ?: default

    fun <T> serviceOrNull(block: IServiceManager.() -> T): T? = mServiceOrNull?.let { block(it) }

    val context: Context
        @Throws(IllegalStateException::class)
        get() {
            val currentApp =
                ActivityThread.currentApplication()
                    ?: throw IllegalStateException("Application is not initialized yet.")
            var ctx: Context = currentApp
            while (ctx is ContextWrapper) {
                ctx = ctx.baseContext ?: return ctx
            }
            return ctx
        }
}
