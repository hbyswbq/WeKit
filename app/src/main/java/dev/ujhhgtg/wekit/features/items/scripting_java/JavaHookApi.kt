package dev.ujhhgtg.wekit.features.items.scripting_java

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import dev.ujhhgtg.wekit.features.core.ApiFeature
import dev.ujhhgtg.wekit.features.core.Feature
import java.lang.reflect.Member
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Function

/**
 * Hook API accessible from BeanShell scripts, mirroring WAuxv's hookBefore/hookAfter/hookReplace/unhook.
 *
 * WAuxv original: uses XposedBridge.hookMethod(Member, XC_MethodHook) with Consumer<MethodHookParam> callback.
 * WeKit: delegates directly to XposedBridge.hookMethod for identical behavior.
 */
@Feature(name = "脚本 Hook 服务", categories = ["API"], description = "提供 BeanShell 脚本可用的 Xposed Hook 能力")
object JavaHookApi : ApiFeature() {

    private val hooks = mutableMapOf<String, XC_MethodHook.Unhook>()

    @Suppress("unused")
    fun hookBefore(member: Member, consumer: Consumer<XC_MethodHook.MethodHookParam>): String {
        val unhook = XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                runCatching { consumer.accept(param) }
            }
        })
        val id = UUID.randomUUID().toString()
        hooks[id] = unhook
        return id
    }

    @Suppress("unused")
    fun hookAfter(member: Member, consumer: Consumer<XC_MethodHook.MethodHookParam>): String {
        val unhook = XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching { consumer.accept(param) }
            }
        })
        val id = UUID.randomUUID().toString()
        hooks[id] = unhook
        return id
    }

    @Suppress("unused")
    fun hookReplace(member: Member, function: Function<XC_MethodHook.MethodHookParam, Any?>): String {
        val unhook = XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                runCatching { param.result = function.apply(param) }
            }
        })
        val id = UUID.randomUUID().toString()
        hooks[id] = unhook
        return id
    }

    @Suppress("unused")
    fun unhook(id: String) {
        hooks.remove(id)?.unhook()
    }

    /**
     * Called when a plugin is unloaded — cleans up any remaining hooks.
     */
    fun unhookAll(ids: Collection<String>) {
        for (id in ids) {
            hooks.remove(id)?.unhook()
        }
    }
}
