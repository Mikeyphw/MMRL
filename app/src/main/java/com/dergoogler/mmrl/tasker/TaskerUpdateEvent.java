package com.dergoogler.mmrl.tasker;

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot;

@TaskerInputRoot
public class TaskerUpdateEvent {
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
            key = "installed_version",
            labelResId = 0,
            descriptionResId = 0
    )
    public String installedVersion;
    @TaskerInputField(
            key = "installed_version_code",
            labelResId = 0,
            descriptionResId = 0
    )
    public int installedVersionCode;
    @TaskerInputField(
            key = "available_version",
            labelResId = 0,
            descriptionResId = 0
    )
    public String availableVersion;
    @TaskerInputField(
            key = "available_version_code",
            labelResId = 0,
            descriptionResId = 0
    )
    public int availableVersionCode;
    @TaskerInputField(
            key = "repository",
            labelResId = 0,
            descriptionResId = 0
    )
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
