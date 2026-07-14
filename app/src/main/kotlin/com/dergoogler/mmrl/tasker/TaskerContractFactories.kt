package com.dergoogler.mmrl.tasker

internal fun taskerRequestInput(
    moduleId: String? = null,
    operationId: String? = null,
    url: String? = null,
    filename: String? = null,
    forceRefresh: Boolean = false,
    reviewToken: String? = null,
): TaskerRequestInput = TaskerRequestInput().apply {
    this.moduleId = moduleId
    this.operationId = operationId
    this.url = url
    this.filename = filename
    this.forceRefresh = forceRefresh
    this.reviewToken = reviewToken
}

internal fun taskerEmptyInput(
    includeIgnored: Boolean = false,
): TaskerEmptyInput = TaskerEmptyInput().apply {
    this.includeIgnored = includeIgnored
}

internal fun taskerResultOutput(
    success: Boolean = true,
    status: String = "",
    message: String = "",
    operationId: String = "",
    operationType: String = "",
    phase: String = "",
    progress: Int = -1,
    moduleId: String = "",
    moduleName: String = "",
    installed: Boolean = false,
    enabled: Boolean = false,
    installedVersion: String = "",
    installedVersionCode: Int = -1,
    availableVersion: String = "",
    availableVersionCode: Int = -1,
    updateAvailable: Boolean = false,
    updateIgnored: Boolean = false,
    repository: String = "",
    rebootRequired: Boolean = false,
    rollbackAvailable: Boolean = false,
    errorCode: String = "",
    errorMessage: String = "",
    logUri: String = "",
    reviewToken: String = "",
    reviewExpiresAt: Long = 0L,
    approvalRequired: Boolean = false,
    safetyLevel: String = "",
    inspectionSummary: String = "",
    resultJson: String = "{}",
    count: Int = 0,
    moduleIds: Array<String> = emptyArray(),
    moduleNames: Array<String> = emptyArray(),
    versions: Array<String> = emptyArray(),
    states: Array<String> = emptyArray(),
): TaskerResultOutput = TaskerResultOutput().apply {
    this.success = success
    this.status = status
    this.message = message
    this.operationId = operationId
    this.operationType = operationType
    this.phase = phase
    this.progress = progress
    this.moduleId = moduleId
    this.moduleName = moduleName
    this.installed = installed
    this.enabled = enabled
    this.installedVersion = installedVersion
    this.installedVersionCode = installedVersionCode
    this.availableVersion = availableVersion
    this.availableVersionCode = availableVersionCode
    this.updateAvailable = updateAvailable
    this.updateIgnored = updateIgnored
    this.repository = repository
    this.rebootRequired = rebootRequired
    this.rollbackAvailable = rollbackAvailable
    this.errorCode = errorCode
    this.errorMessage = errorMessage
    this.logUri = logUri
    this.reviewToken = reviewToken
    this.reviewExpiresAt = reviewExpiresAt
    this.approvalRequired = approvalRequired
    this.safetyLevel = safetyLevel
    this.inspectionSummary = inspectionSummary
    this.resultJson = resultJson
    this.count = count
    this.moduleIds = moduleIds
    this.moduleNames = moduleNames
    this.versions = versions
    this.states = states
}

internal fun taskerUpdateEvent(
    moduleId: String? = null,
    moduleName: String? = null,
    installedVersion: String? = null,
    installedVersionCode: Int = -1,
    availableVersion: String? = null,
    availableVersionCode: Int = -1,
    repository: String? = null,
): TaskerUpdateEvent = TaskerUpdateEvent().apply {
    this.moduleId = moduleId
    this.moduleName = moduleName
    this.installedVersion = installedVersion
    this.installedVersionCode = installedVersionCode
    this.availableVersion = availableVersion
    this.availableVersionCode = availableVersionCode
    this.repository = repository
}

internal fun taskerFailureEvent(
    operationId: String? = null,
    operationType: String? = null,
    moduleId: String? = null,
    moduleName: String? = null,
    errorMessage: String? = null,
    phase: String? = null,
): TaskerFailureEvent = TaskerFailureEvent().apply {
    this.operationId = operationId
    this.operationType = operationType
    this.moduleId = moduleId
    this.moduleName = moduleName
    this.errorMessage = errorMessage
    this.phase = phase
}
