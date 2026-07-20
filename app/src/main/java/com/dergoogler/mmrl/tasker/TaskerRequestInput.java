package com.dergoogler.mmrl.tasker;

import com.dergoogler.mmrl.R;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot;

@TaskerInputRoot
public class TaskerRequestInput {
    @TaskerInputField(
            key = "module_id",
            labelResId = 0,
            descriptionResId = 0,
            labelResIdName = "tasker_field_module_id",
            descriptionResIdName = "tasker_field_module_id"
    )
    public String moduleId;
    @TaskerInputField(
            key = "operation_id",
            labelResId = 0,
            descriptionResId = 0,
            labelResIdName = "tasker_field_operation_id",
            descriptionResIdName = "tasker_field_operation_id"
    )
    public String operationId;
    @TaskerInputField(
            key = "url",
            labelResId = 0,
            descriptionResId = 0,
            labelResIdName = "tasker_field_url",
            descriptionResIdName = "tasker_field_url"
    )
    public String url;
    @TaskerInputField(
            key = "filename",
            labelResId = 0,
            descriptionResId = 0,
            labelResIdName = "tasker_field_filename",
            descriptionResIdName = "tasker_field_filename"
    )
    public String filename;
    @TaskerInputField(
            key = "force_refresh",
            labelResId = 0,
            descriptionResId = 0,
            labelResIdName = "tasker_field_force_refresh",
            descriptionResIdName = "tasker_field_force_refresh"
    )
    public boolean forceRefresh;
    @TaskerInputField(
            key = "review_token",
            labelResId = 0,
            descriptionResId = 0,
            labelResIdName = "tasker_field_review_token",
            descriptionResIdName = "tasker_field_review_token"
    )
    public String reviewToken;

    @TaskerInputField(key = "ash_filter", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_ash_filter", descriptionResIdName = "tasker_field_ash_filter")
    public String ashFilter;
    @TaskerInputField(key = "ash_preset", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_ash_preset", descriptionResIdName = "tasker_field_ash_preset")
    public String ashPreset;
    @TaskerInputField(key = "ash_folders", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_ash_folders", descriptionResIdName = "tasker_field_ash_folders")
    public String ashFolders;
    @TaskerInputField(key = "ash_automation_token", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_ash_token", descriptionResIdName = "tasker_field_ash_token")
    public String ashAutomationToken;
    @TaskerInputField(key = "idempotency_key", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_idempotency_key", descriptionResIdName = "tasker_field_idempotency_key")
    public String idempotencyKey;
    @TaskerInputField(key = "dry_run", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_dry_run", descriptionResIdName = "tasker_field_dry_run")
    public boolean dryRun;
    @TaskerInputField(key = "recommendation_id", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_recommendation_id", descriptionResIdName = "tasker_field_recommendation_id")
    public String recommendationId;
    @TaskerInputField(key = "module_folder", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_module_folder", descriptionResIdName = "tasker_field_module_folder")
    public String moduleFolder;
    @TaskerInputField(key = "guidance_outcome", labelResId = 0, descriptionResId = 0, labelResIdName = "tasker_field_guidance_outcome", descriptionResIdName = "tasker_field_guidance_outcome")
    public String guidanceOutcome;

    public TaskerRequestInput() {
        this(null, null, null, null, false, null);
    }

    public TaskerRequestInput(
            String moduleId,
            String operationId,
            String url,
            String filename,
            boolean forceRefresh,
            String reviewToken
    ) {
        this.moduleId = moduleId;
        this.operationId = operationId;
        this.url = url;
        this.filename = filename;
        this.forceRefresh = forceRefresh;
        this.reviewToken = reviewToken;
    }
}
