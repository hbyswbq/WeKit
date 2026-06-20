package dev.ujhhgtg.wekit.hooks.items.system

import android.app.Activity
import dev.ujhhgtg.reflekt.utils.toClass
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.hooks.core.SwitchHookItem
import dev.ujhhgtg.reflekt.reflekt
import org.luckypray.dexkit.DexKitBridge

@HookItem(name = "禁用存储空间不足检测", categories = ["系统与隐私"], description = "「隐藏应用列表」等隐藏 Root 模块有时会使应用获取到的可用空间不正确, 而微信在可用空间不足时会强制要求清理空间才可继续使用, 本功能移除了该限制")
object DisableLowAvailableStorageDetection : SwitchHookItem(), IResolveDex {

    private val methodSplashActivitySplashFinished by dexMethod()
    private val classStaticValuesHolder by dexClass()

    override fun onEnable() {
        methodSplashActivitySplashFinished.hookBefore {
            classStaticValuesHolder.clazz.reflekt()
                .firstField { type = Boolean::class }
                .setStatic(false)
        }

        "com.tencent.mm.plugin.clean.ui.fileindexui.StorageDisableAlertUI"
            .toClass().hookAfterOnCreate {
                val activity = thisObject as Activity
                activity.finish()
            }
    }

    override fun resolveDex(dexKit: DexKitBridge) {
        methodSplashActivitySplashFinished.find(dexKit) {
            matcher {
                declaredClass = "com.tencent.mm.splash.SplashActivity"
                usingEqStrings("WxSplash.SplashActivity", "Call splashFinished.")
            }
        }

        classStaticValuesHolder.find(dexKit) {
            matcher {
                usingEqStrings("UIPageFragmentActivity", "LuckyMoneyNewPrepareUI", "RemittanceUI")
            }
        }
    }
}
