package com.dergoogler.mmrl.app

import android.content.Context
import android.content.Intent
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ui.activity.CrashHandlerActivity
import dev.dergoogler.mmrl.compat.core.BrickException
import kotlin.system.exitProcess

object AppCrashHandler {
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { launchCrashActivity(appContext, throwable) }
                .onFailure { previous?.uncaughtException(thread, throwable) }
            exitProcess(0)
        }
    }

    private fun launchCrashActivity(context: Context, throwable: Throwable) {
        val intent = Intent(context, CrashHandlerActivity::class.java).apply {
            putExtra("message", throwable.message ?: "Unknown Message")
            if (throwable is BrickException) putExtra("helpMessage", throwable.helpMessage)
            putExtra("stacktrace", formatStackTrace(context, throwable))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }

    private fun formatStackTrace(context: Context, throwable: Throwable, numberOfLines: Int = 88): String {
        val stackTraceElements = throwable.stackTrace.joinToString("\n") { it.toString() }
        return if (throwable.stackTrace.size > numberOfLines) {
            val trimmed = stackTraceElements.lines().take(numberOfLines).joinToString("\n")
            val moreCount = throwable.stackTrace.size - numberOfLines
            context.getString(R.string.stack_trace_truncated, trimmed, moreCount)
        } else {
            stackTraceElements
        }
    }
}
