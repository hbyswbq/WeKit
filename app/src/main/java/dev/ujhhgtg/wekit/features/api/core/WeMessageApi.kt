package dev.ujhhgtg.wekit.features.api.core

import android.annotation.SuppressLint
import android.content.ContentValues
import com.tencent.mm.opensdk.modelmsg.WXFileObject
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.spec.VagueType
import dev.ujhhgtg.reflekt.utils.Modifiers
import dev.ujhhgtg.reflekt.utils.createInstance
import dev.ujhhgtg.reflekt.utils.isBuiltin
import dev.ujhhgtg.reflekt.utils.isStatic
import dev.ujhhgtg.reflekt.utils.makeAccessible
import dev.ujhhgtg.reflekt.utils.toClass
import dev.ujhhgtg.wekit.constants.WeChatVersions
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.dexkit.dsl.dexConstructor
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.api.core.models.MessageInfo
import dev.ujhhgtg.wekit.features.api.net.WeNetSceneApi
import dev.ujhhgtg.wekit.features.core.ApiFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.utils.HostInfo
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.collections.emptyHashSet
import dev.ujhhgtg.wekit.utils.reflection.BBool
import dev.ujhhgtg.wekit.utils.reflection.BInt
import dev.ujhhgtg.wekit.utils.reflection.BString
import dev.ujhhgtg.wekit.utils.reflection.bool
import dev.ujhhgtg.wekit.utils.reflection.int
import dev.ujhhgtg.wekit.utils.serialization.JsonToXmlConverter
import dev.ujhhgtg.wekit.utils.serialization.XmlUtils.extractXmlAttr
import dev.ujhhgtg.wekit.utils.serialization.XmlUtils.extractXmlTag
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.base.AccessFlagsMatcher
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.random.Random


@SuppressLint("DiscouragedApi")
@Feature(name = "消息发送服务", categories = ["API"], description = "提供文本、图片、文件、语音消息发送能力")
object WeMessageApi : ApiFeature(), IResolveDex {

    // -------------------------------------------------------------------------------------
    // 基础消息类
    // -------------------------------------------------------------------------------------
    private val classNetSceneSendMsg by dexClass {
        matcher {
            methods {
                add {
                    paramCount = 1
                    usingStrings("MicroMsg.NetSceneSendMsg", "markMsgFailed for id:%d")
                }
            }
        }
    }
    private val classNetSceneQueue by dexClass {
        searchPackages("com.tencent.mm.modelbase")
        matcher {
            methods {
                add {
                    paramCount = 2
                    usingStrings("worker thread has not been se", "MicroMsg.NetSceneQueue")
                }
            }
        }
    }
    val classNetSceneBase by dexClass {
        matcher {
            usingEqStrings("scene security verification not passed, type=")
        }
    }
    private val classNetSceneObserverOwner by dexClass {
        matcher {
            methods {
                add {
                    paramCount = 4
                    usingStrings("MicroMsg.Mvvm.NetSceneObserverOwner")
                }
            }
        }
    }
    // hi0.j1::d() is identical to xv0.a9::e()
    private val methodGetSendMsgObject by dexMethod(allowMultiple = true) {
        matcher {
            paramCount = 0
            returnType = classNetSceneObserverOwner.getDescriptorString() ?: ""
            modifiers(AccessFlagsMatcher(Modifier.STATIC))
        }
    }
    private val methodPostToQueue by dexMethod {
        searchPackages("com.tencent.mm.modelbase")
        matcher {
            declaredClass = classNetSceneQueue.getDescriptorString() ?: ""
            paramTypes(classNetSceneBase.getDescriptorString() ?: "")
            returnType = "boolean"
            usingNumbers(0)
        }
    }

    // C1391: "PatMsgExtension.ClassPatMsgExtension" — used by sendPat
    private val classPatMsgExtension by dexClass {
        matcher {
            usingEqStrings("MicroMsg.PatMsgExtension")
        }
    }

    // C1353.f4831: NetSceneSendPat — C1350 case 6, ctor 4-param (Pair, String, String, Integer)
    private val ctorNetSceneSendPat by dexConstructor {
        matcher {
            usingEqStrings("MicroMsg.NetSceneSendPat")
            paramCount(4)
        }
    }

    // C1350 case 1+2: C1351.f4829 NetSceneRevokeMsg — 3-param ctor: (f8 msgInfo, String, String)
    private val ctorNetSceneRevokeMsg by dexConstructor {
        searchPackages("com.tencent.mm.modelsimple")
        matcher {
            usingEqStrings("MicroMsg.NetSceneRevokeMsg")
            paramCount(3)
        }
    }

    // C0460 case 11: C1339.f4817 NetSceneSendMsg (location variant) — C2812.m4143("MicroMsg.NetSceneSendMsg", "/cgi-bin/micromsg-bin/newsendmsg")
    private val ctorNetSceneSendMsgLocation by dexConstructor {
        matcher {
            usingEqStrings("MicroMsg.NetSceneSendMsg")
            paramCount(5)
        }
    }

    // C0760 case 2+4: C0662.f2775 ImportMultiVideo — 7-param ctor
    private val ctorImportMultiVideo by dexConstructor {
        searchPackages("com.tencent.mm.pluginsdk.model")
        matcher {
            usingEqStrings("MicroMsg.GetVideoMetadata")
            paramCount(7)
        }
    }

    // C2365: "AppMessage.ClassAppMessage" — card/share message class
    private val classAppMessage by dexClass {
        searchPackages("com.tencent.mm.pluginsdk.model.app")
        matcher {
            usingEqStrings("MicroMsg.AppMessage")
        }
    }

    // C2367: "AppMsgLogic.MethodSendAppMsg" — static send method for AppMessage
    private val methodSendAppMsg by dexMethod {
        matcher {
            usingEqStrings("MicroMsg.AppMsgLogic")
            modifiers(9) // public static
            paramCount(6)
        }
    }
    private val methodShareFile by dexMethod {
        matcher {
            paramTypes(
                "com.tencent.mm.opensdk.modelmsg.WXMediaMessage",
                "java.lang.String",
                "java.lang.String",
                "java.lang.String",
                "int",
                "java.lang.String"
            )
        }
    }
    val classMsgInfo by dexClass {
        searchPackages("com.tencent.mm.storage")
        matcher {
            usingEqStrings("MicroMsg.MsgInfo", "[parseNewXmlSysMsg]")
        }
    }
    val classMsgInfoStorage by dexClass {
        searchPackages("com.tencent.mm.storage")
        matcher {
            usingEqStrings("MicroMsg.MsgInfoStorage", "deleted dirty msg ,count is %d")
        }
    }
    val methodMsgInfoStorageInsertMessage by dexMethod {
        matcher {
            declaredClass(classMsgInfoStorage.clazz)
            usingEqStrings("MsgInfo processAddMsg insert db error")
        }
    }
    val classChattingContext by dexClass {
        matcher {
            usingEqStrings("MicroMsg.ChattingContext", "[notifyDataSetChange]")
        }
    }
    val classChattingDataAdapter by dexClass {
        matcher {
            usingEqStrings(
                "MicroMsg.ChattingDataAdapterV3",
                "[handleMsgChange] isLockNotify:"
            )
        }
    }
    val classTransformChattingComponent by dexClass {
        searchPackages("com.tencent.mm.ui.chatting.component")
        matcher {
            usingEqStrings("MicroMsg.TransformComponent", "[onChattingPause]")
        }
    }
    val methodGetIsTransformed by dexMethod {
        matcher {
            declaredClass(classMsgInfo.clazz)
            usingNumbers(64, 0)
            usingFields {
                add {
                    type = "int"
                }
            }
            returnType = "boolean"
        }
    }

    // -------------------------------------------------------------------------------------
    // 图片发送组件
    // -------------------------------------------------------------------------------------
    private val classMvvmBase by dexClass {
        matcher {
            usingStrings(
                "MicroMsg.Mvvm.MvvmPlugin",
                "onAccountInitialized start"
            )
        }
    }
    private val classImageSender by dexClass()      // 发送逻辑核心
    private val classImageTask by dexClass(allowFailure = true) {
        matcher { usingStrings("msg_raw_img_send") }
    }
    private val classServiceManager by dexClass {
        matcher {
            usingStrings("MicroMsg.ServiceManager")
            methods {
                add {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramCount = 1
                    paramTypes(Class::class.java.name)
                }
            }
        }
    }
    private val classConfigLogic by dexClass {
        matcher {
            usingEqStrings(
                "MicroMsg.ConfigStorageLogic",
                "get userinfo fail"
            )
        }
    }
    private val classImageServiceImpl by dexClass(allowFailure = true) {
        matcher {
            usingStrings("MicroMsg.ImgUpload.MsgImgFeatureService")
            superClass(classMvvmBase.getDescriptorString()!!)
        }
    }
    private val methodImageSendEntry by dexMethod()

    // -------------------------------------------------------------------------------------
    // 语音发送组件
    // -------------------------------------------------------------------------------------
    private val classVoiceParams by dexClass {
        matcher {
            usingEqStrings("toUserName", "fileName", "send_voice_msg")
        }
    }
    private val classVoiceTask by dexClass {
        matcher {
            usingStrings("MicroMsg.VoiceMsg.VoiceMsgSendTask")
            methods {
                add {
                    name = "<init>"
                    paramTypes(classVoiceParams.clazz)
                }
            }
        }
    }
    private val classVoiceNameGen by dexClass {
        matcher {
            usingStrings("CREATE TABLE IF NOT EXISTS voiceinfo ( FileName TEXT PRIMARY KEY")
        }
    }
    private val classVfs by dexClass {
        matcher {
            usingStrings("MicroMsg.VFSFileOp", "Cannot resolve path or URI")
        }
    }
    private val classPathUtil by dexClass {
        searchPackages("com.tencent.mm.sdk.platformtools")
        matcher {
            methods {
                add {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = "java.lang.String"
                    paramTypes(
                        "java.lang.String",
                        "java.lang.String",
                        "java.lang.String",
                        "java.lang.String",
                        "int"
                    )
                }
            }
        }
    }
    private val classMmKernel by dexClass {
        matcher {
            usingStrings("MicroMsg.MMKernel", "Initialize skeleton")
        }
    }
    private val methodMmKernelGetStorage by dexMethod(allowMultiple = true) {
        matcher {
            declaredClass(classMmKernel.clazz)
            modifiers = Modifier.PUBLIC or Modifier.STATIC
            paramCount = 0
            usingStrings("mCoreStorage not initialized!")
        }
    }
    private val classVoiceLogic by dexClass {
        matcher {
            usingEqStrings("MicroMsg.VoiceLogic", "startRecord insert voicestg success")
        }
    }
    private val methodGetAmrFullPath by dexMethod {
        matcher {
            usingEqStrings("getAmrFullPath cost: ")
        }
    }
    private val methodStartRecvAndSend by dexMethod {
        matcher {
            usingEqStrings("MicroMsg.SceneVoiceService", "Start Recv[%s] :%s", "Start Send :")
        }
    }
    private val classSceneVoiceService by dexClass {
        matcher {
            usingEqStrings("MicroMsg.SceneVoiceService", "//voicetrymore", "getVoiceService %s")
        }
    }

    private val classVoiceServiceInterface by dexClass()

    private val classVoiceServiceImpl by dexClass {
        matcher {
            usingEqStrings(
                "MicroMsg.VoiceMsgAsyncSendFSC",
                "sendAsync only support BaseSendMsgTask Type"
            )
        }
    }
    private val methodSendVoice by dexMethod(allowMultiple = true) {
        matcher {
            declaredClass(classVoiceServiceImpl.clazz)
            paramCount = 1
            returnType = "void"
        }
    }

    // -------------------------------------------------------------------------------------
    // 运行时缓存
    // -------------------------------------------------------------------------------------

    // 基础 & 文本
    private var getServiceMethod: Method? = null       // ServiceManager.getService
    private lateinit var getSelfAliasMethod: Method

    // 图片
    private lateinit var imageServiceApiClass: Class<*>
    private lateinit var sendImageMethod: Method
    private lateinit var taskConstructor: Constructor<*>
    private lateinit var crossParamsClass: Class<*>

    // 语音 & VFS
    private lateinit var vfsCopyMethod: Method         // VFS.L (write)
    private lateinit var vfsReadMethod: Method         // VFS.F (read)
    private lateinit var vfsExistsMethod: Method       // VFS.k/e (exists)
    private lateinit var voiceNameGenMethod: Method    // g1.E
    private var storageAccPathMethod: Method? = null  // b0.e (动态解析)
    private lateinit var pathGenMethod: Method         // h1.c
    private lateinit var voiceTaskConstructor: Constructor<*>
    private lateinit var voiceDurationField: Field     // 语音时长字段
    private lateinit var voiceOffsetField: Field       // 偏移量字段

    private val TAG = This.Class.simpleName

    @SuppressLint("NonUniqueDexKitData")
    override fun resolveDex(dexKit: DexKitBridge) {
        classImageSender.find(dexKit, allowFailure = true) {
            matcher {
                usingStrings(
                    "MicroMsg.ImgUpload.MsgImgSyncSendFSC",
                    "/cgi-bin/micromsg-bin/uploadmsgimg"
                )
            }
        }

        methodImageSendEntry.find(dexKit) {
            matcher {
                declaredClass(classImageSender.clazz)
                modifiers = Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL
                paramCount(4, 5)
                usingEqStrings("send_mid_size", "send_hevc_mid_size")
            }
        }

        val taskClassName = methodImageSendEntry.method.parameterTypes[1]
        classImageTask.setDescriptor(taskClassName.name)

        if (HostInfo.versionCode >= WeChatVersions.MM_8_0_67 && !HostInfo.isHostGooglePlay ||
            HostInfo.versionCode >= WeChatVersions.MM_8_0_66_PLAY && HostInfo.isHostGooglePlay
        ) {
            methodImgUploadFeatureServiceSendImage.find(dexKit) {
                matcher {
                    declaredClass {
                        usingEqStrings("MicroMsg.ImgUpload.MsgImgFeatureService", "taskListener", "params")
                    }

                    paramCount(1)
                    usingEqStrings("params")
                }
            }

            methodAppInfoSetAppId.find(dexKit) {
                matcher {
                    declaredClass {
                        usingEqStrings("appinfo", "appid", "version", "appname", "isforceupdate", "messageaction", "messageext", "mediatagname")
                    }

                    paramTypes(BString)
                    usingNumbers(0)
                }
            }

            ctorNetSceneUploadMsgImg.setPlaceholderDescriptor()
        } else {
            methodImgUploadFeatureServiceSendImage.setPlaceholderDescriptor()

            methodAppInfoSetAppId.setPlaceholderDescriptor()

            ctorNetSceneUploadMsgImg.find(dexKit) {
                searchPackages("com.tencent.mm.modelimage")
                matcher {
                    name = "<init>"
                    declaredClass {
                        usingEqStrings("MicroMsg.NetSceneUploadMsgImg", "/cgi-bin/micromsg-bin/uploadmsgimg")
                    }
                    paramTypes(int, BString, BString, BString, int, null, int, BString, BString, bool, int)
                }
            }
        }

        val targetInterface = classVoiceServiceImpl.clazz.interfaces.first {
            !it.isBuiltin && !it.name.startsWith("ki0.")
        }
        classVoiceServiceInterface.setDescriptor(targetInterface.name)
    }

    fun convertMsgInfoFromContentValues(contentValues: ContentValues, boolValue: Boolean): Any {
        val msgInfo = classMsgInfo.clazz.createInstance()
        msgInfo.reflekt().firstMethod {
            name = "convertFrom"
            parameters(ContentValues::class, Boolean::class)
            superclass()
        }.invoke(contentValues, boolValue)
        return msgInfo
    }

    fun createSimpleMsgInfoAndInsert(type: Int, talker: String, content: String, currentTime: Long) {
        val values = ContentValues().apply {
            put("msgid", 0)
            put("msgSvrId", currentTime + Random.nextInt())
            put("type", type)
            put("status", 3)
            put("createTime", currentTime)
            put("talker", talker)
            put("content", content)
        }
        val msgInfo = convertMsgInfoFromContentValues(values, true)
        methodMsgInfoStorageInsertMessage.method.invoke(
            WeServiceApi.messageInfoStorage,
            msgInfo
        )
    }

    /**
     * Revoke (unsend) a message by its msgSvrId.
     * Uses WeChat's MsgInfoStorage revoke method found via DexKit.
     */
    fun revokeMsg(msgSvrId: Long): Boolean {
        return try {
            WeLogger.i(TAG, "revoking message: $msgSvrId")
            val storage = WeServiceApi.messageInfoStorage
            val getMsgMethod = storage.reflekt()
                .firstMethod { parameters(Long::class.java) }
                .self
            val f8 = getMsgMethod.invoke(storage, msgSvrId)
            val netScene = ctorNetSceneRevokeMsg.newInstance(f8, "你撤回了一条消息", "")
            WeNetSceneApi.sendNetScene(netScene)
            true
        } catch (e: Exception) { WeLogger.e(TAG, "revokeMsg failed", e); false }
    }

    fun sendQuoteMsg(talker: String, msgSvrId: Long, content: String): Boolean {
        return try {
            WeLogger.i(TAG, "sending quote message to $talker")
            val storage = WeServiceApi.messageInfoStorage
            val getMsgMethod = storage.reflekt()
                .firstMethod { parameters(Long::class.java) }
                .self
            val f8 = getMsgMethod.invoke(storage, msgSvrId)!!
            val mi = MessageInfo(f8)
            val appmsg = JSONObject()
            appmsg.put("type", 57)
            appmsg.put("title", content)
            val refermsg = JSONObject()
            refermsg.put("type", mi.typeCode)
            refermsg.put("svrid", msgSvrId)
            refermsg.put("fromusr", mi.talker)
            refermsg.put("chatusr", mi.talker)
            refermsg.put("displayname", WeDatabaseApi.getDisplayName(mi.talker ?: ""))
            refermsg.put("msgsource", "")
            refermsg.put("content", content)
            refermsg.put("strid", "")
            refermsg.put("createtime", mi.createTime)
            appmsg.put("refermsg", refermsg)
            val outer = JSONObject()
            outer.put("msg", JSONObject().put("appmsg", appmsg))
            val appMsg = classAppMessage.clazz.createInstance(outer.toString())
            methodSendAppMsg.method.invoke(null, appMsg, "", "", talker, "", null)
            true
        } catch (e: Exception) { WeLogger.e(TAG, "sendQuoteMsg failed", e); false }
    }

    fun sendEmoji(toUser: String, md5: String): Boolean {
        return try {
            WeLogger.i(TAG, "sending emoji: $md5 to $toUser")
            val emojiService = WeServiceApi.emojiFeatureService
            val prepared = emojiService.reflekt()
                .firstMethod { parameters(String::class); returnType = String::class }
                .invoke(md5)
            val emojiInfoClazz = "com.tencent.mm.storage.emotion.EmojiInfo".toClass()
            val emojiInfo = emojiInfoClazz.reflekt()
                .firstMethod { parameters(String::class); modifiers { it.contains(Modifier.STATIC) } }
                .invokeStatic(prepared)
            emojiService.reflekt()
                .firstMethod { parameters(String::class, emojiInfoClazz) }
                .invoke(toUser, emojiInfo)
            true
        } catch (e: Exception) { WeLogger.e(TAG, "sendEmoji failed", e); false }
    }

    fun sendPat(toUser: String, patTargetWxId: String): Boolean {
        return try {
            WeLogger.i(TAG, "sending pat to $patTargetWxId in $toUser")
            // Get PatMsgExtension service instance (C1387 ≈ C1104.m2574 → classPatMsgExtension)
            val patService = WeServiceApi.getServiceByClass(classPatMsgExtension.clazz)
            // First reflection: find method returning String with 2 String params → m1650(patTarget, talker)
            val strMethod = patService.reflekt()
                .firstMethod { parameters(String::class, String::class); returnType = String::class }
                .self
            val str11 = strMethod.invoke(patService, patTargetWxId, toUser) as String
            // timestamp = (int)(System.currentTimeMillis() / 1000)
            val timestamp = (System.currentTimeMillis() / 1000).toInt()
            // Second reflection: find method returning Pair with 6 params → m1650(talker, selfWxId, patTarget, str11, timestamp, 0L)
            val pairMethod = patService.reflekt()
                .firstMethod {
                    parameters(String::class, String::class, String::class, String::class, Int::class.java, Long::class.java)
                    returnType = android.util.Pair::class.java
                }
                .self
            val pair = pairMethod.invoke(patService, toUser, WeApi.selfWxId, patTargetWxId, str11, timestamp, 0L)
            // Dispatch via background executor
            val threadUtilsClazz = "com.tencent.threadpool.ThreadUtils".toClass()
            threadUtilsClazz.reflekt()
                .firstMethod { parameters(Runnable::class, String::class) }
                .invokeStatic(Runnable {
                    val netScene = ctorNetSceneSendPat.newInstance(pair, toUser, patTargetWxId, 0)
                    WeNetSceneApi.sendNetScene(netScene)
                }, "sendPat")
            true
        } catch (e: Exception) { WeLogger.e(TAG, "sendPat failed", e); false }
    }

    fun sendLocation(toUser: String, poiName: String, label: String, x: String, y: String, scale: String): Boolean {
        return try {
            WeLogger.i(TAG, "sending location: $x,$y to $toUser")
            val locJson = """{"msg":{"location":{"poiname":"$poiName","label":"$label","x":"$x","y":"$y","scale":"$scale"}}}"""
            val netScene = ctorNetSceneSendMsgLocation.newInstance(toUser, locJson, 1, 0, null)
            WeNetSceneApi.sendNetScene(netScene)
            true
        } catch (e: Exception) { WeLogger.e(TAG, "sendLocation failed", e); false }
    }

    /**
     * Send a contact card.
     * WAuxv m1784: AppMessage class (C2365) → new AppMessage(cardWxId) → static send (C2367).
     */
    fun sendShareCard(toUser: String, cardWxId: String): Boolean {
        return try {
            WeLogger.i(TAG, "sending share card $cardWxId to $toUser")
            val jSONObject = JSONObject()
            val jSONObject2 = JSONObject()
            jSONObject2.put("username", cardWxId)
            val nickname = WeDatabaseApi.getDisplayName(cardWxId)
            jSONObject2.put("nickname", nickname)
            jSONObject2.put("certflag", if (cardWxId.startsWith("gh_")) 4928270286903575946L else 4928270274018674058L)
            jSONObject.put("msg", jSONObject2)
            val netScene = ctorNetSceneSendMsgLocation.newInstance(toUser, jSONObject.toString(), 1, 0, null)
            WeNetSceneApi.sendNetScene(netScene)
            true
        } catch (e: Exception) { WeLogger.e(TAG, "sendShareCard failed", e); false }
    }

    fun sendVideo(toUser: String, videoPath: String): Boolean {
        return try {
            WeLogger.i(TAG, "sending video: $videoPath to $toUser")
            val thread = ctorImportMultiVideo.newInstance(
                HostInfo.application,
                java.util.Collections.singletonList(videoPath),
                null, toUser, 2, null, java.lang.Boolean.TRUE
            ) as Thread
            thread.start()
            true
        } catch (e: Exception) { WeLogger.e(TAG, "sendVideo failed", e); false }
    }

    override fun onEnable() {
        // -----------------------------------------------------------------------------
        // 图片组件初始化
        // -----------------------------------------------------------------------------
        taskConstructor = classImageTask.clazz.reflekt()
            .firstConstructor { parameterCount = 5 }
            .self

        crossParamsClass = taskConstructor.parameterTypes[4]

        // -----------------------------------------------------------------------------
        // 语音/VFS 组件初始化
        // -----------------------------------------------------------------------------

        // VFS
        classVfs.reflekt().apply {
            vfsReadMethod = firstMethod {
                modifiers { it.contains(Modifiers.STATIC) }
                parameters(String::class)
                returnType = InputStream::class
            }.self

            vfsCopyMethod = firstMethod {
                modifiers { it.contains(Modifiers.STATIC) }
                parameters(String::class, Boolean::class)
                returnType = OutputStream::class
            }.self

            vfsExistsMethod = firstMethod {
                modifiers { it.contains(Modifiers.STATIC) }
                parameters(String::class)
                returnType = Boolean::class
            }.self
        }

        pathGenMethod = classPathUtil.reflekt().firstMethod {
            modifiers { it.contains(Modifiers.STATIC) }
            parameters(VagueType, VagueType, VagueType, VagueType, Int::class)
            returnType = String::class
        }.self

        // Voice Components
        voiceNameGenMethod = classVoiceNameGen.reflekt().firstMethod {
            modifiers { it.contains(Modifiers.STATIC) }
            parameters(String::class, VagueType)
            returnType = String::class
        }.self

        classVoiceParams.reflekt().apply {
            val intFields = fields { type = Int::class }
            voiceDurationField = intFields[0].self
            voiceOffsetField = intFields[1].self
        }

        voiceTaskConstructor = classVoiceTask.reflekt()
            .firstConstructor {
                parameters(classVoiceParams.clazz)
            }.self

        getServiceMethod = classServiceManager.reflekt()
            .firstMethod {
                modifiers { it.contains(Modifiers.STATIC) }
                parameters(Class::class)
            }.self

        getSelfAliasMethod = classConfigLogic.reflekt()
            .firstMethod {
                name { it.length <= 2 }
                modifiers { it.contains(Modifiers.STATIC) }
                parameterCount = 0
                returnType = String::class
            }.self

        imageServiceApiClass = classImageServiceImpl.clazz.interfaces.first {
            !it.isBuiltin
        }

        sendImageMethod = classImageServiceImpl.clazz.declaredMethods.first { m ->
            m.parameterCount == 1 &&
                    m.parameterTypes[0] == classImageTask.clazz &&
                    m.returnType.name.contains("flow", ignoreCase = true)
        }

        crossParamsClass = classImageTask.clazz.declaredConstructors
            .first { it.parameterCount == 5 }.parameterTypes[4]
    }

    /**
     * 动态解析 AccPath 获取方法
     */
    private fun getAccPath(): String {
        val storageObj = methodMmKernelGetStorage.method.invoke(null)
            ?: error("Kernel.getStorage() failed (returned null)")

        if (storageAccPathMethod != null) {
            return storageAccPathMethod!!.invoke(storageObj) as String
        }

        WeLogger.i(TAG, "resolving AccPath method... StorageClass=${storageObj.javaClass.name}")

        var currentClass: Class<*>? = storageObj.javaClass
        var scanCount = 0

        // 递归扫描类继承链
        while (currentClass != null && currentClass != Any::class.java) {
            val methods = currentClass.declaredMethods.filter {
                it.parameterCount == 0 && it.returnType == String::class.java
            }
            scanCount += methods.size

            for (m in methods) {
                try {
                    // 排除干扰项
                    if (m.name == "toString") continue

                    m.makeAccessible()
                    val result = m.invoke(storageObj) as? String

                    // 特征校验：包含 "MicroMsg" 且以 "/" 结尾
                    if (result != null && result.contains("MicroMsg") && result.endsWith("/")) {
                        storageAccPathMethod = m
                        WeLogger.i(TAG, "resolved AccPath method: ${m.name}, path: $result")
                        return result
                    }
                } catch (_: Throwable) {
                    // ignore
                }
            }
            // 向上查找父类
            currentClass = currentClass.superclass
        }

        error("failed to resolve AccPath method (scanned $scanCount candidates, StorageClass=${storageObj.javaClass.name})")
    }

    /** 发送图片消息 */
    fun sendImage(toUser: String, imgPath: String): Boolean {
        return try {
            val serviceObj = getServiceMethod?.invoke(null, imageServiceApiClass) ?: return false

            val paramsObj = crossParamsClass.createInstance()
            paramsObj.reflekt()
                .firstField { type = int }
                .set(4)

            val taskObj = classImageTask.clazz.createInstance(
                imgPath,
                0,
                selfCustomWxId,
                toUser,
                paramsObj
            )
            taskObj.reflekt()
                .lastField { type = String::class }
                .set("media_generate_send_img")

            sendImageMethod.invoke(serviceObj, taskObj)

            WeLogger.i(TAG, "sent image message to $toUser")
            true
        } catch (e: Exception) {
            WeLogger.e(TAG, "failed to send image message", e)
            false
        }
    }

    private val methodImgUploadFeatureServiceSendImage by dexMethod()
    private val methodAppInfoSetAppId by dexMethod()
    private val ctorNetSceneUploadMsgImg by dexConstructor()

    fun sendImageByMd5(toUser: String, md5: String, appMsgAppId: String? = null) {
        if (HostInfo.versionCode >= WeChatVersions.MM_8_0_67 && !HostInfo.isHostGooglePlay ||
            HostInfo.versionCode >= WeChatVersions.MM_8_0_66_PLAY && HostInfo.isHostGooglePlay
        ) {
            val sendImageMethod = methodImgUploadFeatureServiceSendImage.method
            val paramsClass = sendImageMethod.parameterTypes[0]
            val crossParamsClass = paramsClass.reflekt()
                .firstField { type { !it.isBuiltin } }.self.type
            val crossParams = crossParamsClass.createInstance()

            if (appMsgAppId != null) {
                val appInfoClass = methodAppInfoSetAppId.method.declaringClass
                val appInfo = appInfoClass.createInstance()
                methodAppInfoSetAppId.method.invoke(appInfo, appMsgAppId)
                crossParams.reflekt()
                    .firstField {
                        type = appInfoClass
                    }.set(appInfo)
            }

            val params = paramsClass.createInstance(md5, 1, WeApi.selfWxId, toUser, crossParams)
            sendImageMethod.invoke(WeServiceApi.getServiceByClass(sendImageMethod.declaringClass), params)
        } else {
            val xml: String?
            val wxId = WeApi.selfWxId
            if (appMsgAppId != null) {
                val json = JSONObject()
                val json2 = JSONObject()
                val json3 = JSONObject()
                json3.put("appid", appMsgAppId)
                json2.put("appinfo", json3)
                json.put("msg", json2)
                val converter = JsonToXmlConverter(json, emptyHashSet(), emptyHashSet())
                xml = converter.toString()
            } else {
                xml = null
            }
            WeNetSceneApi.sendNetScene(
                ctorNetSceneUploadMsgImg.newInstance(4, wxId, toUser, md5, 1, null, 0, xml, "", true, 0)
            )
        }
    }

    /** 发送文本消息 */
    fun sendText(toUser: String, text: String): Boolean {
        return try {
            WeLogger.i(TAG, "sending text message: $text")
            val sendMsgObject = methodGetSendMsgObject.method.invoke(null) ?: return false
            val msgObj = classNetSceneSendMsg.clazz.createInstance(toUser, text, 1, 0, null)
            methodPostToQueue.method.invoke(sendMsgObject, msgObj) as? Boolean ?: false
        } catch (e: Exception) {
            WeLogger.e(TAG, "failed to send text message", e)
            false
        }
    }

    /** 发送文件消息 */
    fun sendFile(talker: String, filePath: String, title: String, appId: String? = null): Boolean {
        return try {
            WeLogger.i(TAG, "sending file message: $filePath")
            val fileObject = WXFileObject()
            fileObject.filePath = filePath
            val mediaMessage = WXMediaMessage()
            mediaMessage.mediaObject = fileObject
            mediaMessage.title = title
            methodShareFile.method.invoke(null, mediaMessage, appId ?: "", "", talker, 2, null)
            true
        } catch (e: Exception) {
            WeLogger.e(TAG, "failed to send file message", e)
            false
        }
    }

    /** 发送私有路径下的语音文件 */
    fun sendVoice(toUser: String, path: String, durationMs: Int): Boolean {
        var succeeded = runCatching {
            // 尝试通过 ServiceManager 获取
            var finalServiceObj: Any? = null
            if (getServiceMethod != null) {
                try {
                    finalServiceObj = getServiceMethod!!.invoke(null, classVoiceServiceInterface.clazz)
                } catch (e: Exception) {
                    WeLogger.e(TAG, "failed to retrieve ServiceManager, trying singleton fallback", e)
                }
            }

            // 尝试单例 Fallback
            if (finalServiceObj == null) {
                val implClass = classVoiceServiceImpl.clazz
                val instanceField = implClass.declaredFields.find {
                    it.name == "INSTANCE" || it.type == implClass
                }
                if (instanceField != null) {
                    instanceField.makeAccessible()
                    finalServiceObj = instanceField.get(null)
                }
            }

            if (finalServiceObj == null) error("failed to retrieve VoiceService instance")

            // 准备文件
            val fileName = voiceNameGenMethod.invoke(null, selfCustomWxId, "amr_") as? String
                ?: error("VoiceName Gen Failed")
            val accPath = getAccPath()
            val voice2Root = if (accPath.endsWith("/")) "${accPath}voice2/" else "$accPath/voice2/"
            val destFullPath =
                pathGenMethod.invoke(null, voice2Root, "msg_", fileName, ".amr", 2) as? String
                    ?: error("Path Gen Failed")

            if (!copyFileViaVfs(path, destFullPath)) return false

            // 构造任务
            val paramsObj = classVoiceParams.clazz.createInstance(toUser, fileName)
            voiceDurationField.set(paramsObj, durationMs)
            voiceOffsetField.set(paramsObj, 0)

            val taskObj = voiceTaskConstructor.newInstance(paramsObj)
                ?: error("failed to construct voice task")

            methodSendVoice.method.invoke(finalServiceObj, taskObj)
            WeLogger.i(TAG, "sent voice (method 1): $fileName")
        }.onFailure { WeLogger.e(TAG, "failed to send voice (method 1)", it) }.isSuccess

        if (succeeded) return true

        succeeded = runCatching {
            val partialPath = classVoiceLogic.reflekt()
                .firstMethod {
                    parameters(BString, BString)
                    returnType = BString
                }
                .invokeStatic(toUser, "amr_") as String
            val mGetAmrFullPath = methodGetAmrFullPath.method
            val fullPath = if (mGetAmrFullPath.isStatic) {
                mGetAmrFullPath.invoke(null, partialPath, true)
            } else {
                mGetAmrFullPath.invoke(WeServiceApi.getServiceByClass(mGetAmrFullPath.declaringClass), null, partialPath, true)
            } as String

            Files.copy(Path(path), Path(fullPath), StandardCopyOption.REPLACE_EXISTING)

            val target = classVoiceLogic.clazz.reflekt()
                .firstMethod {
                    parameters {
                        it[0] == BString && it[1] == BInt && it[2] == BInt
                    }
                    returnType = BBool
                }.self
            if (target.parameterCount == 4) {
                target.invoke(null, fullPath, durationMs, 0, null)
            } else {
                target.invoke(null, fullPath, durationMs, 0)
            }

            val service = classSceneVoiceService.clazz.reflekt()
                .firstMethod {
                    returnType = methodStartRecvAndSend.method.declaringClass
                    modifiers { it.contains(Modifiers.STATIC) }
                }.invokeStatic()!!

            methodStartRecvAndSend.method.invoke(null, service)

            WeLogger.i(TAG, "sent voice (method 2): $fullPath")
        }.onFailure { WeLogger.e(TAG, "failed to send voice (method 2)", it) }.isSuccess

        return succeeded
    }

    fun sendXmlAppMsg(toUser: String, xmlContent: String): Boolean {
        val appId = extractXmlAttr(xmlContent, "appid")
        val title = extractXmlTag(xmlContent, "title")

        WeLogger.d(TAG, "appmsg info: appid=$appId, title=$title")
        return WeAppMsgApi.sendXmlAppMsg(toUser, title, appId, null, null, xmlContent)
    }

    /**
     * 使用微信内部 VFS 引擎进行物理拷贝
     */
    private fun copyFileViaVfs(sourcePath: String, destPath: String): Boolean {
        WeLogger.d(TAG, "VFS Copy: $sourcePath -> $destPath")
        return try {
            val input = vfsReadMethod.invoke(null, sourcePath) as? InputStream
                ?: error("VFS Open Failed for $sourcePath")

            val output = vfsCopyMethod.invoke(null, destPath, false) as? OutputStream
                ?: error("VFS Create Failed for $destPath")

            input.use { i ->
                output.use { o ->
                    i.copyTo(o)
                }
            }

            // 校验
            val exists = vfsExistsMethod.invoke(null, destPath) as? Boolean ?: false
            if (exists) {
                WeLogger.i(TAG, "VFS copy succeeded")
            } else {
                WeLogger.e(TAG, "VFS copy seems successful but actually failed")
            }
            exists
        } catch (e: Exception) {
            WeLogger.e(TAG, "VFS copy failed", e)
            false
        }
    }

    val selfCustomWxId: String
        get() {
            return getSelfAliasMethod.invoke(null) as? String ?: ""
        }

    fun getMsgInfoFromTag(tag: Any): Any {
        val mGetMsgInfo = tag.reflekt()
            .firstMethodOrNull {
                returnType = classMsgInfo.clazz
                parameterCount(0)
                superclass()
            }

        return if (mGetMsgInfo != null) {
            mGetMsgInfo.invoke()!!
        } else {
            tag.reflekt()
                .firstField {
                    type = classMsgInfo.clazz
                    superclass()
                }.get()!!
        }
    }
}
