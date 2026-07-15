package com.dergoogler.mmrl.tasker;

import com.dergoogler.mmrl.R;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot;

@TaskerInputRoot
public class TaskerEmptyInput {
    @TaskerInputField(
            key = "include_ignored",
            labelResId = 0,
            descriptionResId = 0,
            labelResIdName = "tasker_field_include_ignored",
            descriptionResIdName = "tasker_field_include_ignored"
    )
    public boolean includeIgnored;

    public TaskerEmptyInput() {
        this(false);
    }

    public TaskerEmptyInput(boolean includeIgnored) {
        this.includeIgnored = includeIgnored;
    }
}
