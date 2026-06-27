package dev.ujhhgtg.wekit.features.api.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.isSubclassOf
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.api.core.WeMessageApi
import dev.ujhhgtg.wekit.features.api.core.models.MessageInfo
import dev.ujhhgtg.wekit.features.core.ApiFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.utils.WeLogger

@SuppressLint("StaticFieldLeak")
@Feature(name = "聊天界面消息菜单扩展", categories = ["API"], description = "为聊天界面消息长按菜单提供添加菜单项功能")
object WeChatMessageContextMenuApi : ApiFeature(), IResolveDex {

    fun interface IMenuItemsProvider {
        fun getMenuItems(): List<MenuItem>
    }

    data class MenuItem(
        val id: Int,
        val text: String, val drawable: Drawable,
        val shouldShow: (MessageInfo) -> Boolean,
        val onClick: (View, ChattingContext, MessageInfo) -> Unit
    )

    // for clearer semantics; this simply compiles to Object in JVM bytecode
    @JvmInline
    value class ChattingContext(val instance: Any) {
        val activity: Activity
            get() = instance.reflekt()
                .firstMethod {
                    returnType = Activity::class
                }.invoke()!! as Activity
    }

    private val TAG = This.Class.simpleName

    private val menuItems = mutableMapOf<String, List<MenuItem>>()

    fun addProvider(provider: IMenuItemsProvider) {
        menuItems[provider.javaClass.name] = provider.getMenuItems()
    }

    fun removeProvider(provider: IMenuItemsProvider) {
        menuItems.remove(provider.javaClass.name)
    }

    private val methodCreateMenu by dexMethod {
        searchPackages("com.tencent.mm.ui.chatting.viewitems")
        matcher {
            usingEqStrings("MicroMsg.ChattingItem", "msg is null!")
        }
    }
    private val methodSelectMenuItem by dexMethod {
        searchPackages("com.tencent.mm.ui.chatting.viewitems")
        matcher {
            usingEqStrings("MicroMsg.ChattingItem", "context item select failed, null dataTag")
        }
    }
//    private val classChattingMessBox by dexClass {
//        searchPackages("com.tencent.mm.ui.chatting.component")
//        matcher {
//            usingEqStrings(
//                "MicroMsg.ChattingUI.FootComponent",
//                "onNotifyChange event %s talker %s"
//            )
//        }
//    }

    private var currentView: View? = null

    override fun onEnable() {
        methodCreateMenu.hookBefore {
            val menu = args[0]

            currentView = args[1] as View
            val tag = currentView!!.tag

            val msgInfo = WeMessageApi.getMsgInfoFromTag(tag)

            try {
                for (item in menuItems.values.flatten()) {
                    if (item.shouldShow(MessageInfo(msgInfo))) {
                        menu.reflekt()
                            .firstMethod {
                                parameters(Int::class, CharSequence::class, Drawable::class)
                                returnType = android.view.MenuItem::class
                            }
                            .invoke(item.id, item.text, item.drawable)
                    }
                }
            } catch (ex: Throwable) {
                WeLogger.e(
                    TAG,
                    "exception occurred threw while providing menu items",
                    ex
                )
            }
        }

        methodSelectMenuItem.hookBefore {
            val viewOnLongClickListener = thisObject.reflekt()
                .firstField {
                    type {
                        it isSubclassOf View.OnLongClickListener::class
                    }
                }
                .get() as View.OnLongClickListener
            val chattingContext = viewOnLongClickListener.reflekt()
                .firstField {
                    type = WeMessageApi.classChattingContext.clazz
                    superclass()
                }
                .get()!!

            val tag = currentView!!.tag
            val msgInfo = WeMessageApi.getMsgInfoFromTag(tag)

            val menuItem = args[0] as android.view.MenuItem
            val msgInfoWrapper = MessageInfo(msgInfo)
            try {
                for (item in menuItems.values.flatten()) {
                    if (item.id == menuItem.itemId) {
                        item.onClick(
                            currentView!!,
                            ChattingContext(chattingContext),
                            msgInfoWrapper
                        )
                        result = null
                        return@hookBefore
                    }
                }
            } catch (ex: Throwable) {
                WeLogger.e(
                    TAG,
                    "exception occurred while handling click event",
                    ex
                )
            }
        }
    }

    override fun onDisable() {
        currentView = null
    }
}
