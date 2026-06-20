package dev.ujhhgtg.wekit.hooks.api.net

import android.content.Context
import dev.ujhhgtg.reflekt.utils.createInstance
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.hooks.core.ApiHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.utils.reflection.ClassLoaders
import dev.ujhhgtg.reflekt.reflekt
import java.lang.reflect.Proxy

@HookItem(name = "NetScene 服务", categories = ["API"], description = "提供 NetScene 发送能力")
object WeNetSceneApi : ApiHookItem(), IResolveDex {

    fun sendNetScene(netScene: Any) {
        val queue = classMmKernel.clazz.reflekt()
            .firstMethod {
                returnType = methodAddNetSceneToQueue.method.declaringClass
            }.invokeStatic()!!
        methodAddNetSceneToQueue.method.invoke(queue, netScene, 0)
    }

    fun sendWalletNetScene(netScene: Any, context: Context, someBoolean: Boolean, someInt: Int, someInt2: Int, onReceive: (Array<Any>) -> Unit,) {
        val mgrClass = methodWalletNetSceneMgrSendNetScene.method.declaringClass
        val mgr = mgrClass.createInstance(context,
            Proxy.newProxyInstance(
                ClassLoaders.HOST,
                arrayOf(mgrClass.declaredConstructors[0].parameterTypes[1])
            ) { _, _, args ->
                onReceive(args)
            })
        methodWalletNetSceneMgrSendNetScene.method.invoke(mgr,
            netScene, someBoolean, someInt, someInt2)
    }

    private val classMmKernel by dexClass {
        matcher {
            usingEqStrings("MicroMsg.MMKernel", "Kernel not null, has initialized.")
        }
    }

    private val methodAddNetSceneToQueue by dexMethod {
        matcher {
            usingEqStrings("MicroMsg.NetSceneQueue", "forbid in waiting: type=", "forbid in running: type=")
        }
    }

    private val methodWalletNetSceneMgrSendNetScene by dexMethod {
        matcher {
            usingEqStrings("MicroMsg.WalletNetSceneMgr", "this %s isShowProgress %s scene: %s dialogType %s type %s IWxSafePay %s tipDialog showing? %s")
        }
    }
}
