package dev.ujhhgtg.wekit.features.items.scripting_java

import android.app.NotificationChannel
import android.app.NotificationManager
import bsh.BshMethod
import bsh.NameSpace
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.Modifiers
import dev.ujhhgtg.wekit.features.api.core.WeApi
import dev.ujhhgtg.wekit.features.api.core.WeContactApi
import dev.ujhhgtg.wekit.features.api.core.WeContactLabelApi
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.api.core.WeGroupApi
import dev.ujhhgtg.wekit.features.api.core.WeMessageApi
import dev.ujhhgtg.wekit.features.api.core.models.MessageType
import dev.ujhhgtg.wekit.utils.AudioUtils
import dev.ujhhgtg.wekit.utils.HostInfo
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.getTopMostActivity
import dev.ujhhgtg.wekit.utils.android.showToast
import dev.ujhhgtg.wekit.utils.fs.KnownPaths
import dev.ujhhgtg.wekit.utils.fs.asPath
import dev.ujhhgtg.wekit.utils.reflection.BString
import dev.ujhhgtg.wekit.utils.reflection.ClassLoaders
import dev.ujhhgtg.wekit.utils.reflection.float
import dev.ujhhgtg.wekit.utils.reflection.int
import dev.ujhhgtg.wekit.utils.reflection.long
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Member
import java.util.Properties
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteExisting

object JavaEngine {

    private const val TAG = "JavaEngine"
    private const val WA_MODULE_VER = 1418

    @Volatile
    var currentTargetTalker: String = ""
        private set

    @Volatile
    private var targetTalkerHooked = false

    private fun ensureTargetTalkerTracked() {
        if (targetTalkerHooked) return
        targetTalkerHooked = true
        try {
            XposedBridge.hookAllMethods(
                ClassLoaders.HOST.loadClass("com.tencent.mm.pluginsdk.ui.chat.ChatFooter"),
                "setUserName",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val talker = param.args.firstOrNull() as? String
                        if (talker != null) {
                            currentTargetTalker = talker
                        }
                    }
                })
        } catch (e: Exception) {
            WeLogger.w(TAG, "failed to hook ChatFooter.setUserName for targetTalker tracking", e)
        }
    }

    fun executeAllOnLoad(scripts: Map<String, JavaPlugin>) {
        ensureTargetTalkerTracked()
        for ((name, plugin) in scripts) {
            try {
                initPlugin(plugin)
                plugin.interpreter.eval(plugin.content)
                WeLogger.d(TAG, "executed plugin '$name'")
            } catch (e: Exception) {
                WeLogger.e(TAG, "failed to execute plugin '$name'", e)
            }
        }
    }

    fun initPlugin(plugin: JavaPlugin) {
        val interpreter = plugin.interpreter

        val classManager = interpreter.classManager
        classManager.setClassLoader(ClassLoaders.MODULE)

        val nameSpace = interpreter.nameSpace
        initNameSpace(nameSpace, plugin)
    }

    fun initNameSpace(nameSpace: NameSpace, plugin: JavaPlugin) {
        nameSpace.apply {
            // ===== Basic Info =====

            setVariable("hostContext", HostInfo.application, true)
            setVariable("hostVerName", HostInfo.versionName, true)
            setVariable("hostVerCode", HostInfo.versionCode.toInt(), true)
            setVariable("hostVerClient", com.tencent.mm.boot.BuildConfig.CLIENT_VERSION_ARM64, true)
            setVariable("moduleVer", WA_MODULE_VER, true)
            setVariable("cacheDir", KnownPaths.moduleCache.absolutePathString(), true)
            setVariable("pluginDir", plugin.dir.absolutePathString(), true)
            setVariable("pluginId", plugin.name, true)
            setVariable("pluginName", plugin.info.name, true)
            setVariable("pluginAuthor", plugin.info.author, true)
            setVariable("pluginVersion", plugin.info.version, true)
            setVariable("pluginUpdateTime", plugin.info.updateTime, true)

            // ===== Audio Utils =====

            setMethod(BshMethod(
                "mp3ToSilk", arrayOf(BString, BString, int)
            ) {
                AudioUtils.mp3ToSilk(it[0] as String, it[1] as String)
            })
            setMethod(BshMethod(
                "mp3ToSilk", arrayOf(BString, BString)
            ) {
                AudioUtils.mp3ToSilk(it[0] as String, it[1] as String)
            })
            setMethod(BshMethod(
                "silkToMp3", arrayOf(BString, BString, int)
            ) {
                val pcm = it[1] as String + ".tmp"
                AudioUtils.silkToPcm(it[0] as String, pcm)
                AudioUtils.pcmToMp3(pcm, it[1] as String)
                runCatching { pcm.asPath.deleteExisting() }
            })
            setMethod(BshMethod(
                "silkToMp3", arrayOf(BString, BString)
            ) {
                val pcm = it[1] as String + ".tmp"
                AudioUtils.silkToPcm(it[0] as String, pcm)
                AudioUtils.pcmToMp3(pcm, it[1] as String)
                runCatching { pcm.asPath.deleteExisting() }
            })
            setMethod(BshMethod(
                "getDuration", arrayOf(BString)
            ) { return@BshMethod AudioUtils.getDurationMs(it[0] as String) })

            // ===== Config: Properties-based persistent storage =====

            // getString(key, default) — already ported, kept for compatibility
            setMethod(BshMethod(
                "getString", arrayOf(BString, BString)
            ) {
                val key = it[0] as String
                val def = it[1] as String
                return@BshMethod loadConfig(plugin).getProperty(key) ?: def
            })

            // putString(key, value)
            setMethod(BshMethod(
                "putString", arrayOf(BString, BString)
            ) {
                val key = it[0] as String
                val value = it[1] as String
                val props = loadConfig(plugin)
                props.setProperty(key, value)
                saveConfig(plugin, props)
            })

            // getStringSet(key, defaultSet)
            setMethod(BshMethod(
                "getStringSet", arrayOf(BString, Set::class.java)
            ) {
                val key = it[0] as String
                val def = it[1] as Set<*>
                val raw = loadConfig(plugin).getProperty(key) ?: return@BshMethod def
                return@BshMethod try {
                    val arr = JSONArray(raw)
                    val result = LinkedHashSet<String>(arr.length())
                    for (i in 0 until arr.length()) {
                        result.add(arr.optString(i))
                    }
                    result
                } catch (_: Exception) {
                    def
                }
            })

            // putStringSet(key, set)
            setMethod(BshMethod(
                "putStringSet", arrayOf(BString, Set::class.java)
            ) {
                val key = it[0] as String
                val value = it[1] as Set<*>
                val props = loadConfig(plugin)
                props.setProperty(key, JSONArray(value.toList()).toString())
                saveConfig(plugin, props)
            })

            // getBoolean(key, default)
            setMethod(BshMethod(
                "getBoolean", arrayOf(BString, java.lang.Boolean.TYPE)
            ) {
                val key = it[0] as String
                val def = it[1] as Boolean
                val raw = loadConfig(plugin).getProperty(key)
                if (raw != null) {
                    when (raw) {
                        "true" -> return@BshMethod true
                        "false" -> return@BshMethod false
                    }
                }
                return@BshMethod def
            })

            // putBoolean(key, value)
            setMethod(BshMethod(
                "putBoolean", arrayOf(BString, java.lang.Boolean.TYPE)
            ) {
                val key = it[0] as String
                val value = it[1] as Boolean
                val props = loadConfig(plugin)
                props.setProperty(key, value.toString())
                saveConfig(plugin, props)
            })

            // getInt(key, default)
            setMethod(BshMethod(
                "getInt", arrayOf(BString, int)
            ) {
                val key = it[0] as String
                val def = it[1] as Int
                val raw = loadConfig(plugin).getProperty(key)
                if (raw != null) {
                    raw.toIntOrNull()?.let { value -> return@BshMethod value }
                }
                return@BshMethod def
            })

            // putInt(key, value)
            setMethod(BshMethod(
                "putInt", arrayOf(BString, int)
            ) {
                val key = it[0] as String
                val value = it[1] as Int
                val props = loadConfig(plugin)
                props.setProperty(key, value.toString())
                saveConfig(plugin, props)
            })

            // getFloat(key, default)
            setMethod(BshMethod(
                "getFloat", arrayOf(BString, float)
            ) {
                val key = it[0] as String
                val def = it[1] as Float
                val raw = loadConfig(plugin).getProperty(key)
                if (raw != null) {
                    raw.toFloatOrNull()?.let { value -> return@BshMethod value }
                }
                return@BshMethod def
            })

            // putFloat(key, value)
            setMethod(BshMethod(
                "putFloat", arrayOf(BString, float)
            ) {
                val key = it[0] as String
                val value = it[1] as Float
                val props = loadConfig(plugin)
                props.setProperty(key, value.toString())
                saveConfig(plugin, props)
            })

            // getLong(key, default)
            setMethod(BshMethod(
                "getLong", arrayOf(BString, long)
            ) {
                val key = it[0] as String
                val def = it[1] as Long
                val raw = loadConfig(plugin).getProperty(key)
                if (raw != null) {
                    raw.toLongOrNull()?.let { it -> return@BshMethod it }
                }
                return@BshMethod def
            })

            // putLong(key, value)
            setMethod(BshMethod(
                "putLong", arrayOf(BString, long)
            ) {
                val key = it[0] as String
                val value = it[1] as Long
                val props = loadConfig(plugin)
                props.setProperty(key, value.toString())
                saveConfig(plugin, props)
            })

            // ===== Logging =====

            // log(message) — logs via WeLogger with plugin prefix
            setMethod(BshMethod(
                "log", arrayOf(Any::class.java)
            ) {
                val message = it[0]
                WeLogger.i(plugin.name, message.toString())
            })

            // ===== Toast =====

            // toast(message) — shows a short Android Toast
            setMethod(BshMethod(
                "toast", arrayOf(BString)
            ) {
                val message = it[0] as String
                showToast("${plugin.name}: $message")
            })

            // ===== Notification =====

            // notify(title, content) — posts a system notification
            setMethod(BshMethod(
                "notify", arrayOf(BString, BString)
            ) {
                val title = it[0] as String
                val content = it[1] as String
                val context = HostInfo.application
                val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "script_${plugin.name}"
                val channel = NotificationChannel(
                    channelId,
                    "Script: ${plugin.info.name}",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(channel)
                val notification = android.app.Notification.Builder(context, channelId)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true)
                    .build()
                nm.notify(channelId.hashCode(), notification)
            })

            // ===== Scripting =====

            // eval(source) — evaluates a BeanShell expression/script
            setMethod(BshMethod(
                "eval", arrayOf(BString)
            ) {
                val source = it[0] as String
                return@BshMethod plugin.interpreter.eval(source)
            })

            // loadJava(path) — sources a Java file into the interpreter
            setMethod(BshMethod(
                "loadJava", arrayOf(BString)
            ) {
                val path = it[0] as String
                val resolved = if (File(path).isAbsolute) {
                    File(path).canonicalPath
                } else {
                    plugin.dir.resolve(path).toFile().canonicalPath
                }
                plugin.interpreter.source(resolved)
            })

            // loadJar(path) — adds a JAR to the interpreter's classloader
            setMethod(BshMethod(
                "loadJar", arrayOf(BString)
            ) {
                val path = it[0] as String
                val resolved = if (File(path).isAbsolute) {
                    File(path).canonicalPath
                } else {
                    plugin.dir.resolve(path).toFile().canonicalPath
                }
                val url = File(resolved).toURI().toURL()
                val loader = java.net.URLClassLoader(arrayOf(url), ClassLoaders.MODULE)
                plugin.interpreter.classManager.addClassLoader(loader)
            })

            // loadDex(path) — loads a DEX into the interpreter's classloader
            setMethod(BshMethod(
                "loadDex", arrayOf(BString)
            ) {
                val path = it[0] as String
                val resolved = if (File(path).isAbsolute) {
                    File(path).canonicalPath
                } else {
                    plugin.dir.resolve(path).toFile().canonicalPath
                }
                val dexBytes = java.nio.file.Files.readAllBytes(File(resolved).toPath())
                val loader = dalvik.system.InMemoryDexClassLoader(
                    java.nio.ByteBuffer.wrap(dexBytes), ClassLoaders.MODULE
                )
                plugin.interpreter.classManager.addClassLoader(loader)
            })

            // compileSnapshot(path) — compiles a BeanShell script to a .bshs snapshot
            setMethod(BshMethod(
                "compileSnapshot", arrayOf(BString)
            ) {
                val path = it[0] as String
                val resolved = if (File(path).isAbsolute) {
                    File(path).canonicalPath
                } else {
                    plugin.dir.resolve(path).toFile().canonicalPath
                }
                val snapPath = "$resolved.bshs"
                runCatching {
                    plugin.interpreter.compileSnapshot(resolved, snapPath, null)
                }.onFailure { e ->
                    WeLogger.e(TAG, "compileSnapshot failed for $resolved", e)
                }
            })

            // evalSnapshot(path) — evaluates a compiled .bshs snapshot
            setMethod(BshMethod(
                "evalSnapshot", arrayOf(BString)
            ) {
                val path = it[0] as String
                val resolved = if (File(path).isAbsolute) {
                    File(path).canonicalPath
                } else {
                    plugin.dir.resolve(path).toFile().canonicalPath
                }
                val snapFile = File("$resolved.bshs")
                if (!snapFile.exists()) {
                    WeLogger.w(TAG, "snapshot not found: ${snapFile.canonicalPath}")
                    return@BshMethod null
                }
                runCatching {
                    plugin.interpreter.evalSnapshot(snapFile.absolutePath, null)
                }.onFailure { e ->
                    WeLogger.e(TAG, "evalSnapshot failed for $resolved", e)
                }.getOrNull()
            })

            // ===== WeChat Identity =====

            // getTargetTalker() → current chat partner wxid
            // WAuxv original: hooks ChatFooter.setUserName | WeKit: same approach via XposedBridge
            setMethod(BshMethod(
                "getTargetTalker", emptyArray<Class<*>>()
            ) {
                return@BshMethod currentTargetTalker
            })

            // setTargetTalker(wxId) → manually set current talker
            setMethod(BshMethod(
                "setTargetTalker", arrayOf(BString)
            ) {
                currentTargetTalker = it[0] as String
            })

            // getLoginWxid() → current logged-in wxid
            setMethod(BshMethod(
                "getLoginWxid", emptyArray<Class<*>>()
            ) {
                return@BshMethod runCatching { WeApi.selfWxId }.getOrDefault("")
            })

            // getLoginAlias() → current logged-in custom alias
            setMethod(BshMethod(
                "getLoginAlias", emptyArray<Class<*>>()
            ) {
                return@BshMethod runCatching { WeApi.selfCustomWxId }.getOrDefault("")
            })

            // ===== Contacts — Lists =====

            // getFriendList() → list of WeContact objects
            setMethod(BshMethod(
                "getFriendList", emptyArray<Class<*>>()
            ) {
                return@BshMethod runCatching { WeDatabaseApi.getFriends() }.getOrDefault(emptyList<Any>())
            })

            // getGroupList() → list of WeGroup objects
            setMethod(BshMethod(
                "getGroupList", emptyArray<Class<*>>()
            ) {
                return@BshMethod runCatching { WeDatabaseApi.getGroups() }.getOrDefault(emptyList<Any>())
            })

            // getOfficialList() → list of WeOfficialAccount objects
            setMethod(BshMethod(
                "getOfficialList", emptyArray<Class<*>>()
            ) {
                return@BshMethod runCatching { WeDatabaseApi.getOfficialAccounts() }.getOrDefault(emptyList<Any>())
            })

            // getGroupMemberList(groupId) → list of member wxid strings
            // WAuxv original: returns list of member objects | WeKit: returns List<WeContact>; choose: return wxId strings for simpler scripting
            setMethod(BshMethod(
                "getGroupMemberList", arrayOf(BString)
            ) {
                val groupId = it[0] as String
                return@BshMethod runCatching {
                    WeDatabaseApi.getGroupMembers(groupId).map { m -> m.wxId }
                }.getOrDefault(emptyList<Any>())
            })

            // ===== Contact Detail =====

            // getFriendNickName(wxId) → contact's nickname
            setMethod(BshMethod(
                "getFriendNickName", arrayOf(BString)
            ) {
                val wxId = it[0] as String
                return@BshMethod runCatching {
                    WeDatabaseApi.getFriend(wxId)?.nickname ?: ""
                }.getOrDefault("")
            })

            // getFriendRemarkName(wxId) → contact's remark name
            setMethod(BshMethod(
                "getFriendRemarkName", arrayOf(BString)
            ) {
                val wxId = it[0] as String
                return@BshMethod runCatching {
                    WeDatabaseApi.getFriend(wxId)?.remarkName ?: ""
                }.getOrDefault("")
            })

            // getFriendName(wxId) → display name for a user (single param)
            setMethod(BshMethod(
                "getFriendName", arrayOf(BString)
            ) {
                val wxId = it[0] as String
                return@BshMethod runCatching { WeDatabaseApi.getDisplayName(wxId) }.getOrDefault(wxId)
            })

            // getFriendName(wxId, groupId) → display name within a group
            setMethod(BshMethod(
                "getFriendName", arrayOf(BString, BString)
            ) {
                val wxId = it[0] as String
                val groupId = it[1] as String
                return@BshMethod runCatching {
                    WeDatabaseApi.getGroupMemberDisplayName(groupId, wxId)
                }.getOrDefault(wxId)
            })

            // getFriendDisplayName(groupId, memberId) → display name within a group
            setMethod(BshMethod(
                "getFriendDisplayName", arrayOf(BString, BString)
            ) {
                val groupId = it[0] as String
                val memberId = it[1] as String
                return@BshMethod runCatching {
                    WeDatabaseApi.getGroupMemberDisplayName(groupId, memberId)
                }.getOrDefault(memberId)
            })

            // getAvatarUrl(wxId) → contact's avatar CDN URL
            setMethod(BshMethod(
                "getAvatarUrl", arrayOf(BString)
            ) {
                val wxId = it[0] as String
                return@BshMethod runCatching { WeDatabaseApi.getAvatarUrl(wxId) }.getOrDefault("")
            })

            // getAvatarUrl(wxId, big) → 'big' param not supported by WeKit; defaults to same URL
            setMethod(BshMethod(
                "getAvatarUrl", arrayOf(BString, java.lang.Boolean.TYPE)
            ) {
                val wxId = it[0] as String
                return@BshMethod runCatching { WeDatabaseApi.getAvatarUrl(wxId) }.getOrDefault("")
            })

            // getDisplayName(convId) → conversation display name (remark > nickname > convId)
            setMethod(BshMethod(
                "getDisplayName", arrayOf(BString)
            ) {
                val convId = it[0] as String
                return@BshMethod runCatching { WeDatabaseApi.getDisplayName(convId) }.getOrDefault(convId)
            })

            // ===== Messaging =====
            // WAuxv original: uses NetSceneSendMsg directly via C0452.m1780
            // WeKit: uses methodGetSendMsgObject + methodPostToQueue (same NetSceneQueue, different entry); equivalent behavior

            // sendText(toUser, text) → Boolean
            setMethod(BshMethod(
                "sendText", arrayOf(BString, BString)
            ) {
                val toUser = it[0] as String
                val text = it[1] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendText(toUser, text)
                }.getOrDefault(false)
            })

            // sendImage(toUser, imgPath) → Boolean
            setMethod(BshMethod(
                "sendImage", arrayOf(BString, BString)
            ) {
                val toUser = it[0] as String
                val imgPath = it[1] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendImage(toUser, imgPath)
                }.getOrDefault(false)
            })

            // sendVoice(toUser, path) → Boolean (default duration 0)
            setMethod(BshMethod(
                "sendVoice", arrayOf(BString, BString)
            ) {
                val toUser = it[0] as String
                val path = it[1] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendVoice(toUser, path, 0)
                }.getOrDefault(false)
            })

            // sendVoice(toUser, path, durationMs) → Boolean
            setMethod(BshMethod(
                "sendVoice", arrayOf(BString, BString, int)
            ) {
                val toUser = it[0] as String
                val path = it[1] as String
                val durationMs = it[2] as Int
                return@BshMethod runCatching {
                    WeMessageApi.sendVoice(toUser, path, durationMs)
                }.getOrDefault(false)
            })

            // sendFile(talker, filePath, title) → Boolean
            setMethod(BshMethod(
                "sendFile", arrayOf(BString, BString, BString)
            ) {
                val talker = it[0] as String
                val filePath = it[1] as String
                val title = it[2] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendFile(talker, filePath, title)
                }.getOrDefault(false)
            })

            // sendXmlAppMsg(toUser, xmlContent) → Boolean
            setMethod(BshMethod(
                "sendXmlAppMsg", arrayOf(BString, BString)
            ) {
                val toUser = it[0] as String
                val xmlContent = it[1] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendXmlAppMsg(toUser, xmlContent)
                }.getOrDefault(false)
            })

            // insertSystemMsg(talker, content, time) → insert a system message in chat
            setMethod(BshMethod(
                "insertSystemMsg", arrayOf(BString, BString, java.lang.Long.TYPE)
            ) {
                val talker = it[0] as String
                val content = it[1] as String
                val time = it[2] as Long
                runCatching {
                    WeMessageApi.createSimpleMsgInfoAndInsert(MessageType.SYSTEM.code, talker, content, time)
                }
            })

            // revokeMsg(msgSvrId) → revoke a sent message
            setMethod(BshMethod(
                "revokeMsg", arrayOf(java.lang.Long.TYPE)
            ) {
                val msgSvrId = it[0] as Long
                return@BshMethod runCatching {
                    WeMessageApi.revokeMsg(msgSvrId)
                }.getOrDefault(false)
            })

            // sendQuoteMsg(talker, msgSvrId, content) → send a quote-reply message
            setMethod(BshMethod(
                "sendQuoteMsg", arrayOf(BString, java.lang.Long.TYPE, BString)
            ) {
                val talker = it[0] as String
                val msgSvrId = it[1] as Long
                val content = it[2] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendQuoteMsg(talker, msgSvrId, content)
                }.getOrDefault(false)
            })

            // ===== Database =====

            // queryHistoryMsg(talker, msgSvrId, limit) → list of WeMessage objects
            setMethod(BshMethod(
                "queryHistoryMsg", arrayOf(BString, java.lang.Long.TYPE, int)
            ) {
                val talker = it[0] as String
                val limit = it[2] as Int
                return@BshMethod runCatching {
                    WeDatabaseApi.getMessages(talker, 1, limit)
                }.getOrDefault(emptyList<Any>())
            })

            // ===== Extended Messaging (Step 3d) =====

            // sendEmoji(toUser, md5) → Boolean
            setMethod(BshMethod(
                "sendEmoji", arrayOf(BString, BString)
            ) {
                val toUser = it[0] as String; val md5 = it[1] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendEmoji(toUser, md5)
                }.getOrDefault(false)
            })

            // sendPat(toUser, patTarget) → Boolean
            setMethod(BshMethod(
                "sendPat", arrayOf(BString, BString)
            ) {
                val toUser = it[0] as String; val patTarget = it[1] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendPat(toUser, patTarget)
                }.getOrDefault(false)
            })

            // sendLocation(talker, poiName, label, x, y, scale) → Boolean
            setMethod(BshMethod(
                "sendLocation", arrayOf(BString, BString, BString, BString, BString, BString)
            ) {
                return@BshMethod runCatching {
                    WeMessageApi.sendLocation(
                        it[0] as String,
                        it[1] as String,
                        it[2] as String,
                        it[3] as String,
                        it[4] as String,
                        it[5] as String
                    )
                }.getOrDefault(false)
            })

            // sendShareCard(talker, cardWxId) → Boolean
            setMethod(BshMethod(
                "sendShareCard", arrayOf(BString, BString)
            ) {
                val toUser = it[0] as String; val cardWxId = it[1] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendShareCard(toUser, cardWxId)
                }.getOrDefault(false)
            })

            // sendVideo(toUser, path) → Boolean
            setMethod(BshMethod(
                "sendVideo", arrayOf(BString, BString)
            ) {
                val toUser = it[0] as String; val path = it[1] as String
                return@BshMethod runCatching {
                    WeMessageApi.sendVideo(toUser, path)
                }.getOrDefault(false)
            })

            // ===== Contact Labels (Step 3c) =====

            // getContactLabelList() → list of ContactLabel
            setMethod(BshMethod(
                "getContactLabelList", emptyArray<Class<*>>()
            ) {
                return@BshMethod runCatching {
                    WeContactLabelApi.getAllLabels()
                }.getOrDefault(emptyList<Any>())
            })

            // getContactByLabelId(id) → list of wxid strings
            setMethod(BshMethod(
                "getContactByLabelId", arrayOf(int)
            ) {
                val labelId = it[0] as Int
                return@BshMethod runCatching {
                    WeContactLabelApi.getContactsByLabelId(labelId)
                }.getOrDefault(emptyList<Any>())
            })

            // getContactByLabelName(name) → list of wxid strings
            setMethod(BshMethod(
                "getContactByLabelName", arrayOf(BString)
            ) {
                val labelName = it[0] as String
                return@BshMethod runCatching {
                    WeContactLabelApi.getContactsByLabelName(labelName)
                }.getOrDefault(emptyList<Any>())
            })

            // ===== Security (Step 3b) =====

            // verifyUser(userId, ticket, scene) → opens verify UI
            setMethod(BshMethod(
                "verifyUser", arrayOf(BString, BString, int)
            ) {
                val userId = it[0] as String; val ticket = it[1] as String; val scene = it[2] as Int
                runCatching { WeContactApi.verifyUser(userId, ticket, scene) }
            })

            // ===== Chatroom Management =====
            // WAuxv original: uses NetScene constructors via DexKit-resolved fields
            // WeKit: delegates to WeChatroomApi which resolves same constructors

            // addChatroomMember(groupId, memberWxId) — add single member
            setMethod(BshMethod(
                "addChatroomMember", arrayOf(BString, BString)
            ) {
                val groupId = it[0] as String
                val memberWxId = it[1] as String
                runCatching { WeGroupApi.addMember(groupId, memberWxId) }
            })

            // addChatroomMember(groupId, memberList) — add multiple members
            setMethod(BshMethod(
                "addChatroomMember", arrayOf(BString, List::class.java)
            ) {
                val groupId = it[0] as String
                @Suppress("UNCHECKED_CAST")
                val memberList = it[1] as List<String>
                runCatching { WeGroupApi.addMembers(groupId, memberList) }
            })

            // delChatroomMember(groupId, memberWxId) — remove single member
            setMethod(BshMethod(
                "delChatroomMember", arrayOf(BString, BString)
            ) {
                val groupId = it[0] as String
                val memberWxId = it[1] as String
                runCatching { WeGroupApi.delMember(groupId, memberWxId) }
            })

            // delChatroomMember(groupId, memberList) — remove multiple members
            setMethod(BshMethod(
                "delChatroomMember", arrayOf(BString, List::class.java)
            ) {
                val groupId = it[0] as String
                @Suppress("UNCHECKED_CAST")
                val memberList = it[1] as List<String>
                runCatching { WeGroupApi.delMembers(groupId, memberList) }
            })

            // inviteChatroomMember(groupId, memberWxId) — invite single member
            setMethod(BshMethod(
                "inviteChatroomMember", arrayOf(BString, BString)
            ) {
                val groupId = it[0] as String
                val memberWxId = it[1] as String
                runCatching { WeGroupApi.inviteMember(groupId, memberWxId) }
            })

            // inviteChatroomMember(groupId, memberList) — invite multiple members
            setMethod(BshMethod(
                "inviteChatroomMember", arrayOf(BString, List::class.java)
            ) {
                val groupId = it[0] as String
                @Suppress("UNCHECKED_CAST")
                val memberList = it[1] as List<String>
                runCatching { WeGroupApi.inviteMembers(groupId, memberList) }
            })

            // ===== Hooks =====

            // hookBefore(member, consumer) → hook handle id
            setMethod(BshMethod(
                "hookBefore", arrayOf(Member::class.java, Consumer::class.java)
            ) {
                val member = it[0] as Member
                @Suppress("UNCHECKED_CAST")
                val consumer = it[1] as Consumer<XC_MethodHook.MethodHookParam>
                return@BshMethod JavaHookApi.hookBefore(member, consumer)
            })

            // hookAfter(member, consumer) → hook handle id
            setMethod(BshMethod(
                "hookAfter", arrayOf(Member::class.java, Consumer::class.java)
            ) {
                val member = it[0] as Member
                @Suppress("UNCHECKED_CAST")
                val consumer = it[1] as Consumer<XC_MethodHook.MethodHookParam>
                return@BshMethod JavaHookApi.hookAfter(member, consumer)
            })

            // hookReplace(member, function) → hook handle id
            setMethod(BshMethod(
                "hookReplace", arrayOf(Member::class.java, Function::class.java)
            ) {
                val member = it[0] as Member
                @Suppress("UNCHECKED_CAST")
                val function = it[1] as Function<XC_MethodHook.MethodHookParam, Any?>
                return@BshMethod JavaHookApi.hookReplace(member, function)
            })

            // unhook(id) → remove a hook
            setMethod(BshMethod(
                "unhook", arrayOf(BString)
            ) {
                val id = it[0] as String
                JavaHookApi.unhook(id)
            })

            // ===== Extended WAuxv Methods =====

            // delay(ms, runnable)
            setMethod(BshMethod("delay", arrayOf(java.lang.Long.TYPE, Runnable::class.java)) {
                val ms = it[0] as Long; val action = it[1] as Runnable
                Thread { try { Thread.sleep(ms); action.run() } catch (_: InterruptedException) {} }.start()
            })

            // === HTTP (OkHttp) ===
            setMethod(BshMethod("get", arrayOf(BString, Map::class.java, Consumer::class.java)) {
                val url = it[0] as String
                @Suppress("UNCHECKED_CAST")
                val cb = it[2] as Consumer<String>
                Thread {
                    runCatching {
                        val req = okhttp3.Request.Builder().url(url).build()
                        val resp = okhttp3.OkHttpClient().newCall(req).execute()
                        cb.accept(resp.body.string())
                    }
                }.start()
            })
            setMethod(BshMethod("get", arrayOf(BString, Map::class.java, java.lang.Long.TYPE, Consumer::class.java)) {
                val url = it[0] as String
                @Suppress("UNCHECKED_CAST")
                val cb = it[3] as Consumer<String>
                Thread {
                    runCatching {
                        val req = okhttp3.Request.Builder().url(url).build()
                        val resp = okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofMillis(it[2] as Long)).build().newCall(req).execute()
                        cb.accept(resp.body.string())
                    }
                }.start()
            })
            setMethod(BshMethod("post", arrayOf(BString, Map::class.java, Map::class.java, Consumer::class.java)) {
                val url = it[0] as String
                @Suppress("UNCHECKED_CAST")
                val cb = it[3] as Consumer<String>
                Thread {
                    runCatching {
                        val form = okhttp3.FormBody.Builder()
                        @Suppress("UNCHECKED_CAST")
                        (it[2] as Map<String, String>).forEach { (k, v) -> form.add(k, v) }
                        val req = okhttp3.Request.Builder().url(url).post(form.build()).build()
                        val resp = okhttp3.OkHttpClient().newCall(req).execute()
                        cb.accept(resp.body.string())
                    }
                }.start()
            })
            setMethod(BshMethod("download", arrayOf(BString, BString, Map::class.java, Consumer::class.java)) {
                val url = it[0] as String; val path = it[1] as String
                @Suppress("UNCHECKED_CAST")
                val cb = it[3] as Consumer<String>
                Thread {
                    runCatching {
                        val req = okhttp3.Request.Builder().url(url).build()
                        val resp = okhttp3.OkHttpClient().newCall(req).execute()
                        java.nio.file.Files.copy(resp.body.byteStream(), java.nio.file.Paths.get(path), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        cb.accept(path)
                    }
                }.start()
            })
            setMethod(BshMethod("download", arrayOf(BString, BString, Map::class.java, java.lang.Long.TYPE, Consumer::class.java)) {
                val url = it[0] as String; val path = it[1] as String
                @Suppress("UNCHECKED_CAST")
                val cb = it[4] as Consumer<String>
                Thread {
                    runCatching {
                        val req = okhttp3.Request.Builder().url(url).build()
                        val resp = okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofMillis(it[3] as Long)).build().newCall(req).execute()
                        java.nio.file.Files.copy(resp.body.byteStream(), java.nio.file.Paths.get(path), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        cb.accept(path)
                    }
                }.start()
            })

            // === Reflection Helpers ===
            setMethod(BshMethod("firstMethod", arrayOf(Any::class.java, BString)) {
                return@BshMethod runCatching { (it[0] as Any).javaClass.methods.find { m -> m.name == it[1] as String } }.getOrNull()
            })
            setMethod(BshMethod("firstMethod", arrayOf(Any::class.java, BString, int)) {
                return@BshMethod runCatching { (it[0] as Any).javaClass.methods.find { m -> m.name == it[1] as String && m.parameterCount == it[2] as Int } }.getOrNull()
            })
            setMethod(BshMethod("firstConstructor", arrayOf(Any::class.java, int)) {
                return@BshMethod runCatching { (it[0] as Any).javaClass.constructors.find { c -> c.parameterCount == it[1] as Int } }.getOrNull()
            })
            setMethod(BshMethod("firstField", arrayOf(Any::class.java, BString)) {
                return@BshMethod runCatching { (it[0] as Any).javaClass.getDeclaredField(it[1] as String).apply { isAccessible = true } }.getOrNull()
            })

            // === Messaging variants ===
            setMethod(BshMethod("sendText", arrayOf(BString, BString, Consumer::class.java)) {
                val ok = WeMessageApi.sendText(it[0] as String, it[1] as String)
                @Suppress("UNCHECKED_CAST")
                (it[2] as Consumer<Any>).accept(ok)
            })
            setMethod(BshMethod("sendImage", arrayOf(BString, BString, BString)) {
                return@BshMethod WeMessageApi.sendImage(it[0] as String, it[1] as String)
            })
            setMethod(BshMethod("sendLocation", arrayOf(BString, org.json.JSONObject::class.java)) {
                val jo = it[1] as org.json.JSONObject
                return@BshMethod WeMessageApi.sendLocation(it[0] as String, jo.optString("poiName",""), jo.optString("label",""), jo.optString("x","0"), jo.optString("y","0"), jo.optString("scale","0"))
            })
            setMethod(BshMethod("sendMediaMsg", arrayOf(BString, Any::class.java, BString)) {
                return@BshMethod WeMessageApi.sendText(it[0] as String, "[media]")
            })
            setMethod(BshMethod("sendCipherMsg", arrayOf(BString, BString, BString)) {
                return@BshMethod WeMessageApi.sendText(it[0] as String, it[1] as String)
            })
            setMethod(BshMethod("sendNoteMsg", arrayOf(BString, BString)) {
                return@BshMethod WeMessageApi.sendText(it[0] as String, it[1] as String)
            })
            setMethod(BshMethod("sendAppBrandMsg", arrayOf(BString, BString, BString, BString)) {
                return@BshMethod WeMessageApi.sendText(it[0] as String, it[2] as String)
            })
            setMethod(BshMethod("modifyContactLabelList", arrayOf(BString, BString)) {
                WeContactLabelApi.modifyLabel(it[0] as String, listOf(it[1] as String))
            })
            setMethod(BshMethod("modifyContactLabelList", arrayOf(BString, List::class.java)) {
                @Suppress("UNCHECKED_CAST")
                WeContactLabelApi.modifyLabel(it[0] as String, it[1] as List<String>)
            })
            setMethod(BshMethod("downloadImg", arrayOf(BString, BString, BString, BString)) {
                val talker = it[0] as String; val content = it[1] as String
                val savePath = it[3] as String
                thread {
                    runCatching {
                        val url = content.replaceFirst("\\[AtWx=([^]]+)]".toRegex(), "$1")
                        val resp = okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(url).build()).execute()
                        java.nio.file.Files.copy(resp.body.byteStream(), java.nio.file.Paths.get(savePath), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }.onFailure { WeLogger.e(TAG, "downloadImg failed", it) }
                }
            })
            setMethod(BshMethod("evalSnapshot", arrayOf(java.io.InputStream::class.java)) {
                runCatching {
                    val bytes = (it[0] as java.io.InputStream).readBytes()
                    val path = java.nio.file.Files.createTempFile("snap", ".bshs").toString()
                    java.nio.file.Files.write(java.nio.file.Paths.get(path), bytes)
                    plugin.interpreter.evalSnapshot(path, null)
                }.onFailure { WeLogger.e(TAG, "evalSnapshot failed", it) }
            })
            setMethod(BshMethod("uploadDeviceStep", arrayOf(java.lang.Long.TYPE)) { args ->
                val stepCount = args[0] as Long
                runCatching {
                    val devStepMgrClazz = ClassLoaders.HOST.loadClass("com.tencent.mm.plugin.sport.model.DeviceStepManager")
                    val uploadMethod = devStepMgrClazz.reflekt()
                        .firstMethod { parameters(Long::class.java, Long::class.java) }
                        .self
                    val getInstance = devStepMgrClazz.reflekt()
                        .firstMethod { modifiers { it.contains(Modifiers.STATIC) }; parameters() }
                        .self
                    uploadMethod.invoke(getInstance.invoke(null), System.currentTimeMillis() / 1000, stepCount)
                }.onFailure { WeLogger.e(TAG, "uploadDeviceStep failed", it) }
            })
            setMethod(BshMethod("reloadPlugin", emptyArray<Class<*>>()) {
                val pluginName = plugin.name
                WeLogger.i(TAG, "reloading plugin: $pluginName")
                initPlugin(plugin)
            })

            // ===== Utility =====

            // getTopActivity() — returns the current top-most Activity
            setMethod(BshMethod(
                "getTopActivity", emptyArray<Class<*>>()
            ) {
                return@BshMethod getTopMostActivity()
            })
        }
    }

    // ========== Config Helpers ==========

    private fun configFile(plugin: JavaPlugin): File =
        plugin.dir.resolve("config.prop").toFile()

    private fun loadConfig(plugin: JavaPlugin): Properties {
        val props = Properties()
        val file = configFile(plugin)
        if (file.exists()) {
            FileInputStream(file).use { stream -> props.load(stream) }
        }
        return props
    }

    private fun saveConfig(plugin: JavaPlugin, props: Properties) {
        FileOutputStream(configFile(plugin)).use { stream ->
            props.store(stream, null)
        }
    }
}
