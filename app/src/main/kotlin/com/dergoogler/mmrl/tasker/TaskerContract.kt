package com.dergoogler.mmrl.tasker

import com.dergoogler.mmrl.R
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

@TaskerInputRoot
class TaskerRequestInput @JvmOverloads constructor(
    @field:TaskerInputField("module_id", R.string.tasker_field_module_id)
    var moduleId: String? = null,
    @field:TaskerInputField("operation_id", R.string.tasker_field_operation_id)
    var operationId: String? = null,
    @field:TaskerInputField("url", R.string.tasker_field_url)
    var url: String? = null,
    @field:TaskerInputField("filename", R.string.tasker_field_filename)
    var filename: String? = null,
    @field:TaskerInputField("force_refresh", R.string.tasker_field_force_refresh)
    var forceRefresh: Boolean = false,
    @field:TaskerInputField("review_token", R.string.tasker_field_review_token)
    var reviewToken: String? = null,
)

@TaskerInputRoot
class TaskerEmptyInput @JvmOverloads constructor(
    @field:TaskerInputField("include_ignored", R.string.tasker_field_include_ignored)
    var includeIgnored: Boolean = false,
)

@TaskerOutputObject
class TaskerResultOutput(
    @get:TaskerOutputVariable(R.string.tasker_var_success, R.string.tasker_var_success_label, R.string.tasker_var_success_description)
    val success: Boolean = true,
    @get:TaskerOutputVariable(R.string.tasker_var_status, R.string.tasker_var_status_label, R.string.tasker_var_status_description)
    val status: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_message, R.string.tasker_var_message_label, R.string.tasker_var_message_description)
    val message: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_operation_id, R.string.tasker_var_operation_id_label, R.string.tasker_var_operation_id_description)
    val operationId: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_operation_type, R.string.tasker_var_operation_type_label, R.string.tasker_var_operation_type_description)
    val operationType: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_phase, R.string.tasker_var_phase_label, R.string.tasker_var_phase_description)
    val phase: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_progress, R.string.tasker_var_progress_label, R.string.tasker_var_progress_description)
    val progress: Int = -1,
    @get:TaskerOutputVariable(R.string.tasker_var_module_id, R.string.tasker_var_module_id_label, R.string.tasker_var_module_id_description)
    val moduleId: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_module_name, R.string.tasker_var_module_name_label, R.string.tasker_var_module_name_description)
    val moduleName: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_installed, R.string.tasker_var_installed_label, R.string.tasker_var_installed_description)
    val installed: Boolean = false,
    @get:TaskerOutputVariable(R.string.tasker_var_enabled, R.string.tasker_var_enabled_label, R.string.tasker_var_enabled_description)
    val enabled: Boolean = false,
    @get:TaskerOutputVariable(R.string.tasker_var_installed_version, R.string.tasker_var_installed_version_label, R.string.tasker_var_installed_version_description)
    val installedVersion: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_installed_version_code, R.string.tasker_var_installed_version_code_label, R.string.tasker_var_installed_version_code_description)
    val installedVersionCode: Int = -1,
    @get:TaskerOutputVariable(R.string.tasker_var_available_version, R.string.tasker_var_available_version_label, R.string.tasker_var_available_version_description)
    val availableVersion: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_available_version_code, R.string.tasker_var_available_version_code_label, R.string.tasker_var_available_version_code_description)
    val availableVersionCode: Int = -1,
    @get:TaskerOutputVariable(R.string.tasker_var_update_available, R.string.tasker_var_update_available_label, R.string.tasker_var_update_available_description)
    val updateAvailable: Boolean = false,
    @get:TaskerOutputVariable(R.string.tasker_var_update_ignored, R.string.tasker_var_update_ignored_label, R.string.tasker_var_update_ignored_description)
    val updateIgnored: Boolean = false,
    @get:TaskerOutputVariable(R.string.tasker_var_repository, R.string.tasker_var_repository_label, R.string.tasker_var_repository_description)
    val repository: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_reboot_required, R.string.tasker_var_reboot_required_label, R.string.tasker_var_reboot_required_description)
    val rebootRequired: Boolean = false,
    @get:TaskerOutputVariable(R.string.tasker_var_rollback_available, R.string.tasker_var_rollback_available_label, R.string.tasker_var_rollback_available_description)
    val rollbackAvailable: Boolean = false,
    @get:TaskerOutputVariable(R.string.tasker_var_error_code, R.string.tasker_var_error_code_label, R.string.tasker_var_error_code_description)
    val errorCode: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_error_message, R.string.tasker_var_error_message_label, R.string.tasker_var_error_message_description)
    val errorMessage: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_log_uri, R.string.tasker_var_log_uri_label, R.string.tasker_var_log_uri_description)
    val logUri: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_review_token, R.string.tasker_var_review_token_label, R.string.tasker_var_review_token_description)
    val reviewToken: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_review_expires_at, R.string.tasker_var_review_expires_at_label, R.string.tasker_var_review_expires_at_description)
    val reviewExpiresAt: Long = 0L,
    @get:TaskerOutputVariable(R.string.tasker_var_approval_required, R.string.tasker_var_approval_required_label, R.string.tasker_var_approval_required_description)
    val approvalRequired: Boolean = false,
    @get:TaskerOutputVariable(R.string.tasker_var_safety_level, R.string.tasker_var_safety_level_label, R.string.tasker_var_safety_level_description)
    val safetyLevel: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_inspection_summary, R.string.tasker_var_inspection_summary_label, R.string.tasker_var_inspection_summary_description)
    val inspectionSummary: String = "",
    @get:TaskerOutputVariable(R.string.tasker_var_result_json, R.string.tasker_var_result_json_label, R.string.tasker_var_result_json_description)
    val resultJson: String = "{}",
    @get:TaskerOutputVariable(R.string.tasker_var_count, R.string.tasker_var_count_label, R.string.tasker_var_count_description)
    val count: Int = 0,
    @get:TaskerOutputVariable(R.string.tasker_var_module_ids, R.string.tasker_var_module_ids_label, R.string.tasker_var_module_ids_description)
    val moduleIds: Array<String> = emptyArray(),
    @get:TaskerOutputVariable(R.string.tasker_var_module_names, R.string.tasker_var_module_names_label, R.string.tasker_var_module_names_description)
    val moduleNames: Array<String> = emptyArray(),
    @get:TaskerOutputVariable(R.string.tasker_var_versions, R.string.tasker_var_versions_label, R.string.tasker_var_versions_description)
    val versions: Array<String> = emptyArray(),
    @get:TaskerOutputVariable(R.string.tasker_var_states, R.string.tasker_var_states_label, R.string.tasker_var_states_description)
    val states: Array<String> = emptyArray(),
)

@TaskerInputRoot
class TaskerUpdateEvent @JvmOverloads constructor(
    @field:TaskerInputField("module_id") var moduleId: String? = null,
    @field:TaskerInputField("module_name") var moduleName: String? = null,
    @field:TaskerInputField("installed_version") var installedVersion: String? = null,
    @field:TaskerInputField("installed_version_code") var installedVersionCode: Int = -1,
    @field:TaskerInputField("available_version") var availableVersion: String? = null,
    @field:TaskerInputField("available_version_code") var availableVersionCode: Int = -1,
    @field:TaskerInputField("repository") var repository: String? = null,
)

@TaskerInputRoot
class TaskerFailureEvent @JvmOverloads constructor(
    @field:TaskerInputField("operation_id") var operationId: String? = null,
    @field:TaskerInputField("operation_type") var operationType: String? = null,
    @field:TaskerInputField("module_id") var moduleId: String? = null,
    @field:TaskerInputField("module_name") var moduleName: String? = null,
    @field:TaskerInputField("error_message") var errorMessage: String? = null,
    @field:TaskerInputField("phase") var phase: String? = null,
)
