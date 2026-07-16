package com.brucewang.inputaccessibility

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText

class InputActivity : Activity() {

    companion object {
        private const val TAG = "InputActivity"
        private const val PREFS_NAME = "input_activity_prefs"
        private const val PREF_KEEP_OPEN = "keep_open"
        const val EXTRA_HINT = "extra_hint"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_INPUT_TYPE = "extra_input_type"
        const val EXTRA_IME_OPTIONS = "extra_ime_options"
        const val EXTRA_ALLOW_ENTER_AS_NEWLINE = "extra_allow_enter_as_newline"
        const val EXTRA_BROWSER_SUBMIT_FIELD = "extra_browser_submit_field"
        const val EXTRA_RESHOW_KEYBOARD_ONLY = "extra_reshow_keyboard_only"

        private var targetEditTextNode: AccessibilityNodeInfo? = null
        private var currentInstance: InputActivity? = null
        private var lastSecondaryLaunchAtMs = 0L

        fun start(
            context: Context,
            editTextNode: AccessibilityNodeInfo,
            hint: String?,
            text: String?,
            inputType: Int,
            imeOptions: Int,
            allowEnterAsNewline: Boolean,
            browserSubmitField: Boolean
        ) {
            targetEditTextNode = editTextNode
            val intent = Intent(context, InputActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(EXTRA_HINT, hint)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_INPUT_TYPE, inputType)
                putExtra(EXTRA_IME_OPTIONS, imeOptions)
                putExtra(EXTRA_ALLOW_ENTER_AS_NEWLINE, allowEnterAsNewline)
                putExtra(EXTRA_BROWSER_SUBMIT_FIELD, browserSubmitField)
            }
            currentInstance?.takeIf { !it.isFinishing && !it.isDestroyed && it.isReady() }?.let { activity ->
                activity.runOnUiThread {
                    activity.bindTarget(
                        hint = hint,
                        text = text,
                        inputType = inputType,
                        imeOptions = imeOptions,
                        allowEnterAsNewline = allowEnterAsNewline,
                        browserSubmitField = browserSubmitField
                    )
                    activity.showKeyboard(force = isKeepOpenEnabled(activity))
                }
                launchOnSecondaryDisplay(context, intent)
                return
            }


            // 尝试在第二个屏幕启动Activity
            val displayManager = context.getSystemService(DISPLAY_SERVICE) as DisplayManager
            val displays = displayManager.displays

            // Log.d(TAG, "Available displays: ${displays.size}")
            displays.forEachIndexed { index, display ->
                Log.d(TAG, "Display[$index]: id=${display.displayId}, name=${display.name}, flags=${display.flags}")
            }

            if (displays.size > 1) {
                // 优先选择非默认显示器（通常是副屏）
                val secondaryDisplay = displays.firstOrNull { it.displayId != displays[0].displayId }

                if (secondaryDisplay != null) {
                    Log.d(TAG, "Starting activity on secondary display: id=${secondaryDisplay.displayId}, name=${secondaryDisplay.name}")
                    val options = android.app.ActivityOptions.makeBasic()
                    options.launchDisplayId = secondaryDisplay.displayId
                    try {
                        context.startActivity(intent, options.toBundle())
                        Log.d(TAG, "Activity started successfully on secondary display")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start activity on secondary display", e)
                    }
                    return
                }
            } else {
                // 如果没有第二个屏幕，不启动
                Log.d(TAG, "There is no secondary display available. Activity will not be started.")
            }
        }

        /**
         * 关闭当前的InputActivity实例
         */
        private fun launchOnSecondaryDisplay(context: Context, intent: Intent) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSecondaryLaunchAtMs < 700L) {
                Log.d(TAG, "Skipping duplicate secondary display launch")
                return
            }
            lastSecondaryLaunchAtMs = now

            val displayManager = context.getSystemService(DISPLAY_SERVICE) as DisplayManager
            val displays = displayManager.displays
            displays.forEachIndexed { index, display ->
                Log.d(TAG, "Display[$index]: id=${display.displayId}, name=${display.name}, flags=${display.flags}")
            }

            if (displays.size <= 1) {
                Log.d(TAG, "There is no secondary display available. Activity will not be started.")
                return
            }

            val secondaryDisplay = displays.firstOrNull { it.displayId != displays[0].displayId } ?: return
            Log.d(
                TAG,
                "Bringing activity to secondary display: id=${secondaryDisplay.displayId}, name=${secondaryDisplay.name}"
            )
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = secondaryDisplay.displayId
            }
            try {
                context.startActivity(intent, options.toBundle())
                Log.d(TAG, "Activity brought to secondary display successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bring activity to secondary display", e)
            }
        }

        fun closeCurrentInstance(force: Boolean = false) {
            currentInstance?.let { activity ->
                if (!force && isKeepOpenEnabled(activity)) {
                    Log.d(TAG, "Keeping InputActivity open and detaching target")
                    activity.detachTarget()
                } else {
                    Log.d(TAG, "Closing InputActivity instance")
                    activity.finish()
                    currentInstance = null
                }
            }
        }

        fun getTargetEditText(): AccessibilityNodeInfo? = targetEditTextNode

        fun isKeepOpenEnabled(context: Context): Boolean {
            return context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_KEEP_OPEN, false)
        }
    }

    private lateinit var etFloatingInput: EditText
    private lateinit var btnClose: Button
    private lateinit var cbKeepOpen: CheckBox
    private var textSyncEnabled = true
    private var targetInputType = InputType.TYPE_CLASS_TEXT
    private var submitImeAction = EditorInfo.IME_ACTION_DONE
    private var allowEnterAsNewline = false
    private var browserSubmitField = false
    private var lastSubmitAtMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保存当前实例
        currentInstance = this

        // 记录当前显示器信息
        val currentDisplay = display
        Log.d(TAG, "Activity running on display: id=${currentDisplay?.displayId}, name=${currentDisplay?.name}")

        // 设置窗口属性
        window.apply {

            // 设置软键盘模式 - 自动调整窗口大小以适应软键盘
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        setContentView(R.layout.activity_input)

        etFloatingInput = findViewById(R.id.etFloatingInput)
        btnClose = findViewById(R.id.btnClose)
        cbKeepOpen = findViewById(R.id.cbKeepOpen)

        setupView()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!::etFloatingInput.isInitialized || !::cbKeepOpen.isInitialized) {
            return
        }

        if (intent.getBooleanExtra(EXTRA_RESHOW_KEYBOARD_ONLY, false)) {
            Log.d(TAG, "onNewIntent: reshow keyboard only")
            showKeyboard(force = true)
            return
        }

        bindTarget(
            hint = intent.getStringExtra(EXTRA_HINT),
            text = intent.getStringExtra(EXTRA_TEXT),
            inputType = intent.getIntExtra(EXTRA_INPUT_TYPE, 0),
            imeOptions = intent.getIntExtra(EXTRA_IME_OPTIONS, EditorInfo.IME_ACTION_DONE),
            allowEnterAsNewline = intent.getBooleanExtra(EXTRA_ALLOW_ENTER_AS_NEWLINE, false),
            browserSubmitField = intent.getBooleanExtra(EXTRA_BROWSER_SUBMIT_FIELD, false)
        )
        showKeyboard(force = true)
    }

    private fun setupView() {
        val hint = intent.getStringExtra(EXTRA_HINT)
        val text = intent.getStringExtra(EXTRA_TEXT)
        val inputType = intent.getIntExtra(EXTRA_INPUT_TYPE, 0)
        val imeOptions = intent.getIntExtra(EXTRA_IME_OPTIONS,
            EditorInfo.IME_ACTION_DONE)

        cbKeepOpen.isChecked = isKeepOpenEnabled(this)
        cbKeepOpen.setOnCheckedChangeListener { _, isChecked ->
            setKeepOpenEnabled(isChecked)
        }

        bindTarget(
            hint = hint,
            text = text,
            inputType = inputType,
            imeOptions = imeOptions,
            allowEnterAsNewline = intent.getBooleanExtra(EXTRA_ALLOW_ENTER_AS_NEWLINE, false),
            browserSubmitField = intent.getBooleanExtra(EXTRA_BROWSER_SUBMIT_FIELD, false)
        )

        // 设置输入框属性
        etFloatingInput.apply {
            // 监听文本变化，实时同步到目标EditText
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (textSyncEnabled) {
                        syncTextToTarget(s?.toString() ?: "")
                    }
                }
            })

            // 监听软键盘 action 和实体键盘 Enter。
            // 部分浏览器输入框会把实体 Enter 作为 IME_NULL + KeyEvent 发回来。
            setOnEditorActionListener { _, actionId, event ->
                handleEditorAction(actionId, event)
            }

            setOnKeyListener { _, keyCode, event ->
                if (!isEnterKey(keyCode)) {
                    return@setOnKeyListener false
                }

                if (shouldInsertLocalNewline(event)) {
                    return@setOnKeyListener false
                }

                if (event.action == KeyEvent.ACTION_UP) {
                    triggerTargetAction(submitImeAction)
                }
                true
            }

            // 请求焦点
            requestFocus()
        }

        // 关闭按钮
        btnClose.setOnClickListener {
            closeCurrentInstance(force = true)
        }

        // 延迟显示键盘
        etFloatingInput.postDelayed({
            showKeyboard(force = true)
        }, 100)
    }

    private fun isReady(): Boolean {
        return ::etFloatingInput.isInitialized && ::cbKeepOpen.isInitialized
    }

    private fun bindTarget(
        hint: String?,
        text: String?,
        inputType: Int,
        imeOptions: Int,
        allowEnterAsNewline: Boolean,
        browserSubmitField: Boolean
    ) {
        this.allowEnterAsNewline = allowEnterAsNewline
        this.browserSubmitField = browserSubmitField
        targetInputType = sanitizeInputType(inputType)
        submitImeAction = normalizeImeAction(imeOptions)

        textSyncEnabled = false
        etFloatingInput.apply {
            this.hint = if (!hint.isNullOrEmpty()) hint else getString(R.string.input_hint)
            setText(text.orEmpty())
            setSelection(text.orEmpty().length)

            if (targetInputType != 0) {
                this.inputType = targetInputType
            }

            this.imeOptions = submitImeAction or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setSingleLine(!allowEnterAsNewline)
            maxLines = if (allowEnterAsNewline) Int.MAX_VALUE else 1
        }
        textSyncEnabled = true
    }

    private fun detachTarget() {
        targetEditTextNode = null
        if (!::etFloatingInput.isInitialized) {
            return
        }
        textSyncEnabled = false
        etFloatingInput.apply {
            hint = "等待主屏输入框..."
            setText("")
            setSelection(0)
        }
        textSyncEnabled = true
        showKeyboard(force = true)
    }

    private fun setKeepOpenEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_KEEP_OPEN, enabled)
            .apply()
    }

    private fun showKeyboard(force: Boolean = false) {
        if (!::etFloatingInput.isInitialized) {
            return
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.decorView.requestFocus()
        etFloatingInput.requestFocus()
        etFloatingInput.post {
            etFloatingInput.requestFocus()
            val flag = if (force) InputMethodManager.SHOW_FORCED else InputMethodManager.SHOW_IMPLICIT
            imm.showSoftInput(etFloatingInput, flag)
            if (force && isKeepOpenEnabled(this)) {
                reclaimKeyboardFocusFromTarget()
            }
        }
    }

    private fun keepKeyboardVisibleAfterSubmit() {
        if (!isKeepOpenEnabled(this) || !::etFloatingInput.isInitialized) {
            return
        }

        val retryDelays = longArrayOf(0L, 80L, 180L, 360L, 700L, 1200L, 2000L, 3200L)
        retryDelays.forEach { delayMs ->
            etFloatingInput.postDelayed({
                if (!isFinishing && !isDestroyed && ::etFloatingInput.isInitialized) {
                    Log.d(TAG, "Keeping keyboard visible after submit: delayMs=$delayMs")
                    bringToFrontForKeyboard()
                    showKeyboard(force = true)
                }
            }, delayMs)
        }
    }

    private fun bringToFrontForKeyboard() {
        val intent = Intent(this, InputActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(EXTRA_RESHOW_KEYBOARD_ONLY, true)
        }
        launchOnSecondaryDisplay(this, intent)
    }

    private fun reclaimKeyboardFocusFromTarget() {
        val node = targetEditTextNode ?: return
        etFloatingInput.postDelayed({
            try {
                InputAccessibilityService.suppressFocusLossDetach()
                if (!isSupportedBrowserPackage(node.packageName?.toString())) {
                    val cleared = node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
                    Log.d(TAG, "Cleared target focus for persistent keyboard: $cleared")
                } else {
                    Log.d(TAG, "Keeping browser target focus for text sync and Enter")
                }
                etFloatingInput.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etFloatingInput, InputMethodManager.SHOW_FORCED)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reclaim keyboard focus", e)
            }
        }, 120)
    }

    /**
     * 实时同步文本到目标 EditText
     */
    private fun syncTextToTarget(text: String) {
        targetEditTextNode?.let { node ->
            try {
                val refreshed = node.refresh()
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                var setTextSuccess = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                val browserTarget = isSupportedBrowserPackage(node.packageName?.toString())
                if (!setTextSuccess && browserTarget) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    node.refresh()
                    setTextSuccess = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    showKeyboard(force = true)
                }
                val selectionSuccess = if (setTextSuccess) {
                    setTargetSelection(node, text.length)
                } else {
                    false
                }
                Log.d(
                    TAG,
                    "Sync text to target: refreshed=$refreshed, setText=$setTextSuccess, " +
                            "selection=$selectionSuccess, length=${text.length}, " +
                            "browserTarget=$browserTarget, browserSubmitField=$browserSubmitField"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync text to target", e)
            }
        }
    }

    /**
     * 触发目标 EditText 的 EditorAction (Enter 键操作)
     */
    private fun triggerTargetAction(actionId: Int) {
        targetEditTextNode?.let { node ->
            try {
                if (!acceptSubmitEvent()) {
                    Log.d(TAG, "Ignoring duplicate Enter submit")
                    return
                }

                val normalizedAction = normalizeImeAction(actionId)
                Log.d(TAG, "Triggering target action for IME action: ${imeActionName(normalizedAction)}")

                // 先把最新文字推到目标框，避免 Enter 比最后一次 TextWatcher 同步更早执行。
                if (isSupportedBrowserPackage(node.packageName?.toString()) && !browserSubmitField) {
                    focusBrowserTargetForEnter(node)
                }
                syncTextToTarget(etFloatingInput.text?.toString() ?: "")

                val refreshedBeforeAction = node.refresh()
                Log.d(
                    TAG,
                    "Target before Enter: refreshed=$refreshedBeforeAction, " +
                            "package=${node.packageName}, class=${node.className}, " +
                            "editable=${node.isEditable}, focused=${node.isFocused}, " +
                            "actions=${describeActions(node)}"
                )

                if (browserSubmitField) {
                    triggerBrowserSubmit(node)
                    return
                }

                var actionPerformed = performImeEnter(node)

                if (!actionPerformed && !browserSubmitField) {
                    Log.d(TAG, "IME enter failed; refocusing target and retrying")
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    node.refresh()
                    actionPerformed = performImeEnter(node)
                }

                if (!actionPerformed && allowEnterAsNewline) {
                    actionPerformed = appendNewlineToTarget()
                    Log.d(TAG, "Fallback newline sync result: $actionPerformed")
                }

                Log.d(TAG, "Final Enter action result: $actionPerformed")

                // Browser address bars often keep focus after navigation; auto-finishing here can look like a crash
                // or trigger an immediate Accessibility relaunch loop. Let the user close it explicitly.
                if (isKeepOpenEnabled(this)) {
                    keepKeyboardVisibleAfterSubmit()
                } else if (!browserSubmitField && actionPerformed &&
                    (normalizedAction == EditorInfo.IME_ACTION_SEARCH ||
                            normalizedAction == EditorInfo.IME_ACTION_SEND ||
                            normalizedAction == EditorInfo.IME_ACTION_GO)) {
                    etFloatingInput.postDelayed({
                        finish()
                    }, 200)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger target action", e)
            }
        }
    }

    private fun triggerBrowserSubmit(node: AccessibilityNodeInfo) {
        try {
            val rawText = etFloatingInput.text?.toString()?.trim().orEmpty()
            val browserPackageName = node.packageName?.toString()
            val launched = launchBrowserNavigation(rawText, browserPackageName)
            Log.d(TAG, "Browser intent submit result: $launched, package=$browserPackageName, text=$rawText")
            keepKeyboardVisibleAfterSubmit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare browser submit", e)
        }
    }

    private fun acceptSubmitEvent(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSubmitAtMs < 800L) {
            return false
        }
        lastSubmitAtMs = now
        return true
    }

    private fun launchBrowserNavigation(rawText: String, browserPackageName: String?): Boolean {
        if (rawText.isBlank()) {
            return false
        }

        val uri = buildBrowserUri(rawText)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isSupportedBrowserPackage(browserPackageName)) {
                setPackage(browserPackageName)
            }
        }

        return try {
            InputAccessibilityService.suppressBrowserFocusForNavigation()
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = 0
            }
            startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch browser on display 0, retrying without display option", e)
            try {
                startActivity(intent)
                true
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Failed to launch browser navigation", fallbackError)
                false
            }
        }
    }

    private fun buildBrowserUri(rawText: String): Uri {
        val value = rawText.trim()
        return when {
            hasUriScheme(value) -> Uri.parse(value)
            looksLikeWebAddress(value) -> Uri.parse("https://$value")
            else -> Uri.parse("https://www.google.com/search?q=${Uri.encode(value)}")
        }
    }

    private fun hasUriScheme(value: String): Boolean {
        return value.contains("://")
    }

    private fun looksLikeWebAddress(value: String): Boolean {
        val lower = value.lowercase()
        return lower == "localhost" ||
                lower.startsWith("localhost:") ||
                lower.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}(:\d+)?(/.*)?$""")) ||
                (lower.contains(".") && !lower.contains(" "))
    }

    private fun isSupportedBrowserPackage(packageName: String?): Boolean {
        return packageName == "com.android.chrome" ||
                packageName == "com.google.android.apps.chrome" ||
                packageName == "com.chrome" ||
                packageName == "com.microsoft.emmx" ||
                packageName == "com.microsoft.emmx.canary" ||
                packageName == "com.microsoft.emmx.dev" ||
                packageName == "com.microsoft.emmx.beta"
    }

    private fun handleEditorAction(actionId: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            if (!isEnterKey(event.keyCode)) {
                return false
            }
            if (shouldInsertLocalNewline(event)) {
                return false
            }
            if (event.action == KeyEvent.ACTION_UP) {
                triggerTargetAction(resolveActionId(actionId))
            }
            return true
        }

        val resolvedAction = resolveActionId(actionId)
        triggerTargetAction(resolvedAction)
        return true
    }

    private fun isEnterKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    }

    private fun shouldInsertLocalNewline(event: KeyEvent): Boolean {
        return allowEnterAsNewline && event.isShiftPressed
    }

    private fun sanitizeInputType(inputType: Int): Int {
        val fallbackInputType = if (inputType != 0) inputType else InputType.TYPE_CLASS_TEXT
        return if (allowEnterAsNewline) {
            fallbackInputType
        } else {
            fallbackInputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE.inv()
        }
    }

    private fun resolveActionId(actionId: Int): Int {
        return if (actionId == EditorInfo.IME_NULL) {
            submitImeAction
        } else {
            normalizeImeAction(actionId)
        }
    }

    private fun normalizeImeAction(actionId: Int): Int {
        val maskedAction = actionId and EditorInfo.IME_MASK_ACTION
        return when (maskedAction) {
            EditorInfo.IME_ACTION_UNSPECIFIED,
            EditorInfo.IME_ACTION_NONE,
            EditorInfo.IME_NULL -> EditorInfo.IME_ACTION_DONE
            else -> maskedAction
        }
    }

    private fun performImeEnter(node: AccessibilityNodeInfo): Boolean {
        val actionId = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        val hasAction = node.actionList?.any { it.id == actionId } == true
        val performed = node.performAction(actionId)
        Log.d(TAG, "Performed ACTION_IME_ENTER: hasAction=$hasAction, success=$performed")
        return performed
    }

    private fun focusBrowserTargetForEnter(node: AccessibilityNodeInfo): Boolean {
        node.refresh()
        if (node.isFocused) {
            return true
        }

        val focusAction = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.refresh()
        val clickAction = if (!node.isFocused && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            false
        }
        node.refresh()
        Log.d(
            TAG,
            "Focused browser field before Enter: focusAction=$focusAction, " +
                    "clickAction=$clickAction, focused=${node.isFocused}"
        )
        return node.isFocused
    }

    private fun setTargetSelection(node: AccessibilityNodeInfo, position: Int): Boolean {
        val selectionArguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, position)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, position)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArguments)
    }

    private fun appendNewlineToTarget(): Boolean {
        val textWithNewline = "${etFloatingInput.text ?: ""}\n"
        etFloatingInput.setText(textWithNewline)
        etFloatingInput.setSelection(textWithNewline.length)
        syncTextToTarget(textWithNewline)
        return true
    }

    private fun describeActions(node: AccessibilityNodeInfo): String {
        return node.actionList
            ?.joinToString(separator = ",") { "${it.id}:${it.label ?: actionName(it.id)}" }
            ?: "null"
    }

    private fun actionName(actionId: Int): String {
        return when (actionId) {
            AccessibilityNodeInfo.ACTION_FOCUS -> "FOCUS"
            AccessibilityNodeInfo.ACTION_CLEAR_FOCUS -> "CLEAR_FOCUS"
            AccessibilityNodeInfo.ACTION_SELECT -> "SELECT"
            AccessibilityNodeInfo.ACTION_CLEAR_SELECTION -> "CLEAR_SELECTION"
            AccessibilityNodeInfo.ACTION_CLICK -> "CLICK"
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> "LONG_CLICK"
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> "ACCESSIBILITY_FOCUS"
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> "CLEAR_ACCESSIBILITY_FOCUS"
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "NEXT_AT_GRANULARITY"
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "PREVIOUS_AT_GRANULARITY"
            AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT -> "NEXT_HTML_ELEMENT"
            AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT -> "PREVIOUS_HTML_ELEMENT"
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "SCROLL_FORWARD"
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "SCROLL_BACKWARD"
            AccessibilityNodeInfo.ACTION_COPY -> "COPY"
            AccessibilityNodeInfo.ACTION_PASTE -> "PASTE"
            AccessibilityNodeInfo.ACTION_CUT -> "CUT"
            AccessibilityNodeInfo.ACTION_SET_SELECTION -> "SET_SELECTION"
            AccessibilityNodeInfo.ACTION_EXPAND -> "EXPAND"
            AccessibilityNodeInfo.ACTION_COLLAPSE -> "COLLAPSE"
            AccessibilityNodeInfo.ACTION_DISMISS -> "DISMISS"
            AccessibilityNodeInfo.ACTION_SET_TEXT -> "SET_TEXT"
            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id -> "IME_ENTER"
            else -> "ACTION_$actionId"
        }
    }

    private fun imeActionName(actionId: Int): String {
        return when (actionId) {
            EditorInfo.IME_ACTION_GO -> "GO"
            EditorInfo.IME_ACTION_SEARCH -> "SEARCH"
            EditorInfo.IME_ACTION_SEND -> "SEND"
            EditorInfo.IME_ACTION_NEXT -> "NEXT"
            EditorInfo.IME_ACTION_DONE -> "DONE"
            EditorInfo.IME_ACTION_PREVIOUS -> "PREVIOUS"
            else -> actionId.toString()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== onDestroy() called ===")

        // 清除实例引用
        if (currentInstance == this) {
            currentInstance = null
        }

        targetEditTextNode = null
    }

    override fun onStart() {
        super.onStart()
//        Log.d(TAG, "=== onStart() called ===")
    }

    override fun onResume() {
        super.onResume()
//        Log.d(TAG, "=== onResume() called ===")
    }

    override fun onPause() {
        super.onPause()
//        Log.d(TAG, "=== onPause() called ===")
    }

    override fun onStop() {
        super.onStop()
//        Log.d(TAG, "=== onStop() called ===")
    }
}

