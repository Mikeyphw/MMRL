package com.mikeyphw.mmrl.hooklab.module;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import java.lang.reflect.Method;

/** Modern Xposed module: one test method in one dedicated disposable package. */
public final class LabHookModule extends XposedModule {
    private static final String TARGET = "com.mikeyphw.mmrl.hooklab.target";
    private static final String CLASS = TARGET + ".ProbeValue";

    @Override public void onPackageReady(PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName()) || !param.isFirstPackage()) return;
        try {
            Class<?> klass = param.getClassLoader().loadClass(CLASS);
            Method method = klass.getDeclaredMethod("readValue");
            hook(method).intercept(chain -> "HOOKED_PHASE05");
        } catch (ReflectiveOperationException error) {
            log("MMRL_HOOK_LAB_HOOK_REGISTRATION_FAILED: " + error.getClass().getSimpleName());
        }
    }
}
