package dev.ujhhgtg.wekit.hooks.items.chat

import dev.ujhhgtg.reflekt.utils.Modifiers
import com.tencent.mm.plugin.gif.MMWXGFJNI
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.hooks.api.ui.WeChatMessageContextMenuApi
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.hooks.core.SwitchHookItem
import dev.ujhhgtg.wekit.ui.utils.DownloadIcon
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToastSuspend
import dev.ujhhgtg.wekit.utils.fs.KnownPaths
import dev.ujhhgtg.reflekt.reflekt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.luckypray.dexkit.DexKitBridge
import kotlin.io.path.div
import kotlin.io.path.outputStream

@HookItem(name = "贴纸保存到本地", categories = ["聊天"], description = "在贴纸消息菜单添加保存按钮, 允许将图片保存到本地")
object SaveStickersToLocalStorage : SwitchHookItem(), IResolveDex,
    WeChatMessageContextMenuApi.IMenuItemsProvider {

    private val TAG = This.Class.simpleName

    private val classEmojiFileEncryptMgr by dexClass()

    override fun onEnable() {
        WeChatMessageContextMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeChatMessageContextMenuApi.removeProvider(this)
    }

    override fun resolveDex(dexKit: DexKitBridge) {
        classEmojiFileEncryptMgr.find(dexKit) {
            matcher {
                methods {
                    add {
                        usingEqStrings(
                            "MicroMsg.emoji.EmojiFileEncryptMgr",
                            "decode emoji file failed. path is no exist :%s "
                        )
                    }
                }
            }
        }
    }

    override fun getMenuItems(): List<WeChatMessageContextMenuApi.MenuItem> {
        return listOf(
            @Suppress("UNCHECKED_CAST")
            WeChatMessageContextMenuApi.MenuItem(
                777001,
                "存本地",
                DownloadIcon,
                { msgInfo -> msgInfo.type?.isSticker ?: false }
            ) { _, _, msgInfo ->
                val md5 = msgInfo.imagePath
                val emojiInfo = StickersSync.getEmojiInfoByMd5(md5)
                val emojiFileEncryptMgr = classEmojiFileEncryptMgr.reflekt()
                    .firstMethod {
                        modifiers { it.contains(Modifiers.STATIC) }
                        parameterCount = 0
                    }
                    .invokeStatic()!!
                var bytes = emojiFileEncryptMgr.reflekt()
                    .firstMethod {
                        parameters("com.tencent.mm.api.IEmojiInfo")
                        returnType = ByteArray::class
                    }
                    .invoke(emojiInfo) as ByteArray
                bytes = MMWXGFJNI.nativeWxamToGif(bytes)

                val fileName = "sticker_${System.currentTimeMillis()}.gif"
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        (KnownPaths.downloads / fileName).outputStream().use { out ->
                            out.write(bytes)
                        }
                    }.onSuccess {
                        showToastSuspend("已将贴纸保存到 /sdcard/Download/WeKit/$fileName")
                    }.onFailure { e ->
                        WeLogger.e(TAG, "failed to save sticker $fileName", e)
                        showToastSuspend("贴纸保存失败! 查看日志以了解错误详情")
                    }
                }
            }
        )
    }
}
