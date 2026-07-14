package com.dergoogler.mmrl.tasker;

import com.dergoogler.mmrl.R;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot;

@TaskerInputRoot
public class TaskerRequestInput {
    @TaskerInputField("module_id", R.string.tasker_field_module_id)
    public String moduleId;
    @TaskerInputField("operation_id", R.string.tasker_field_operation_id)
    public String operationId;
    @TaskerInputField("url", R.string.tasker_field_url)
    public String url;
    @TaskerInputField("filename", R.string.tasker_field_filename)
    public String filename;
    @TaskerInputField("force_refresh", R.string.tasker_field_force_refresh)
    public boolean forceRefresh;
    @TaskerInputField("review_token", R.string.tasker_field_review_token)
    public String reviewToken;

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
