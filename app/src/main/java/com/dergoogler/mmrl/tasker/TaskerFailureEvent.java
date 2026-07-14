package com.dergoogler.mmrl.tasker;

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot;

@TaskerInputRoot
public class TaskerFailureEvent {
    @TaskerInputField("operation_id")
    public String operationId;
    @TaskerInputField("operation_type")
    public String operationType;
    @TaskerInputField("module_id")
    public String moduleId;
    @TaskerInputField("module_name")
    public String moduleName;
    @TaskerInputField("error_message")
    public String errorMessage;
    @TaskerInputField("phase")
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
