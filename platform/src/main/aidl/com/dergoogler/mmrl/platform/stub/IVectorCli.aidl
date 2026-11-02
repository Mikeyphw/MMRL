package com.dergoogler.mmrl.platform.stub;

// Narrow, app-owned Binder IPC. Never exposes arbitrary shell command execution.
interface IVectorCli {
    String execute(String operation, String modulePackage, String targetPackage);
}
