package dev.ujhhgtg.wekit.hooks.api.ui

import android.content.Context
import android.content.DialogInterface
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.hooks.core.ApiHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.utils.reflection.BString
import org.luckypray.dexkit.DexKitBridge

@HookItem(name = "对话框 API", categories = ["API"], description = "提供显示微信自带对话框的能力")
object WeAlertDialogApi : ApiHookItem(), IResolveDex {

    private val classMmAlert by dexClass()

    override fun resolveDex(dexKit: DexKitBridge) {
        classMmAlert.find(dexKit) {
            matcher {
                usingEqStrings("MicroMsg.MMAlert")
            }
        }
    }

    fun showAlertDialog(
        context: Context,
        content: String,
        title: String? = null,
        onClickOk: (DialogInterface) -> Unit = {},
        onClickCancel: (DialogInterface) -> Unit = {},
        okText: String = "确定",
        cancelText: String = "取消"
    ) {
        classMmAlert.reflekt()
            .firstMethod {
                parameters(Context::class, BString, BString, BString, BString, DialogInterface.OnClickListener::class, DialogInterface.OnClickListener::class)
            }.invokeStatic(
                context, content, title ?: "", okText, cancelText,
                DialogInterface.OnClickListener { di, _ -> onClickOk(di) },
                DialogInterface.OnClickListener { di, _ -> onClickCancel(di) })
    }
}
