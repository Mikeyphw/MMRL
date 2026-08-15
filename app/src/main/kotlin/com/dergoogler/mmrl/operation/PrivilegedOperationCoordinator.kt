package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped owner for privileged mutations. Callers may detach/recreate without cancelling the mutation.
 * A single history id/idempotency key maps to a single live execution. Cancellation after mutation begins is
 * never reported as a clean cancel; the record becomes OUTCOME_UNKNOWN and must be reconciled before retry.
 */
@Singleton
class PrivilegedOperationCoordinator @Inject constructor(
    private val history: OperationHistoryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executions = ConcurrentHashMap<String, Deferred<OperationCompletion<*>>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> execute(
        historyId: String,
        timeoutMs: Long = DEFAULT_OPERATION_TIMEOUT_MS,
        cleanup: suspend () -> Unit = {},
        block: suspend ExecutionContext.() -> OperationCompletion<T>,
    ): OperationCompletion<T> {
        val candidate = scope.async(start = CoroutineStart.LAZY) {
            try {
                runExecution(historyId, timeoutMs, block) as OperationCompletion<*>
            } finally {
                try {
                    cleanup()
                } catch (error: Throwable) {
                    history.appendLog(historyId, "Operation cleanup failed: ${error.message ?: error::class.java.simpleName}")
                }
            }
        }
        val active = executions.putIfAbsent(historyId, candidate) ?: candidate.also { it.start() }
        if (active !== candidate) candidate.cancel()
        return try {
            active.await() as OperationCompletion<T>
        } finally {
            if (active.isCompleted) executions.remove(historyId, active)
        }
    }

    suspend fun requestCancellation(historyId: String): Boolean {
        val accepted = history.requestCancel(historyId)
        if (!accepted) return false

        val execution = executions[historyId]
        if (execution != null) {
            execution.cancel(CancellationException("Cancellation requested"))
            runCatching { execution.await() }
        }
        settleInterruptedCancellation(historyId)
        return true
    }

    private suspend fun settleInterruptedCancellation(historyId: String) {
        val entry = history.getById(historyId) ?: return
        if (!entry.isRunning) return
        if (entry.mutationStartedAt == null) {
            history.cancel(historyId, "Cancelled before privileged mutation began")
        } else {
            history.outcomeUnknown(
                id = historyId,
                summary = "Cancellation interrupted a privileged mutation; reconcile before retrying",
                requiresReboot = entry.requiresReboot,
                rollbackAction = entry.rollbackAction?.let { raw ->
                    runCatching { OperationAction.valueOf(raw) }.getOrNull()
                },
            )
        }
    }

    fun isActive(historyId: String): Boolean = executions[historyId]?.isActive == true

    private suspend fun <T> runExecution(
        historyId: String,
        timeoutMs: Long,
        block: suspend ExecutionContext.() -> OperationCompletion<T>,
    ): OperationCompletion<T> {
        val entered = history.transition(
            id = historyId,
            to = OperationStatus.RUNNING,
            from = setOf(OperationStatus.QUEUED, OperationStatus.RUNNING),
            phase = OperationPhase.STAGE,
        )
        if (!entered) {
            val current = history.getById(historyId)
            return OperationCompletion.Failure(
                current?.summary ?: "Operation is no longer eligible for privileged execution",
            )
        }
        val context = ExecutionContext(historyId, history)
        val completion = try {
            withTimeout(timeoutMs) { context.block() }
        } catch (error: TimeoutCancellationException) {
            if (context.mutationStarted) {
                OperationCompletion.OutcomeUnknown("Timed out after privileged mutation began; reconcile before retrying", error)
            } else {
                OperationCompletion.Cancelled("Timed out before privileged mutation began")
            }
        } catch (error: CancellationException) {
            if (context.mutationStarted) {
                OperationCompletion.OutcomeUnknown("Cancellation interrupted a privileged mutation; reconcile before retrying", error)
            } else {
                OperationCompletion.Cancelled("Cancelled before privileged mutation began")
            }
        } catch (error: Throwable) {
            if (context.mutationStarted) {
                OperationCompletion.OutcomeUnknown(error.message ?: "Privileged outcome is unknown; reconcile before retrying", error)
            } else {
                OperationCompletion.Failure(error.message ?: "Privileged operation failed", error)
            }
        }

        withContext(NonCancellable) {
            when (completion) {
                is OperationCompletion.Success -> history.succeed(
                    id = historyId,
                    summary = completion.summary,
                    requiresReboot = completion.requiresReboot,
                    rollbackAction = completion.rollbackAction,
                )
                is OperationCompletion.Failure -> history.fail(
                    id = historyId,
                    summary = completion.summary,
                    error = completion.error,
                    requiresReboot = completion.requiresReboot,
                    rollbackAction = completion.rollbackAction,
                    retryable = completion.retryable,
                )
                is OperationCompletion.Cancelled -> history.cancel(historyId, completion.summary)
                is OperationCompletion.OutcomeUnknown -> history.outcomeUnknown(
                    id = historyId,
                    summary = completion.summary,
                    error = completion.error,
                    requiresReboot = completion.requiresReboot,
                    rollbackAction = completion.rollbackAction,
                )
            }
        }
        return completion
    }

    class ExecutionContext internal constructor(
        val historyId: String,
        private val history: OperationHistoryRepository,
    ) {
        @Volatile internal var mutationStarted: Boolean = false
            private set

        suspend fun markMutationStarted() {
            check(history.markMutationStarted(historyId)) { "Operation is no longer eligible to mutate" }
            mutationStarted = true
        }

        suspend fun phase(phase: OperationPhase, summary: String) = history.phase(historyId, phase, summary)
        suspend fun log(line: String) = history.appendLog(historyId, line)
        suspend fun progress(value: Float) = history.progress(historyId, value)
    }

    sealed interface OperationCompletion<out T> {
        data class Success<T>(
            val value: T,
            val summary: String,
            val requiresReboot: Boolean = false,
            val rollbackAction: OperationAction? = null,
        ) : OperationCompletion<T>

        data class Failure(
            val summary: String,
            val error: Throwable? = null,
            val requiresReboot: Boolean = false,
            val rollbackAction: OperationAction? = null,
            val retryable: Boolean = false,
        ) : OperationCompletion<Nothing>

        data class Cancelled(val summary: String) : OperationCompletion<Nothing>
        data class OutcomeUnknown(
            val summary: String,
            val error: Throwable? = null,
            val requiresReboot: Boolean = false,
            val rollbackAction: OperationAction? = null,
        ) : OperationCompletion<Nothing>
    }

    companion object {
        const val DEFAULT_OPERATION_TIMEOUT_MS = 20L * 60L * 1000L
    }
}
