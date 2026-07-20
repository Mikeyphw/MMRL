package com.dergoogler.mmrl.tasker

import android.content.Context
import com.dergoogler.mmrl.ash.automation.ASH_EXTERNAL_CONTROL_API_VERSION
import com.dergoogler.mmrl.ash.automation.ASH_EXTERNAL_CONTROL_SCHEMA
import com.dergoogler.mmrl.ash.automation.AshAutomationEntryPoint
import com.dergoogler.mmrl.ash.automation.AshExternalControlPolicy
import com.dergoogler.mmrl.ash.automation.AshExternalControlStore
import com.dergoogler.mmrl.ash.automation.AshExternalEvidenceFilter
import com.dergoogler.mmrl.ash.automation.AshExternalMutationKind
import com.dergoogler.mmrl.ash.automation.toJson
import com.dergoogler.mmrl.ash.model.AshGuidanceEngine
import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshModuleIntelligence
import com.dergoogler.mmrl.ash.model.AshRecoveryGuardSeverity
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private const val ONE_MINUTE_MILLIS = 60_000L
private const val TEN_MINUTES_MILLIS = 10L * ONE_MINUTE_MILLIS

private fun ashEntryPoint(context: Context): AshAutomationEntryPoint = EntryPointAccessors.fromApplication(
    context.applicationContext,
    AshAutomationEntryPoint::class.java,
)

private fun ashEnvelope(
    action: String,
    status: String,
    data: JSONObject = JSONObject(),
    message: String = "",
): JSONObject = JSONObject()
    .put("ok", status !in setOf("FAILED", "DENIED", "BLOCKED"))
    .put("apiVersion", ASH_EXTERNAL_CONTROL_API_VERSION)
    .put("schema", ASH_EXTERNAL_CONTROL_SCHEMA)
    .put("action", action)
    .put("status", status)
    .put("message", message)
    .put("data", data)

private fun ashOutput(
    status: String,
    message: String,
    result: JSONObject,
    operationId: String = "",
    operationType: String = "ASH_EXTERNAL",
    approvalRequired: Boolean = false,
    token: String = "",
    expiresAt: Long = 0L,
    planId: String = "",
    recoveryRevision: String = "",
    risk: String = "",
    dryRun: Boolean = false,
    replayed: Boolean = false,
    count: Int = 0,
    moduleIds: Array<String> = emptyArray(),
    moduleNames: Array<String> = emptyArray(),
    states: Array<String> = emptyArray(),
): TaskerResultOutput = taskerResultOutput(
    success = status !in setOf("FAILED", "DENIED", "BLOCKED"),
    status = status,
    message = message,
    operationId = operationId,
    operationType = operationType,
    approvalRequired = approvalRequired,
    resultJson = result.toString(),
    count = count,
    moduleIds = moduleIds,
    moduleNames = moduleNames,
    states = states,
    protocolVersion = ASH_EXTERNAL_CONTROL_API_VERSION,
    schema = ASH_EXTERNAL_CONTROL_SCHEMA,
    automationToken = token,
    automationExpiresAt = expiresAt,
    planId = planId,
    recoveryRevision = recoveryRevision,
    risk = risk,
    dryRun = dryRun,
    replayed = replayed,
)

private fun AshManagerState.requireSnapshot() = requireNotNull(snapshot) {
    liveError ?: "AshReXcue snapshot is unavailable"
}

private fun stateData(state: AshManagerState): JSONObject {
    val snapshot = state.snapshot
    val dashboard = snapshot?.dashboard
    return JSONObject()
        .put("source", state.source.name.lowercase())
        .put("readOnly", state.readOnly)
        .put("rootAvailable", state.rootAvailable)
        .put("compatible", state.lifecycle.compatible)
        .put("lifecycle", state.lifecycle.state.name)
        .put("moduleVersion", state.lifecycle.installation.version)
        .put("moduleVersionCode", state.lifecycle.installation.versionCode)
        .put("generatedAt", snapshot?.generatedAt ?: 0L)
        .put("recoveryRevision", snapshot?.recoveryRevision.orEmpty())
        .put("bootState", dashboard?.bootState.orEmpty())
        .put("restoreState", dashboard?.restoreState.orEmpty())
        .put("quarantined", dashboard?.quarantined ?: 0)
        .put("suspectModules", dashboard?.suspectModules ?: 0)
        .put("latestRescueId", dashboard?.latestRescueId.orEmpty())
        .put("latestRescueStatus", dashboard?.latestRescueStatus.orEmpty())
        .put("error", state.liveError.orEmpty())
}

private fun AshModuleIntelligence.toJson(): JSONObject = JSONObject()
    .put("folder", folder)
    .put("moduleId", moduleId)
    .put("name", name)
    .put("trust", trust)
    .put("quarantined", quarantined)
    .put("enabled", enabled)
    .put("changedSinceStable", changedSinceStable)
    .put("riskScore", riskScore)
    .put("riskBand", riskBand.name)
    .put("summary", summary)
    .put("recommendedAction", recommendedAction)
    .put(
        "factors",
        JSONArray().also { array ->
            factors.forEach { factor ->
                array.put(
                    JSONObject()
                        .put("code", factor.code)
                        .put("title", factor.title)
                        .put("detail", factor.detail)
                        .put("weight", factor.weight),
                )
            }
        },
    )

class AshCapabilitiesRunner : TaskerPluginRunnerAction<TaskerEmptyInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerEmptyInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val state = ashEntryPoint(context).manager().refreshIfStale()
                val moduleCapabilities = state.snapshot?.capabilities
                val data = JSONObject()
                    .put("apiVersion", ASH_EXTERNAL_CONTROL_API_VERSION)
                    .put("schema", ASH_EXTERNAL_CONTROL_SCHEMA)
                    .put(
                        "features",
                        JSONArray(
                            listOf(
                                "capability-discovery",
                                "recovery-status",
                                "module-evidence",
                                "recovery-plan-preview",
                                "guarded-plan-execution",
                                "guidance-outcomes",
                                "idempotency",
                                "one-shot-tokens",
                                "rate-limits",
                                "dry-run",
                                "audit-history",
                            ),
                        ),
                    )
                    .put("moduleApiVersion", moduleCapabilities?.apiVersion ?: 0)
                    .put("moduleFeatures", JSONArray(moduleCapabilities?.features?.sorted().orEmpty()))
                    .put("rootAvailable", state.rootAvailable)
                    .put("compatible", state.lifecycle.compatible)
                ashOutput(
                    status = "OK",
                    message = "AshReXcue external-control capabilities loaded",
                    result = ashEnvelope("capabilities", "OK", data),
                )
            }
        }
}

class AshRecoveryStatusRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val store = AshExternalControlStore(context)
                store.requireRateLimit("read", AshExternalControlPolicy.MAX_READS_PER_MINUTE, ONE_MINUTE_MILLIS)
                val manager = ashEntryPoint(context).manager()
                val state = if (input.regular.forceRefresh) manager.refresh() else manager.refreshIfStale()
                val data = stateData(state)
                ashOutput(
                    status = if (state.source == AshSnapshotSource.None) "UNAVAILABLE" else "OK",
                    message = state.liveError ?: "AshReXcue recovery status loaded",
                    result = ashEnvelope("status", if (state.source == AshSnapshotSource.None) "UNAVAILABLE" else "OK", data),
                    recoveryRevision = state.snapshot?.recoveryRevision.orEmpty(),
                )
            }
        }
}

class AshListEvidenceRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val store = AshExternalControlStore(context)
                store.requireRateLimit("read", AshExternalControlPolicy.MAX_READS_PER_MINUTE, ONE_MINUTE_MILLIS)
                val manager = ashEntryPoint(context).manager()
                val state = if (input.regular.forceRefresh) manager.refresh() else manager.refreshIfStale()
                val snapshot = state.requireSnapshot()
                val filter = AshExternalEvidenceFilter.parse(input.regular.ashFilter)
                val items = AshExternalControlPolicy.filterEvidence(
                    snapshot = snapshot,
                    filter = filter,
                    source = state.source,
                    readOnly = state.readOnly,
                )
                val array = JSONArray().also { result -> items.forEach { result.put(it.toJson()) } }
                val data = JSONObject()
                    .put("filter", filter.wireValue)
                    .put("recoveryRevision", snapshot.recoveryRevision)
                    .put("count", items.size)
                    .put("items", array)
                ashOutput(
                    status = "OK",
                    message = "${items.size} AshReXcue evidence item(s)",
                    result = ashEnvelope("list-evidence", "OK", data),
                    recoveryRevision = snapshot.recoveryRevision,
                    count = items.size,
                    moduleIds = items.map { it.moduleId }.toTypedArray(),
                    moduleNames = items.map { it.name }.toTypedArray(),
                    states = items.map { it.riskBand.name }.toTypedArray(),
                )
            }
        }
}

class AshPrepareRecoveryPlanRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val request = input.regular
                val idempotencyKey = AshExternalControlPolicy.requireIdempotencyKey(request.idempotencyKey)
                val entry = ashEntryPoint(context)
                val preferences = entry.preferences().data.first()
                require(preferences.taskerIntegrationEnabled && preferences.taskerAllowAshRecovery) {
                    "AshReXcue recovery automation is disabled in MMRL Tasker settings"
                }
                val store = AshExternalControlStore(context)
                store.requireRateLimit(
                    "prepare",
                    AshExternalControlPolicy.MAX_PREVIEWS_PER_MINUTE,
                    ONE_MINUTE_MILLIS,
                )
                val manager = entry.manager()
                val state = manager.refresh()
                require(state.source == AshSnapshotSource.Live && state.lifecycle.compatible) {
                    state.liveError ?: "Live compatible AshReXcue access is required"
                }
                val snapshot = state.requireSnapshot()
                val prepared = AshExternalControlPolicy.preparePlan(
                    snapshot = snapshot,
                    presetValue = request.ashPreset,
                    foldersValue = request.ashFolders,
                    idempotencyKey = idempotencyKey,
                    dryRun = request.dryRun,
                )
                val history = TaskerRuntime.repositories(context).operationHistoryRepository()
                val operationId = history.start(
                    kind = OperationKind.ASH_RESTORATION,
                    title = prepared.plan.title,
                    summary = "Recovery plan preview requested by Tasker",
                    moduleId = prepared.plan.affectedFolders.singleOrNull(),
                    origin = "TASKER_ASH",
                )
                val tokenRecord = if (prepared.plan.canExecute && !prepared.dryRun) {
                    store.createOrReuse(prepared)
                } else {
                    null
                }
                val blockers = prepared.plan.guards.filter { it.severity == AshRecoveryGuardSeverity.Blocker }
                val status = when {
                    blockers.isNotEmpty() -> "BLOCKED"
                    prepared.dryRun -> "DRY_RUN"
                    else -> "READY"
                }
                val data = JSONObject()
                    .put("plan", prepared.plan.toJson())
                    .put("token", tokenRecord?.token.orEmpty())
                    .put("expiresAt", tokenRecord?.prepared?.expiresAt ?: 0L)
                    .put("dryRun", prepared.dryRun)
                    .put("idempotencyKey", idempotencyKey)
                history.appendLog(operationId, "preset=${prepared.plan.preset.name.lowercase()}")
                history.appendLog(operationId, "revision=${prepared.plan.recoveryRevision}")
                history.appendLog(operationId, "modules=${prepared.plan.affectedFolders.joinToString(",")}")
                history.succeed(operationId, "Tasker recovery plan preview completed: $status")
                ashOutput(
                    status = status,
                    message = when (status) {
                        "BLOCKED" -> blockers.joinToString(" ") { "${it.title}: ${it.detail}" }
                        "DRY_RUN" -> "Recovery plan preview completed without issuing an execution token"
                        else -> "Recovery plan is ready for guarded execution"
                    },
                    result = ashEnvelope("prepare-plan", status, data),
                    operationId = operationId,
                    operationType = OperationKind.ASH_RESTORATION.name,
                    token = tokenRecord?.token.orEmpty(),
                    expiresAt = tokenRecord?.prepared?.expiresAt ?: 0L,
                    planId = prepared.plan.id,
                    recoveryRevision = prepared.plan.recoveryRevision,
                    risk = prepared.plan.risk.name,
                    dryRun = prepared.dryRun,
                    count = prepared.plan.affectedFolders.size,
                    moduleIds = prepared.plan.affectedFolders.toTypedArray(),
                    states = Array(prepared.plan.affectedFolders.size) { prepared.plan.risk.name },
                )
            }
        }
}

class AshExecuteRecoveryPlanRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val request = input.regular
                val idempotencyKey = AshExternalControlPolicy.requireIdempotencyKey(request.idempotencyKey)
                val token = AshExternalControlPolicy.requireToken(request.ashAutomationToken)
                val store = AshExternalControlStore(context)
                store.receipt(idempotencyKey)?.let { receipt ->
                    val result = runCatching { JSONObject(receipt.resultJson) }.getOrElse { JSONObject() }
                    return@runBlocking ashOutput(
                        status = receipt.status,
                        message = receipt.message,
                        result = ashEnvelope("execute-plan", receipt.status, result, receipt.message),
                        operationId = receipt.operationId,
                        operationType = OperationKind.ASH_RESTORATION.name,
                        replayed = true,
                    )
                }
                store.requireRateLimit(
                    "execute",
                    AshExternalControlPolicy.MAX_EXECUTIONS_PER_TEN_MINUTES,
                    TEN_MINUTES_MILLIS,
                )
                val record = store.peek(token, idempotencyKey)
                val history = TaskerRuntime.repositories(context).operationHistoryRepository()
                if (record.operationId.isNotBlank()) {
                    val existing = history.getById(record.operationId)
                    val existingStatus = existing?.status ?: "QUEUED"
                    return@runBlocking ashOutput(
                        status = existingStatus,
                        message = existing?.summary ?: "Recovery plan is already queued",
                        result = ashEnvelope(
                            "execute-plan",
                            existingStatus,
                            JSONObject()
                                .put("operationId", record.operationId)
                                .put("plan", record.prepared.plan.toJson()),
                        ),
                        operationId = record.operationId,
                        operationType = OperationKind.ASH_RESTORATION.name,
                        planId = record.prepared.plan.id,
                        recoveryRevision = record.prepared.plan.recoveryRevision,
                        risk = record.prepared.plan.risk.name,
                        replayed = true,
                    )
                }
                val entry = ashEntryPoint(context)
                val preferences = entry.preferences().data.first()
                val policyDecision = TaskerAuthorizationPolicy.decideForModules(
                    context = context,
                    preferences = preferences,
                    capability = TaskerCapability.ASH_RECOVERY,
                    moduleIds = record.prepared.plan.affectedFolders,
                )
                val decision = if (
                    record.prepared.plan.risk.name == "High" &&
                    policyDecision == TaskerAuthorizationDecision.EXECUTE
                ) {
                    TaskerAuthorizationDecision.REQUIRE_APPROVAL
                } else {
                    policyDecision
                }
                val operationId = history.start(
                    kind = OperationKind.ASH_RESTORATION,
                    title = record.prepared.plan.title,
                    summary = "Guarded recovery plan requested by Tasker",
                    moduleId = record.prepared.plan.affectedFolders.singleOrNull(),
                    origin = "TASKER_ASH",
                )
                history.appendLog(operationId, "token=${token.take(18)}…")
                history.appendLog(operationId, "idempotency=$idempotencyKey")
                history.appendLog(operationId, "revision=${record.prepared.plan.recoveryRevision}")
                if (decision == TaskerAuthorizationDecision.DENY) {
                    history.fail(operationId, "AshReXcue Tasker recovery is disabled by policy")
                    throw IllegalStateException("AshReXcue Tasker recovery is disabled in MMRL settings")
                }
                store.reserve(token, idempotencyKey, operationId)
                val status = try {
                    TaskerRootDispatcher.dispatch(
                        context,
                        TaskerRootRequest(
                            operationId = operationId,
                            command = "ASH_EXECUTE_PLAN",
                            moduleId = record.prepared.plan.affectedFolders.singleOrNull().orEmpty(),
                            moduleName = "${record.prepared.plan.title} (${record.prepared.plan.affectedFolders.size})",
                            ashAutomationToken = token,
                            idempotencyKey = idempotencyKey,
                        ),
                        decision,
                    )
                } catch (error: Throwable) {
                    store.releaseReservation(token, operationId)
                    history.fail(operationId, error.message ?: "Unable to queue recovery plan", error)
                    throw error
                }
                if (status == "AWAITING_APPROVAL") {
                    history.phase(operationId, OperationPhase.APPROVAL, "Waiting for MMRL approval")
                }
                val data = JSONObject()
                    .put("operationId", operationId)
                    .put("approvalRequired", status == "AWAITING_APPROVAL")
                    .put("plan", record.prepared.plan.toJson())
                ashOutput(
                    status = status,
                    message = if (status == "AWAITING_APPROVAL") {
                        "Waiting for MMRL approval"
                    } else {
                        "Guarded recovery plan queued"
                    },
                    result = ashEnvelope("execute-plan", status, data),
                    operationId = operationId,
                    operationType = OperationKind.ASH_RESTORATION.name,
                    approvalRequired = status == "AWAITING_APPROVAL",
                    planId = record.prepared.plan.id,
                    recoveryRevision = record.prepared.plan.recoveryRevision,
                    risk = record.prepared.plan.risk.name,
                    count = record.prepared.plan.affectedFolders.size,
                    moduleIds = record.prepared.plan.affectedFolders.toTypedArray(),
                )
            }
        }
}

class AshRecordGuidanceOutcomeRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val request = input.regular
                val idempotencyKey = AshExternalControlPolicy.requireIdempotencyKey(request.idempotencyKey)
                val store = AshExternalControlStore(context)
                store.receipt(idempotencyKey)?.let { receipt ->
                    return@runBlocking ashOutput(
                        status = receipt.status,
                        message = receipt.message,
                        result = ashEnvelope(
                            "record-outcome",
                            receipt.status,
                            runCatching { JSONObject(receipt.resultJson) }.getOrElse { JSONObject() },
                            receipt.message,
                        ),
                        operationId = receipt.operationId,
                        replayed = true,
                    )
                }
                store.requireRateLimit(
                    "outcome",
                    AshExternalControlPolicy.MAX_OUTCOMES_PER_MINUTE,
                    ONE_MINUTE_MILLIS,
                )
                val entry = ashEntryPoint(context)
                val preferences = entry.preferences().data.first()
                require(preferences.taskerIntegrationEnabled && preferences.taskerAllowAshRecovery) {
                    "AshReXcue recovery automation is disabled in MMRL Tasker settings"
                }
                val recommendationId = AshExternalControlPolicy.requireRecommendationId(request.recommendationId)
                val folder = AshExternalControlPolicy.requireFolder(request.moduleFolder?.trim().orEmpty())
                val outcome = AshExternalControlPolicy.parseOutcome(request.guidanceOutcome)
                val history = TaskerRuntime.repositories(context).operationHistoryRepository()
                val operationId = history.start(
                    kind = OperationKind.ASH_RESCUE,
                    title = "AshReXcue guidance outcome",
                    summary = "Guidance feedback requested by Tasker",
                    moduleId = folder,
                    origin = "TASKER_ASH",
                )
                return@runBlocking try {
                    val result = entry.manager().recordGuidanceOutcome(recommendationId, folder, outcome)
                    check(result.ok) { result.message }
                    val data = JSONObject()
                        .put("recommendationId", recommendationId)
                        .put("moduleFolder", folder)
                        .put("outcome", outcome.wireValue)
                    history.succeed(operationId, result.message)
                    store.recordIdempotentResult(
                        kind = AshExternalMutationKind.RecordOutcome,
                        idempotencyKeyValue = idempotencyKey,
                        operationId = operationId,
                        status = "SUCCEEDED",
                        message = result.message,
                        resultJson = data.toString(),
                    )
                    ashOutput(
                        status = "SUCCEEDED",
                        message = result.message,
                        result = ashEnvelope("record-outcome", "SUCCEEDED", data),
                        operationId = operationId,
                        operationType = OperationKind.ASH_RESCUE.name,
                        moduleIds = arrayOf(folder),
                        states = arrayOf(outcome.wireValue),
                    )
                } catch (error: Throwable) {
                    history.fail(operationId, error.message ?: "Unable to record guidance outcome", error)
                    throw error
                }
            }
        }
}

class AshRefreshEvidenceRunner : TaskerPluginRunnerAction<TaskerEmptyInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerEmptyInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val store = AshExternalControlStore(context)
                store.requireRateLimit("refresh", 12, ONE_MINUTE_MILLIS)
                val history = TaskerRuntime.repositories(context).operationHistoryRepository()
                val operationId = history.start(
                    kind = OperationKind.ASH_RESCUE,
                    title = "Refresh AshReXcue evidence",
                    summary = "Evidence refresh requested by Tasker",
                    origin = "TASKER_ASH",
                )
                return@runBlocking try {
                    val state = ashEntryPoint(context).manager().refresh()
                    val snapshot = state.requireSnapshot()
                    val guidance = AshGuidanceEngine.build(snapshot)
                    val data = stateData(state)
                        .put("candidateCount", guidance.candidates.size)
                        .put("recommendationCount", guidance.recommendations.size)
                    history.succeed(operationId, "AshReXcue evidence refreshed")
                    ashOutput(
                        status = "SUCCEEDED",
                        message = "AshReXcue evidence refreshed",
                        result = ashEnvelope("refresh-evidence", "SUCCEEDED", data),
                        operationId = operationId,
                        operationType = OperationKind.ASH_RESCUE.name,
                        recoveryRevision = snapshot.recoveryRevision,
                    )
                } catch (error: Throwable) {
                    history.fail(operationId, error.message ?: "AshReXcue evidence refresh failed", error)
                    throw error
                }
            }
        }
}
