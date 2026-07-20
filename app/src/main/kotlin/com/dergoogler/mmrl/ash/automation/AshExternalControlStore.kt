package com.dergoogler.mmrl.ash.automation

import android.content.Context
import android.util.AtomicFile
import com.dergoogler.mmrl.ash.model.AshRecoveryGuard
import com.dergoogler.mmrl.ash.model.AshRecoveryGuardSeverity
import com.dergoogler.mmrl.ash.model.AshRecoveryPlan
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanPreset
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanRisk
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class AshExternalTokenRecord(
    val token: String,
    val prepared: AshExternalPreparedPlan,
    val operationId: String = "",
)

internal data class AshExternalReceipt(
    val idempotencyKey: String,
    val operationId: String,
    val status: String,
    val message: String,
    val resultJson: String,
    val completedAt: Long,
)

internal class AshExternalControlStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "ash-external-control")
    private val tokenDirectory = File(root, "tokens")
    private val receiptDirectory = File(root, "receipts")
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    init {
        tokenDirectory.mkdirs()
        receiptDirectory.mkdirs()
    }

    @Synchronized
    fun createOrReuse(prepared: AshExternalPreparedPlan): AshExternalTokenRecord {
        prune()
        require(receipt(prepared.idempotencyKey) == null) {
            "This idempotency key already completed an automation request"
        }
        existingToken(prepared.idempotencyKey)?.let { existing ->
            require(existing.prepared.sameRequestAs(prepared)) {
                "Idempotency key is already bound to a different recovery plan"
            }
            return existing
        }
        val token = "ash1.${UUID.randomUUID().toString().replace("-", "")}.${UUID.randomUUID().toString().replace("-", "")}"
        val record = AshExternalTokenRecord(token = token, prepared = prepared)
        writeAtomic(tokenFile(token), record.toJson().toString())
        return record
    }

    @Synchronized
    fun peek(tokenValue: String, idempotencyKeyValue: String): AshExternalTokenRecord {
        prune()
        val token = AshExternalControlPolicy.requireToken(tokenValue)
        val idempotencyKey = AshExternalControlPolicy.requireIdempotencyKey(idempotencyKeyValue)
        val record = readToken(token) ?: throw IllegalArgumentException("Automation token was not found or has expired")
        validate(record, idempotencyKey)
        return record
    }

    @Synchronized
    fun reserve(tokenValue: String, idempotencyKeyValue: String, operationId: String): AshExternalTokenRecord {
        val record = peek(tokenValue, idempotencyKeyValue)
        require(operationId.isNotBlank()) { "Operation ID is required" }
        if (record.operationId.isNotBlank()) {
            require(record.operationId == operationId) {
                "Automation token is already reserved by operation ${record.operationId}"
            }
            return record
        }
        return record.copy(operationId = operationId).also {
            writeAtomic(tokenFile(it.token), it.toJson().toString())
        }
    }

    @Synchronized
    fun releaseReservation(tokenValue: String, operationId: String) {
        val token = AshExternalControlPolicy.requireToken(tokenValue)
        val record = readToken(token) ?: return
        if (record.operationId == operationId) {
            writeAtomic(tokenFile(token), record.copy(operationId = "").toJson().toString())
        }
    }

    @Synchronized
    fun claim(tokenValue: String, idempotencyKeyValue: String, operationId: String): AshExternalTokenRecord {
        val record = peek(tokenValue, idempotencyKeyValue)
        require(record.operationId == operationId) {
            if (record.operationId.isBlank()) {
                "Automation token has not been reserved for this operation"
            } else {
                "Automation token belongs to a different operation"
            }
        }
        return record
    }

    @Synchronized
    fun complete(
        tokenValue: String,
        operationId: String,
        status: String,
        message: String,
        resultJson: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): AshExternalReceipt {
        val token = AshExternalControlPolicy.requireToken(tokenValue)
        val record = readToken(token) ?: throw IllegalArgumentException("Automation token is no longer available")
        require(record.operationId == operationId) { "Automation token does not belong to this operation" }
        val receipt = AshExternalReceipt(
            idempotencyKey = record.prepared.idempotencyKey,
            operationId = operationId,
            status = status,
            message = message,
            resultJson = resultJson,
            completedAt = nowMillis,
        )
        writeAtomic(receiptFile(receipt.idempotencyKey), receipt.toJson().toString())
        tokenFile(token).delete()
        return receipt
    }

    @Synchronized
    fun receipt(idempotencyKeyValue: String): AshExternalReceipt? {
        val idempotencyKey = AshExternalControlPolicy.requireIdempotencyKey(idempotencyKeyValue)
        val file = receiptFile(idempotencyKey)
        return readJson(file)?.let(::receiptFromJson)
    }

    @Synchronized
    fun recordIdempotentResult(
        kind: AshExternalMutationKind,
        idempotencyKeyValue: String,
        operationId: String,
        status: String,
        message: String,
        resultJson: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): AshExternalReceipt {
        val idempotencyKey = AshExternalControlPolicy.requireIdempotencyKey(idempotencyKeyValue)
        receipt(idempotencyKey)?.let { return it }
        val receipt = AshExternalReceipt(
            idempotencyKey = idempotencyKey,
            operationId = operationId,
            status = status,
            message = message,
            resultJson = resultJson,
            completedAt = nowMillis,
        )
        writeAtomic(receiptFile(idempotencyKey), receipt.toJson().put("kind", kind.wireValue).toString())
        return receipt
    }

    @Synchronized
    fun requireRateLimit(
        bucket: String,
        limit: Int,
        windowMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val key = "rate.$bucket"
        val timestamps = preferences.getString(key, "")
            .orEmpty()
            .split(',')
            .mapNotNull(String::toLongOrNull)
        val result = AshExternalControlPolicy.rateLimit(timestamps, nowMillis, limit, windowMillis)
        require(result.allowed) {
            "Automation rate limit reached; retry in ${((result.retryAfterMillis + 999L) / 1_000L)} seconds"
        }
        val active = timestamps.filter { nowMillis - it in 0 until windowMillis } + nowMillis
        preferences.edit().putString(key, active.joinToString(",")).apply()
    }

    @Synchronized
    fun prune(nowMillis: Long = System.currentTimeMillis()) {
        tokenDirectory.listFiles().orEmpty().forEach { source ->
            val record = runCatching { readJson(source)?.let(::tokenRecordFromJson) }.getOrNull()
            if (record == null || record.prepared.expiresAt <= nowMillis) source.delete()
        }
        tokenDirectory.listFiles().orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_TOKEN_FILES)
            .forEach(File::delete)
        val receiptCutoff = nowMillis - RECEIPT_RETENTION_MILLIS
        receiptDirectory.listFiles().orEmpty().forEach { source ->
            val receipt = runCatching { readJson(source)?.let(::receiptFromJson) }.getOrNull()
            if (receipt == null || receipt.completedAt < receiptCutoff) source.delete()
        }
        receiptDirectory.listFiles().orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_RECEIPT_FILES)
            .forEach(File::delete)
    }

    private fun existingToken(idempotencyKey: String): AshExternalTokenRecord? = tokenDirectory
        .listFiles()
        .orEmpty()
        .asSequence()
        .mapNotNull { source ->
            runCatching { readJson(source)?.let(::tokenRecordFromJson) }.getOrNull()
        }
        .firstOrNull { it.prepared.idempotencyKey == idempotencyKey }

    private fun validate(record: AshExternalTokenRecord, idempotencyKey: String) {
        require(record.prepared.expiresAt > System.currentTimeMillis()) { "Automation token has expired" }
        require(record.prepared.idempotencyKey == idempotencyKey) { "Automation token is bound to a different idempotency key" }
        val expected = AshExternalControlPolicy.binding(
            plan = record.prepared.plan,
            idempotencyKey = record.prepared.idempotencyKey,
            createdAt = record.prepared.createdAt,
            expiresAt = record.prepared.expiresAt,
        )
        require(expected == record.prepared.binding) { "Automation token binding is invalid" }
        require(!record.prepared.dryRun) { "Dry-run previews cannot be executed" }
    }

    private fun readToken(token: String): AshExternalTokenRecord? =
        readJson(tokenFile(token))?.let(::tokenRecordFromJson)

    private fun tokenFile(token: String): File = File(tokenDirectory, safe(token) + ".json")
    private fun receiptFile(idempotencyKey: String): File = File(receiptDirectory, safe(idempotencyKey) + ".json")
    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun readJson(target: File): JSONObject? = runCatching {
        JSONObject(AtomicFile(target).readFully().toString(Charsets.UTF_8))
    }.getOrNull()

    private fun writeAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        val atomicFile = AtomicFile(target)
        val output = atomicFile.startWrite()
        try {
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private companion object {
        const val PREFERENCES = "ash_external_control"
        const val RECEIPT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        const val MAX_TOKEN_FILES = 64
        const val MAX_RECEIPT_FILES = 256
    }
}


private fun AshExternalPreparedPlan.sameRequestAs(other: AshExternalPreparedPlan): Boolean =
    idempotencyKey == other.idempotencyKey &&
        dryRun == other.dryRun &&
        plan.id == other.plan.id &&
        plan.preset == other.plan.preset &&
        plan.recoveryRevision == other.plan.recoveryRevision &&
        plan.affectedFolders == other.plan.affectedFolders &&
        plan.risk == other.plan.risk

internal fun AshExternalTokenRecord.toJson(): JSONObject = JSONObject()
    .put("token", token)
    .put("prepared", prepared.toJson())
    .put("operation_id", operationId)

internal fun AshExternalPreparedPlan.toJson(): JSONObject = JSONObject()
    .put("plan", plan.toJson())
    .put("idempotency_key", idempotencyKey)
    .put("dry_run", dryRun)
    .put("created_at", createdAt)
    .put("expires_at", expiresAt)
    .put("binding", binding)

internal fun AshRecoveryPlan.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("preset", preset.name)
    .put("title", title)
    .put("summary", summary)
    .put("affected_folders", JSONArray(affectedFolders))
    .put("risk", risk.name)
    .put("guards", JSONArray().also { array -> guards.forEach { array.put(it.toJson()) } })
    .put("confirmation_phrase", confirmationPhrase)
    .put("rollback_strategy", rollbackStrategy)
    .put("recovery_revision", recoveryRevision)

internal fun AshRecoveryGuard.toJson(): JSONObject = JSONObject()
    .put("code", code)
    .put("title", title)
    .put("detail", detail)
    .put("severity", severity.name)

internal fun AshExternalReceipt.toJson(): JSONObject = JSONObject()
    .put("idempotency_key", idempotencyKey)
    .put("operation_id", operationId)
    .put("status", status)
    .put("message", message)
    .put("result_json", resultJson)
    .put("completed_at", completedAt)

internal fun tokenRecordFromJson(value: JSONObject): AshExternalTokenRecord = AshExternalTokenRecord(
    token = value.getString("token"),
    prepared = preparedPlanFromJson(value.getJSONObject("prepared")),
    operationId = value.optString("operation_id"),
)

internal fun preparedPlanFromJson(value: JSONObject): AshExternalPreparedPlan = AshExternalPreparedPlan(
    plan = recoveryPlanFromJson(value.getJSONObject("plan")),
    idempotencyKey = value.getString("idempotency_key"),
    dryRun = value.optBoolean("dry_run"),
    createdAt = value.getLong("created_at"),
    expiresAt = value.getLong("expires_at"),
    binding = value.getString("binding"),
)

internal fun recoveryPlanFromJson(value: JSONObject): AshRecoveryPlan = AshRecoveryPlan(
    id = value.getString("id"),
    preset = AshRecoveryPlanPreset.valueOf(value.getString("preset")),
    title = value.getString("title"),
    summary = value.getString("summary"),
    affectedFolders = value.getJSONArray("affected_folders").toStringList(),
    risk = AshRecoveryPlanRisk.valueOf(value.getString("risk")),
    guards = value.getJSONArray("guards").toObjectList().map { guard ->
        AshRecoveryGuard(
            code = guard.getString("code"),
            title = guard.getString("title"),
            detail = guard.getString("detail"),
            severity = AshRecoveryGuardSeverity.valueOf(guard.getString("severity")),
        )
    },
    confirmationPhrase = value.optString("confirmation_phrase"),
    rollbackStrategy = value.optString("rollback_strategy"),
    recoveryRevision = value.getString("recovery_revision"),
)

internal fun receiptFromJson(value: JSONObject): AshExternalReceipt = AshExternalReceipt(
    idempotencyKey = value.getString("idempotency_key"),
    operationId = value.getString("operation_id"),
    status = value.getString("status"),
    message = value.optString("message"),
    resultJson = value.optString("result_json", "{}"),
    completedAt = value.getLong("completed_at"),
)

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) add(getString(index))
}

private fun JSONArray.toObjectList(): List<JSONObject> = buildList {
    for (index in 0 until length()) add(getJSONObject(index))
}
