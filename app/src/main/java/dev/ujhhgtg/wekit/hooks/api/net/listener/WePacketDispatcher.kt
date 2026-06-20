package dev.ujhhgtg.wekit.hooks.api.net.listener

import android.os.Handler
import android.os.Looper
import androidx.core.os.postDelayed
import com.tencent.kinda.framework.module.impl.WXPCommReqResp
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.hooks.api.net.WePacketHelper
import dev.ujhhgtg.wekit.hooks.api.net.WePacketManager
import dev.ujhhgtg.wekit.hooks.core.ApiHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.reflection.ClassLoaders
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

@HookItem(name = "数据包拦截与篡改服务", categories = ["API"], description = "响应数据包拦截与篡改")
object WePacketDispatcher : ApiHookItem(), IResolveDex {

    private val TAG = This.Class.simpleName

    private val classOnGYNetEnd by dexClass {
        searchPackages("com.tencent.mm.network")
        matcher {
            methodCount(1)
            methods {
                add {
                    name = "onGYNetEnd"
                    paramCount = 6
                }
            }
        }
    }

    private val recentRequests = ConcurrentHashMap<String, Long>()

    override fun onEnable() {
        Handler(Looper.getMainLooper()).postDelayed(3000) {
            try {
                val netSceneBaseClass = WePacketHelper.classNetSceneBase.clazz
                val callbackInterface = classOnGYNetEnd.clazz

                netSceneBaseClass.reflekt().firstMethod { name = "dispatch" }.hookBefore {
                    val v0Var = args[1] ?: return@hookBefore
                    val originalCallback = args[2] ?: return@hookBefore

                    // 有时 getUri 返回 null
                    val v0Ref = v0Var.reflekt()
                    val uri = v0Ref.invokeMethod("getUri") as? String? ?: "null"
                    val cgiId = v0Ref.invokeMethod("getType") as Int
                    try {
                        val reqWrapper = v0Ref.invokeMethod("getReqObj")!!
                        val reqPbObj = reqWrapper.reflekt().getField("a")!!
                        val reqBytes = reqPbObj.reflekt().invokeMethod("toByteArray")!! as ByteArray

                        // 构造唯一标识符
                        val key =
                            "$cgiId|$uri|${reqWrapper.javaClass.name}|${reqPbObj.javaClass.name}|${reqBytes.contentToString()}"

                        // 检查是否在缓存中且时间间隔小于500毫秒
                        val currentTime = System.currentTimeMillis()
                        val lastTime = recentRequests[key]
                        if (lastTime != null && currentTime - lastTime < 500) {
                            // 直接返回，不执行任何请求处理
                            WeLogger.d(TAG, "request skipped (duplicate): $uri")
                            return@hookBefore
                        }

                        // 更新缓存
                        recentRequests[key] = currentTime
                        // 限制缓存大小为10条
                        if (recentRequests.size > 10) {
                            // 移除最旧的条目
                            val oldestEntry = recentRequests.entries.firstOrNull()
                            oldestEntry?.let {
                                recentRequests.remove(it.key)
                            }
                        }

                        WePacketManager.handleRequestTamper(uri, cgiId, reqBytes)?.let { tampered ->
                            reqPbObj.reflekt().invokeMethod("parseFrom", tampered)
                            WeLogger.i(TAG, "tampered request: $uri")
                        }
                    } catch (_: Throwable) {
                    }

                    if (Proxy.isProxyClass(originalCallback.javaClass)) return@hookBefore

                    args[2] = Proxy.newProxyInstance(
                        ClassLoaders.HOST,
                        arrayOf(callbackInterface)
                    ) { _, method, args ->
                        when (method.name) {
                            "hashCode" -> return@newProxyInstance originalCallback.hashCode()
                            "toString" -> return@newProxyInstance originalCallback.toString()
                            "equals" -> return@newProxyInstance originalCallback == args?.get(0)
                            "onGYNetEnd" -> {
                                try {
                                    val respV0 = args!![4] ?: v0Var
                                    val respV0Ref = respV0.reflekt()

                                    // 处理 Kinda 框架的 WXPCommReqResp
                                    if (respV0 is WXPCommReqResp) {
                                        val originalRespBytes = respV0.wxpRespData
                                        if (originalRespBytes != null) {
                                            WePacketManager.handleResponseTamper(
                                                uri,
                                                cgiId,
                                                originalRespBytes
                                            )?.let { tampered ->
                                                respV0Ref.invokeMethod("setWXPRespData", tampered)
                                                WeLogger.i(
                                                    TAG,
                                                    "tampered response (WXP): $uri"
                                                )
                                            }
                                        }
                                    }
                                    // 处理标准混淆的 ICommReqResp 实现
                                    else {
                                        val respWrapper = try {
                                            respV0Ref.getField("b")
                                        } catch (_: NoSuchFieldError) {
                                            respV0Ref.invokeMethod("getRespObj")
                                        }

                                        if (respWrapper != null) {
                                            val respPbObj = runCatching {
                                                respWrapper.reflekt().getField("a")
                                            }.getOrNull()


                                            if (respPbObj != null) {
                                                val respPbObjRef = respPbObj.reflekt()

                                                try {
                                                    val originalRespBytes = respPbObjRef
                                                        .invokeMethod("toByteArray")!! as ByteArray
                                                    WePacketManager.handleResponseTamper(
                                                        uri,
                                                        cgiId,
                                                        originalRespBytes
                                                    )?.let { tampered ->
                                                        respPbObjRef.invokeMethod("parseFrom", tampered)
                                                        WeLogger.i(
                                                            TAG,
                                                            "tampered response (PB): $uri"
                                                        )
                                                    }
                                                } catch (_: NoSuchElementException) {
                                                } catch (_: NoSuchMethodException) {
                                                }
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    WeLogger.e(TAG, "failed to tamper inner logic", t)
                                }
                            }
                        }

                        return@newProxyInstance method.invoke(
                            originalCallback,
                            *(args ?: emptyArray())
                        )
                    }
                }
            } catch (ex: IllegalStateException) {
                WeLogger.w(TAG, "exception occurred during entry", ex)
            }
        }
    }
}
