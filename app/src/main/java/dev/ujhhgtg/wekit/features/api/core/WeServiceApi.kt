package dev.ujhhgtg.wekit.features.api.core

import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.Modifiers
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.api.core.models.MessageInfo
import dev.ujhhgtg.wekit.features.core.ApiFeature
import dev.ujhhgtg.wekit.features.core.Feature
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

@Feature(name = "微信服务管理服务", categories = ["API"], description = "提供获取并使用微信服务的能力")
object WeServiceApi : ApiFeature(), IResolveDex {

    private val methodServiceManagerGetService by dexMethod {
        matcher {
            modifiers(Modifier.STATIC)
            paramTypes(Class::class.java)
            usingEqStrings("calling getService(...)")
        }
    }
    private val classEmojiFeatureService by dexClass {
        searchPackages("com.tencent.mm.feature.emoji")
        matcher {
            methods {
                add {
                    usingEqStrings("MicroMsg.EmojiFeatureService", "[onAccountInitialized]")
                }
            }
        }
    }
    private val classStorageFeatureService by dexClass {
        searchPackages("com.tencent.mm.plugin.messenger.foundation")
        matcher {
            addMethod {
                returnType {
                    usingEqStrings("PRAGMA table_info( contact_ext )")
                }
            }
            addMethod {
                returnType {
                    usingEqStrings("MicroMsg.MsgInfoStorage", "deleted dirty msg ,count is %d")
                }
            }
            addMethod {
                returnType {
                    usingEqStrings("PRAGMA table_info( rconversation)")
                }
            }
        }
    }
    private val classChatroomService by dexClass {
        matcher {
            usingEqStrings("MicroMsg.ChatroomService", "[isEnableRoomManager]")
        }
    }
    private val classImageInfoStorage by dexClass {
        matcher {
            usingEqStrings("MicroMsg.ImgInfoStorage", "generateMd5: %s, %s")
        }
    }
    private val classDownloadImageService by dexClass {
        matcher {
            usingEqStrings("ModelImage.DownloadImgService", "cancelNetScene reset curTaskInfo (%s %s %s)")
        }
    }
    private val classImageFeatureService by dexClass {
        matcher {
            addFieldForType(classImageInfoStorage.clazz)
            addFieldForType(classDownloadImageService.clazz)
        }
    }
    private val methodApiManagerGetApi by dexMethod {
        searchPackages("com.tencent.mm.ui.chatting.manager")
        matcher {
            usingEqStrings("[get] ", " is not a interface!")
        }
    }
    private val methodMmKernelGetServiceImpl by dexMethod()
    private val methodVideoPathFeatureServiceRestoreMp4Path by dexMethod() // formerly VideoInfoStorage
    val classVideoService by dexClass {
        matcher {
            usingEqStrings("MicroMsg.VideoService", "MicroMsg.SubCoreVideo", "quitVideoSendThread")
        }
    }

    val classApiManager: Class<*> by lazy { methodApiManagerGetApi.method.declaringClass }

    val emojiFeatureService by lazy {
        getServiceByClass(classEmojiFeatureService.clazz)
    }

    val storageFeatureService by lazy {
        getServiceByClass(classStorageFeatureService.clazz)
    }

    val messageInfoStorage by lazy {
        storageFeatureService.reflekt()
            .firstMethod {
                parameterCount = 0
                returnType = WeMessageApi.classMsgInfoStorage.clazz
            }
            .invoke()!!
    }

    val chatroomService by lazy {
        getServiceImplByClass(classChatroomService.clazz.interfaces[0])
    }

    fun getServiceByClass(clazz: Class<*>): Any {
        return methodServiceManagerGetService.method.invoke(null, clazz)!!
    }

    fun getServiceImplByClass(clazz: Class<*>): Any {
        return methodMmKernelGetServiceImpl.method.invoke(null, clazz)!!
    }

    fun getApiByClass(apiManager: Any, clazz: Class<*>): Any {
        return methodApiManagerGetApi.method.invoke(apiManager, clazz.interfaces[0])!!
    }

    val imageInfoStorage by lazy {
        classImageFeatureService.reflekt()
            .firstMethod {
                modifiers { it.contains(Modifiers.STATIC) }
                returnType = classImageInfoStorage.clazz
            }.invokeStatic()!!
    }

    fun getImageMd5FromMsgInfo(msgInfo: MessageInfo): String {
        return imageInfoStorage.reflekt()
            .firstMethod {
                returnType = String::class
                parameters(WeMessageApi.classMsgInfo.clazz)
            }.invoke(msgInfo.instance)!! as String
    }

    val videoPathFeatureService by lazy {
        classVideoService.reflekt()
            .firstMethod {
                modifiers { it.contains(Modifiers.STATIC) }
                returnType = methodVideoPathFeatureServiceRestoreMp4Path.method.declaringClass
            }.invokeStatic()!!
    }

    fun getVideoMp4PathFromMsgInfo(msgInfo: MessageInfo): String {
        return methodVideoPathFeatureServiceRestoreMp4Path.method.invoke(
            videoPathFeatureService, msgInfo.imagePath
        ) as String
    }

    override fun resolveDex(dexKit: DexKitBridge) {
        val classMmKernel = dexKit.findClass {
            matcher {
                usingEqStrings("MicroMsg.MMKernel", "Kernel not null, has initialized.")
            }
        }.single()

        methodMmKernelGetServiceImpl.find(dexKit) {
            matcher {
                declaredClass = classMmKernel.name
                paramTypes(Class::class.java)
            }
        }

        val results = dexKit.findMethod {
            // >= 8.0.61
            matcher {
                usingEqStrings("MicroMsg.C2CVideoPathFeatureService", "success restore file, from ", ".mp4")
            }
        }.ifEmpty {
            // < 8.0.61
            dexKit.findMethod {
                matcher {
                    usingEqStrings("MicroMsg.VideoInfoStorage", "success restore file, from ", ".mp4")
                }
            }
        }
        methodVideoPathFeatureServiceRestoreMp4Path.setDescriptor(results.single())
    }
}
