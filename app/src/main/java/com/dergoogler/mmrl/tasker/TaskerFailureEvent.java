package com.dergoogler.mmrl.tasker;

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot;

@TaskerInputRoot
public class TaskerFailureEvent {
    @TaskerInputField(
            key = "operation_id",
            labelResId = 0,
            descriptionResId = 0
    )
    public String operationId;
    @TaskerInputField(
            key = "operation_type",
            labelResId = 0,
            descriptionResId = 0
    )
    public String operationType;
    @TaskerInputField(
            key = "module_id",
            labelResId = 0,
            descriptionResId = 0
    )
    public String moduleId;
    @TaskerInputField(
            key = "module_name",
            labelResId = 0,
            descriptionResId = 0
    )
    public String moduleName;
    @TaskerInputField(
            key = "error_message",
            labelResId = 0,
            descriptionResId = 0
    )
    public String errorMessage;
    @TaskerInputField(
            key = "phase",
            labelResId = 0,
            descriptionResId = 0
    )
    public String phase;

    public TaskerFailureEvent() {
        this(null, null, null, null, null, null);
    }

    public TaskerFailureEvent(
            String operationId,
            String operationType,
            String moduleId,
            String moduleName,
            String errorMessage,
            String phase
    ) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.errorMessage = errorMessage;
        this.phase = phase;
    }
}
