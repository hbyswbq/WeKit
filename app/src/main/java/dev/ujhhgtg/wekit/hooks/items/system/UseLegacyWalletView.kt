package dev.ujhhgtg.wekit.hooks.items.system

import com.tencent.mm.ui.base.preference.Preference
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.hooks.core.SwitchHookItem
import org.luckypray.dexkit.DexKitBridge

@HookItem(name = "恢复旧版卡包界面", categories = ["系统与隐私"], description = "使用旧版「卡包」而非「小店与卡包」")
object UseLegacyWalletView : SwitchHookItem(), IResolveDex {

    override fun onEnable() {
        methodGetOrderAndCardEntranceInfo.hookAfter {
            result.reflekt()
                .firstField {
                    type = Int::class.java
                }.set(1)
        }

        methodMoreTabUIHandlePrefOnClick.hookBefore {
            val field = Preference::class.reflekt()
                .firstField { type = String::class }

            val pref = args[1] as Preference
            if (field.get(pref) as? String? == "settings_mm_cardpackage_new") {
                field.set(pref, "settings_mm_cardpackage")
            }
        }
    }

    private val methodGetOrderAndCardEntranceInfo by dexMethod()

    private val methodMoreTabUIHandlePrefOnClick by dexMethod()

    override fun resolveDex(dexKit: DexKitBridge) {
        methodGetOrderAndCardEntranceInfo.find(dexKit) {
            matcher {
                usingEqStrings("MicroMsg.EcsOrderService", "getOrderAndCardEntranceInfo use finder logic")
            }
        }

        methodMoreTabUIHandlePrefOnClick.find(dexKit) {
            matcher {
                usingEqStrings("MicroMsg.MoreTabUI", "account has not already!", "onPreferenceTreeClick")
            }
        }
    }
}
