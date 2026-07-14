package com.dergoogler.mmrl.tasker;

import com.dergoogler.mmrl.R;
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject;
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable;

@TaskerOutputObject
public class TaskerResultOutput {
    public boolean success;
    public String status;
    public String message;
    public String operationId;
    public String operationType;
    public String phase;
    public int progress;
    public String moduleId;
    public String moduleName;
    public boolean installed;
    public boolean enabled;
    public String installedVersion;
    public int installedVersionCode;
    public String availableVersion;
    public int availableVersionCode;
    public boolean updateAvailable;
    public boolean updateIgnored;
    public String repository;
    public boolean rebootRequired;
    public boolean rollbackAvailable;
    public String errorCode;
    public String errorMessage;
    public String logUri;
    public String reviewToken;
    public long reviewExpiresAt;
    public boolean approvalRequired;
    public String safetyLevel;
    public String inspectionSummary;
    public String resultJson;
    public int count;
    public String[] moduleIds;
    public String[] moduleNames;
    public String[] versions;
    public String[] states;

    public TaskerResultOutput() {
        this(
                true,
                "",
                "",
                "",
                "",
                "",
                -1,
                "",
                "",
                false,
                false,
                "",
                -1,
                "",
                -1,
                false,
                false,
                "",
                false,
                false,
                "",
                "",
                "",
                "",
                0L,
                false,
                "",
                "",
                "{}",
                0,
                new String[0],
                new String[0],
                new String[0],
                new String[0]
        );
    }

    public TaskerResultOutput(
            boolean success,
            String status,
            String message,
            String operationId,
            String operationType,
            String phase,
            int progress,
            String moduleId,
            String moduleName,
            boolean installed,
            boolean enabled,
            String installedVersion,
            int installedVersionCode,
            String availableVersion,
            int availableVersionCode,
            boolean updateAvailable,
            boolean updateIgnored,
            String repository,
            boolean rebootRequired,
            boolean rollbackAvailable,
            String errorCode,
            String errorMessage,
            String logUri,
            String reviewToken,
            long reviewExpiresAt,
            boolean approvalRequired,
            String safetyLevel,
            String inspectionSummary,
            String resultJson,
            int count,
            String[] moduleIds,
            String[] moduleNames,
            String[] versions,
            String[] states
    ) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.operationId = operationId;
        this.operationType = operationType;
        this.phase = phase;
        this.progress = progress;
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.installed = installed;
        this.enabled = enabled;
        this.installedVersion = installedVersion;
        this.installedVersionCode = installedVersionCode;
        this.availableVersion = availableVersion;
        this.availableVersionCode = availableVersionCode;
        this.updateAvailable = updateAvailable;
        this.updateIgnored = updateIgnored;
        this.repository = repository;
        this.rebootRequired = rebootRequired;
        this.rollbackAvailable = rollbackAvailable;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.logUri = logUri;
        this.reviewToken = reviewToken;
        this.reviewExpiresAt = reviewExpiresAt;
        this.approvalRequired = approvalRequired;
        this.safetyLevel = safetyLevel;
        this.inspectionSummary = inspectionSummary;
        this.resultJson = resultJson;
        this.count = count;
        this.moduleIds = moduleIds;
        this.moduleNames = moduleNames;
        this.versions = versions;
        this.states = states;
    }

    @TaskerOutputVariable("success", R.string.tasker_var_success_label, R.string.tasker_var_success_description)
    public boolean getSuccess() { return success; }
    public void setSuccess(boolean value) { success = value; }

    @TaskerOutputVariable("status", R.string.tasker_var_status_label, R.string.tasker_var_status_description)
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }

    @TaskerOutputVariable("message", R.string.tasker_var_message_label, R.string.tasker_var_message_description)
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }

    @TaskerOutputVariable("operation_id", R.string.tasker_var_operation_id_label, R.string.tasker_var_operation_id_description)
    public String getOperationId() { return operationId; }
    public void setOperationId(String value) { operationId = value; }

    @TaskerOutputVariable("operation_type", R.string.tasker_var_operation_type_label, R.string.tasker_var_operation_type_description)
    public String getOperationType() { return operationType; }
    public void setOperationType(String value) { operationType = value; }

    @TaskerOutputVariable("phase", R.string.tasker_var_phase_label, R.string.tasker_var_phase_description)
    public String getPhase() { return phase; }
    public void setPhase(String value) { phase = value; }

    @TaskerOutputVariable("progress", R.string.tasker_var_progress_label, R.string.tasker_var_progress_description)
    public int getProgress() { return progress; }
    public void setProgress(int value) { progress = value; }

    @TaskerOutputVariable("module_id", R.string.tasker_var_module_id_label, R.string.tasker_var_module_id_description)
    public String getModuleId() { return moduleId; }
    public void setModuleId(String value) { moduleId = value; }

    @TaskerOutputVariable("module_name", R.string.tasker_var_module_name_label, R.string.tasker_var_module_name_description)
    public String getModuleName() { return moduleName; }
    public void setModuleName(String value) { moduleName = value; }

    @TaskerOutputVariable("installed", R.string.tasker_var_installed_label, R.string.tasker_var_installed_description)
    public boolean isInstalled() { return installed; }
    public void setInstalled(boolean value) { installed = value; }

    @TaskerOutputVariable("enabled", R.string.tasker_var_enabled_label, R.string.tasker_var_enabled_description)
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }

    @TaskerOutputVariable("installed_version", R.string.tasker_var_installed_version_label, R.string.tasker_var_installed_version_description)
    public String getInstalledVersion() { return installedVersion; }
    public void setInstalledVersion(String value) { installedVersion = value; }

    @TaskerOutputVariable("installed_version_code", R.string.tasker_var_installed_version_code_label, R.string.tasker_var_installed_version_code_description)
    public int getInstalledVersionCode() { return installedVersionCode; }
    public void setInstalledVersionCode(int value) { installedVersionCode = value; }

    @TaskerOutputVariable("available_version", R.string.tasker_var_available_version_label, R.string.tasker_var_available_version_description)
    public String getAvailableVersion() { return availableVersion; }
    public void setAvailableVersion(String value) { availableVersion = value; }

    @TaskerOutputVariable("available_version_code", R.string.tasker_var_available_version_code_label, R.string.tasker_var_available_version_code_description)
    public int getAvailableVersionCode() { return availableVersionCode; }
    public void setAvailableVersionCode(int value) { availableVersionCode = value; }

    @TaskerOutputVariable("update_available", R.string.tasker_var_update_available_label, R.string.tasker_var_update_available_description)
    public boolean isUpdateAvailable() { return updateAvailable; }
    public void setUpdateAvailable(boolean value) { updateAvailable = value; }

    @TaskerOutputVariable("update_ignored", R.string.tasker_var_update_ignored_label, R.string.tasker_var_update_ignored_description)
    public boolean isUpdateIgnored() { return updateIgnored; }
    public void setUpdateIgnored(boolean value) { updateIgnored = value; }

    @TaskerOutputVariable("repository", R.string.tasker_var_repository_label, R.string.tasker_var_repository_description)
    public String getRepository() { return repository; }
    public void setRepository(String value) { repository = value; }

    @TaskerOutputVariable("reboot_required", R.string.tasker_var_reboot_required_label, R.string.tasker_var_reboot_required_description)
    public boolean isRebootRequired() { return rebootRequired; }
    public void setRebootRequired(boolean value) { rebootRequired = value; }

    @TaskerOutputVariable("rollback_available", R.string.tasker_var_rollback_available_label, R.string.tasker_var_rollback_available_description)
    public boolean isRollbackAvailable() { return rollbackAvailable; }
    public void setRollbackAvailable(boolean value) { rollbackAvailable = value; }

    @TaskerOutputVariable("error_code", R.string.tasker_var_error_code_label, R.string.tasker_var_error_code_description)
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { errorCode = value; }

    @TaskerOutputVariable("error_message", R.string.tasker_var_error_message_label, R.string.tasker_var_error_message_description)
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }

    @TaskerOutputVariable("log_uri", R.string.tasker_var_log_uri_label, R.string.tasker_var_log_uri_description)
    public String getLogUri() { return logUri; }
    public void setLogUri(String value) { logUri = value; }

    @TaskerOutputVariable("review_token", R.string.tasker_var_review_token_label, R.string.tasker_var_review_token_description)
    public String getReviewToken() { return reviewToken; }
    public void setReviewToken(String value) { reviewToken = value; }

    @TaskerOutputVariable("review_expires_at", R.string.tasker_var_review_expires_at_label, R.string.tasker_var_review_expires_at_description)
    public long getReviewExpiresAt() { return reviewExpiresAt; }
    public void setReviewExpiresAt(long value) { reviewExpiresAt = value; }

    @TaskerOutputVariable("approval_required", R.string.tasker_var_approval_required_label, R.string.tasker_var_approval_required_description)
    public boolean isApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(boolean value) { approvalRequired = value; }

    @TaskerOutputVariable("safety_level", R.string.tasker_var_safety_level_label, R.string.tasker_var_safety_level_description)
    public String getSafetyLevel() { return safetyLevel; }
    public void setSafetyLevel(String value) { safetyLevel = value; }

    @TaskerOutputVariable("inspection_summary", R.string.tasker_var_inspection_summary_label, R.string.tasker_var_inspection_summary_description)
    public String getInspectionSummary() { return inspectionSummary; }
    public void setInspectionSummary(String value) { inspectionSummary = value; }

    @TaskerOutputVariable("result_json", R.string.tasker_var_result_json_label, R.string.tasker_var_result_json_description)
    public String getResultJson() { return resultJson; }
    public void setResultJson(String value) { resultJson = value; }

    @TaskerOutputVariable("count", R.string.tasker_var_count_label, R.string.tasker_var_count_description)
    public int getCount() { return count; }
    public void setCount(int value) { count = value; }

    @TaskerOutputVariable("module_ids", R.string.tasker_var_module_ids_label, R.string.tasker_var_module_ids_description)
    public String[] getModuleIds() { return moduleIds; }
    public void setModuleIds(String[] value) { moduleIds = value; }

    @TaskerOutputVariable("module_names", R.string.tasker_var_module_names_label, R.string.tasker_var_module_names_description)
    public String[] getModuleNames() { return moduleNames; }
    public void setModuleNames(String[] value) { moduleNames = value; }

    @TaskerOutputVariable("versions", R.string.tasker_var_versions_label, R.string.tasker_var_versions_description)
    public String[] getVersions() { return versions; }
    public void setVersions(String[] value) { versions = value; }

    @TaskerOutputVariable("states", R.string.tasker_var_states_label, R.string.tasker_var_states_description)
    public String[] getStates() { return states; }
    public void setStates(String[] value) { states = value; }
}
