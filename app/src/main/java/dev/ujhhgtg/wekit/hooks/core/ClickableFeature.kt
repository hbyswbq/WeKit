package dev.ujhhgtg.wekit.hooks.core

import android.content.Context
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.utils.TargetProcesses

abstract class ClickableFeature : SwitchFeature() {

    override fun startup() {
        if (!TargetProcesses.isInMain) return
        _isEnabled = WePrefs.getBoolOrFalse(name)
        if (_isEnabled || alwaysRun) enable()
    }

    open val alwaysRun: Boolean = false

    open val noSwitchWidget = false

    abstract fun onClick(context: Context)
}
