package com.dergoogler.mmrl.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.dergoogler.mmrl.R
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput

abstract class TaskerRequestConfigActivity : Activity(), TaskerPluginConfig<TaskerRequestInput> {
    override val context: Context get() = applicationContext

    protected open val showModuleId = false
    protected open val showOperationId = false
    protected open val showUrl = false
    protected open val showFilename = false
    protected open val showForceRefresh = false
    protected open val showReviewToken = false
    protected abstract val screenTitle: String
    protected abstract val screenDescription: String
    protected abstract fun helperOnCreate()
    protected abstract fun helperFinish()

    private lateinit var moduleId: EditText
    private lateinit var operationId: EditText
    private lateinit var url: EditText
    private lateinit var filename: EditText
    private lateinit var forceRefresh: CheckBox
    private lateinit var reviewToken: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        content.addView(TextView(this).apply {
            text = screenTitle
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = screenDescription
            textSize = 15f
            setPadding(0, dp(8), 0, dp(20))
        })
        moduleId = field(content, getString(R.string.tasker_field_module_id), showModuleId)
        operationId = field(content, getString(R.string.tasker_field_operation_id), showOperationId)
        url = field(content, getString(R.string.tasker_field_url), showUrl, InputType.TYPE_TEXT_VARIATION_URI)
        filename = field(content, getString(R.string.tasker_field_filename), showFilename)
        reviewToken = field(content, getString(R.string.tasker_field_review_token), showReviewToken)
        forceRefresh = CheckBox(this).apply {
            text = getString(R.string.tasker_force_refresh_repositories)
            visibility = if (showForceRefresh) android.view.View.VISIBLE else android.view.View.GONE
        }
        content.addView(forceRefresh, matchWrap())
        content.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        content.addView(Button(this).apply {
            text = getString(R.string.tasker_save_action)
            setOnClickListener { helperFinish() }
        }, matchWrap())
        setContentView(ScrollView(this).apply { addView(content, matchMatch()) })
        helperOnCreate()
    }

    override fun assignFromInput(input: TaskerInput<TaskerRequestInput>) {
        input.regular.run {
            this@TaskerRequestConfigActivity.moduleId.setText(moduleId.orEmpty())
            this@TaskerRequestConfigActivity.operationId.setText(operationId.orEmpty())
            this@TaskerRequestConfigActivity.url.setText(url.orEmpty())
            this@TaskerRequestConfigActivity.filename.setText(filename.orEmpty())
            this@TaskerRequestConfigActivity.forceRefresh.isChecked = forceRefresh
            this@TaskerRequestConfigActivity.reviewToken.setText(reviewToken.orEmpty())
        }
    }


    protected fun finishRequiringModuleId(action: () -> Unit) {
        if (moduleId.text?.toString()?.trim().isNullOrEmpty()) {
            moduleId.error = getString(R.string.tasker_error_module_id_required)
            moduleId.requestFocus()
            return
        }
        action()
    }

    protected fun finishRequiringOperationId(action: () -> Unit) {
        if (operationId.text?.toString()?.trim().isNullOrEmpty()) {
            operationId.error = getString(R.string.tasker_error_operation_id_required)
            operationId.requestFocus()
            return
        }
        action()
    }

    protected fun finishRequiringDownloadSource(action: () -> Unit) {
        val module = moduleId.text?.toString()?.trim().orEmpty()
        val directUrl = url.text?.toString()?.trim().orEmpty()
        if (module.isEmpty() && directUrl.isEmpty()) {
            moduleId.error = getString(R.string.tasker_error_download_source_required)
            url.error = getString(R.string.tasker_error_download_source_required)
            moduleId.requestFocus()
            return
        }
        action()
    }


    protected fun finishRequiringModuleOrOperation(action: () -> Unit) {
        val module = moduleId.text?.toString()?.trim().orEmpty()
        val operation = operationId.text?.toString()?.trim().orEmpty()
        if (module.isEmpty() && operation.isEmpty()) {
            moduleId.error = "Module ID or operation ID is required"
            operationId.error = "Module ID or operation ID is required"
            moduleId.requestFocus()
            return
        }
        action()
    }

    protected fun finishRequiringReviewToken(action: () -> Unit) {
        if (reviewToken.text?.toString()?.trim().isNullOrEmpty()) {
            reviewToken.error = getString(R.string.tasker_error_review_token_required)
            reviewToken.requestFocus()
            return
        }
        action()
    }

    override val inputForTasker: TaskerInput<TaskerRequestInput>
        get() = TaskerInput(
            taskerRequestInput(
                moduleId = moduleId.text?.toString()?.trim(),
                operationId = operationId.text?.toString()?.trim(),
                url = url.text?.toString()?.trim(),
                filename = filename.text?.toString()?.trim(),
                forceRefresh = forceRefresh.isChecked,
                reviewToken = reviewToken.text?.toString()?.trim(),
            ),
        )

    private fun field(parent: LinearLayout, hint: String, visible: Boolean, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or type
            visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            setSingleLine(true)
            parent.addView(this, matchWrap())
        }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun matchMatch() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

class GetModuleStatusHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, GetModuleStatusRunner>(config) {
    override val runnerClass = GetModuleStatusRunner::class.java
    override val inputClass = TaskerRequestInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Get MMRL status for ${input.regular.moduleId.orEmpty()}")
    }
}
class GetModuleStatusConfigActivity : TaskerRequestConfigActivity() {
    override val showModuleId = true
    override val screenTitle = "Get module status"
    override val screenDescription = "Return installed state, versions, update status, repository and reboot state. Tasker variables are supported."
    private val helper by lazy { GetModuleStatusHelper(this) }
    override fun helperOnCreate() = helper.onCreate()
    override fun helperFinish() = finishRequiringModuleId { helper.finishForTasker() }
}

class CheckUpdatesHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, CheckUpdatesRunner>(config) {
    override val timeoutSeconds = 180
    override val runnerClass = CheckUpdatesRunner::class.java
    override val inputClass = TaskerRequestInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append(if (input.regular.forceRefresh) "Refresh repositories and check updates" else "Check cached module updates")
    }
}
class CheckUpdatesConfigActivity : TaskerRequestConfigActivity() {
    override val showForceRefresh = true
    override val screenTitle = "Check module updates"
    override val screenDescription = "Compare installed modules with repository versions and return arrays plus JSON."
    private val helper by lazy { CheckUpdatesHelper(this) }
    override fun helperOnCreate() = helper.onCreate()
    override fun helperFinish() { helper.finishForTasker() }
}

class GetOperationResultHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, GetOperationResultRunner>(config) {
    override val runnerClass = GetOperationResultRunner::class.java
    override val inputClass = TaskerRequestInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Get MMRL operation ${input.regular.operationId.orEmpty()}")
    }
}
class GetOperationResultConfigActivity : TaskerRequestConfigActivity() {
    override val showOperationId = true
    override val screenTitle = "Get operation result"
    override val screenDescription = "Read progress, phase, result, error, reboot and rollback state from Activity history."
    private val helper by lazy { GetOperationResultHelper(this) }
    override fun helperOnCreate() = helper.onCreate()
    override fun helperFinish() = finishRequiringOperationId { helper.finishForTasker() }
}

class DownloadModuleHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, DownloadModuleRunner>(config) {
    override val runnerClass = DownloadModuleRunner::class.java
    override val inputClass = TaskerRequestInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Queue MMRL download for ${input.regular.moduleId ?: input.regular.url.orEmpty()}")
    }
}
class DownloadModuleConfigActivity : TaskerRequestConfigActivity() {
    override val showModuleId = true
    override val showUrl = true
    override val showFilename = true
    override val screenTitle = "Download module"
    override val screenDescription = "Provide a repository module ID or direct ZIP URL. The operation is queued and returned immediately."
    private val helper by lazy { DownloadModuleHelper(this) }
    override fun helperOnCreate() = helper.onCreate()
    override fun helperFinish() = finishRequiringDownloadSource { helper.finishForTasker() }
}

class CancelDownloadHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, CancelDownloadRunner>(config) {
    override val runnerClass = CancelDownloadRunner::class.java
    override val inputClass = TaskerRequestInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Cancel MMRL download ${input.regular.operationId.orEmpty()}")
    }
}
class CancelDownloadConfigActivity : TaskerRequestConfigActivity() {
    override val showOperationId = true
    override val screenTitle = "Cancel download"
    override val screenDescription = "Cancel a running queued download using the operation ID returned by MMRL."
    private val helper by lazy { CancelDownloadHelper(this) }
    override fun helperOnCreate() = helper.onCreate()
    override fun helperFinish() = finishRequiringOperationId { helper.finishForTasker() }
}

class ExportOperationLogHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, ExportOperationLogRunner>(config) {
    override val runnerClass = ExportOperationLogRunner::class.java
    override val inputClass = TaskerRequestInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Export MMRL log ${input.regular.operationId.orEmpty()}")
    }
}
class ExportOperationLogConfigActivity : TaskerRequestConfigActivity() {
    override val showOperationId = true
    override val screenTitle = "Export technical log"
    override val screenDescription = "Return a temporary content URI for an operation's complete technical log."
    private val helper by lazy { ExportOperationLogHelper(this) }
    override fun helperOnCreate() = helper.onCreate()
    override fun helperFinish() = finishRequiringOperationId { helper.finishForTasker() }
}

class ListModulesHelper(config: TaskerPluginConfig<TaskerEmptyInput>) :
    TaskerPluginConfigHelper<TaskerEmptyInput, TaskerResultOutput, ListModulesRunner>(config) {
    override val runnerClass = ListModulesRunner::class.java
    override val inputClass = TaskerEmptyInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerEmptyInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("List installed MMRL modules")
    }
}
class ListModulesConfigActivity : Activity(), TaskerPluginConfig<TaskerEmptyInput> {
    override val context: Context get() = applicationContext
    private val helper by lazy { ListModulesHelper(this) }
    override fun assignFromInput(input: TaskerInput<TaskerEmptyInput>) = Unit
    override val inputForTasker get() = TaskerInput(taskerEmptyInput())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper.finishForTasker()
    }
}


class EnableModuleHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, EnableModuleRunner>(config) {
    override val runnerClass = EnableModuleRunner::class.java; override val inputClass = TaskerRequestInput::class.java; override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) { blurbBuilder.append("Enable ${input.regular.moduleId.orEmpty()}") }
}
class EnableModuleConfigActivity : TaskerRequestConfigActivity() {
    override val showModuleId = true; override val screenTitle = "Enable module"; override val screenDescription = "Enable an installed module through MMRL's approval and Activity system."
    private val helper by lazy { EnableModuleHelper(this) }; override fun helperOnCreate() = helper.onCreate(); override fun helperFinish() = finishRequiringModuleId { helper.finishForTasker() }
}
class DisableModuleHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, DisableModuleRunner>(config) {
    override val runnerClass = DisableModuleRunner::class.java; override val inputClass = TaskerRequestInput::class.java; override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) { blurbBuilder.append("Disable ${input.regular.moduleId.orEmpty()}") }
}
class DisableModuleConfigActivity : TaskerRequestConfigActivity() {
    override val showModuleId = true; override val screenTitle = "Disable module"; override val screenDescription = "Disable an installed module and return an Activity operation ID."
    private val helper by lazy { DisableModuleHelper(this) }; override fun helperOnCreate() = helper.onCreate(); override fun helperFinish() = finishRequiringModuleId { helper.finishForTasker() }
}
class RemoveModuleHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, RemoveModuleRunner>(config) {
    override val runnerClass = RemoveModuleRunner::class.java; override val inputClass = TaskerRequestInput::class.java; override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) { blurbBuilder.append("Remove ${input.regular.moduleId.orEmpty()}") }
}
class RemoveModuleConfigActivity : TaskerRequestConfigActivity() {
    override val showModuleId = true; override val screenTitle = "Remove module"; override val screenDescription = "Mark an installed module for removal using MMRL's approval policy."
    private val helper by lazy { RemoveModuleHelper(this) }; override fun helperOnCreate() = helper.onCreate(); override fun helperFinish() = finishRequiringModuleId { helper.finishForTasker() }
}
class RunModuleActionHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, RunModuleActionRunner>(config) {
    override val timeoutSeconds = 180; override val runnerClass = RunModuleActionRunner::class.java; override val inputClass = TaskerRequestInput::class.java; override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) { blurbBuilder.append("Run action for ${input.regular.moduleId.orEmpty()}") }
}
class RunModuleActionConfigActivity : TaskerRequestConfigActivity() {
    override val showModuleId = true; override val screenTitle = "Run module action"; override val screenDescription = "Run only the installed module's predefined action.sh; arbitrary shell is not exposed."
    private val helper by lazy { RunModuleActionHelper(this) }; override fun helperOnCreate() = helper.onCreate(); override fun helperFinish() = finishRequiringModuleId { helper.finishForTasker() }
}
class RestoreModuleHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, RestoreModuleRunner>(config) {
    override val runnerClass = RestoreModuleRunner::class.java; override val inputClass = TaskerRequestInput::class.java; override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) { blurbBuilder.append("Restore previous version from ${input.regular.operationId.orEmpty()}") }
}
class RestoreModuleConfigActivity : TaskerRequestConfigActivity() {
    override val showOperationId = true; override val screenTitle = "Restore previous version"; override val screenDescription = "Restore the retained rollback archive associated with an MMRL Activity operation."
    private val helper by lazy { RestoreModuleHelper(this) }; override fun helperOnCreate() = helper.onCreate(); override fun helperFinish() = finishRequiringOperationId { helper.finishForTasker() }
}
class PrepareReviewedInstallHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, PrepareReviewedInstallRunner>(config) {
    override val timeoutSeconds = 600; override val runnerClass = PrepareReviewedInstallRunner::class.java; override val inputClass = TaskerRequestInput::class.java; override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) { blurbBuilder.append("Prepare reviewed install for ${input.regular.moduleId ?: input.regular.operationId.orEmpty()}") }
}
class PrepareReviewedInstallConfigActivity : TaskerRequestConfigActivity() {
    override val showModuleId = true; override val showOperationId = true; override val screenTitle = "Prepare reviewed install"; override val screenDescription = "Download or reuse a completed download, inspect it, and return a hash-bound 30-minute review token."
    private val helper by lazy { PrepareReviewedInstallHelper(this) }; override fun helperOnCreate() = helper.onCreate()
    override fun helperFinish() = finishRequiringModuleOrOperation { helper.finishForTasker() }
}
class ExecuteReviewedInstallHelper(config: TaskerPluginConfig<TaskerRequestInput>) : TaskerPluginConfigHelper<TaskerRequestInput, TaskerResultOutput, ExecuteReviewedInstallRunner>(config) {
    override val runnerClass = ExecuteReviewedInstallRunner::class.java; override val inputClass = TaskerRequestInput::class.java; override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerRequestInput>, blurbBuilder: StringBuilder) { blurbBuilder.append("Execute reviewed MMRL install") }
}
class ExecuteReviewedInstallConfigActivity : TaskerRequestConfigActivity() {
    override val showReviewToken = true; override val screenTitle = "Execute reviewed install"; override val screenDescription = "Execute a still-valid reviewed archive after MMRL rechecks its hash and safety result."
    private val helper by lazy { ExecuteReviewedInstallHelper(this) }; override fun helperOnCreate() = helper.onCreate(); override fun helperFinish() = finishRequiringReviewToken { helper.finishForTasker() }
}
