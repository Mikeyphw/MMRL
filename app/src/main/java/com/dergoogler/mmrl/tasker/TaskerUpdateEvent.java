package com.dergoogler.mmrl.tasker;

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot;

@TaskerInputRoot
public class TaskerUpdateEvent {
    @TaskerInputField("module_id")
    public String moduleId;
    @TaskerInputField("module_name")
    public String moduleName;
    @TaskerInputField("installed_version")
    public String installedVersion;
    @TaskerInputField("installed_version_code")
    public int installedVersionCode;
    @TaskerInputField("available_version")
    public String availableVersion;
    @TaskerInputField("available_version_code")
    public int availableVersionCode;
    @TaskerInputField("repository")
    public String repository;

    public TaskerUpdateEvent() {
        this(null, null, null, -1, null, -1, null);
    }

    public TaskerUpdateEvent(
            String moduleId,
            String moduleName,
            String installedVersion,
            int installedVersionCode,
            String availableVersion,
            int availableVersionCode,
            String repository
    ) {
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.installedVersion = installedVersion;
        this.installedVersionCode = installedVersionCode;
        this.availableVersion = availableVersion;
        this.availableVersionCode = availableVersionCode;
        this.repository = repository;
    }
}
