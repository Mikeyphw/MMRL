package com.dergoogler.mmrl.utils

import android.content.Intent
import android.os.IBinder
import com.dergoogler.mmrl.platform.service.ServiceManager
import com.dergoogler.mmrl.platform.util.Shell.exec
import com.topjohnwu.superuser.ipc.RootService

class SuService : RootService() {
    override fun onBind(intent: Intent): IBinder {
        // Detect inside the privileged process. The requested working mode is not evidence.
        val detected = RootPlatformDetector.fromSuVersion("su -v".exec().getOrNull())
        return ServiceManager(detected)
    }
}
