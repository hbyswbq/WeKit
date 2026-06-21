package dev.ujhhgtg.wekit.hooks.items.payment

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.hooks.core.ClickableHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.preferences.WePrefs.Companion.prefOption
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.nul
import dev.ujhhgtg.wekit.utils.reflection.BString
import dev.ujhhgtg.wekit.utils.reflection.bool

@HookItem(name = "修改显示余额", categories = ["红包与支付"], description = "伪装钱包余额文字")
object ModifyWalletBalanceDisplay : ClickableHookItem(), IResolveDex {

    private const val KEY_BALANCE = "fake_wallet_balance"

    private val methodWcPayMoneyLoadingViewSetMoneyCore by dexMethod {
        matcher {
            declaredClass = "com.tencent.mm.plugin.wallet_core.ui.view.WcPayMoneyLoadingView"
            paramTypes(BString, bool, bool, bool)
            addInvoke {
                declaredClass = "com.tencent.mm.plugin.wallet_core.ui.view.WcPayMoneyLoadingView"
                name = "setFirstMoney"
            }
        }
    }

    private var balance by prefOption(KEY_BALANCE, nul<String>())

    override fun onEnable() {
        methodWcPayMoneyLoadingViewSetMoneyCore.hookBefore {
            val balance = balance ?: return@hookBefore
            args[0] = balance
        }
    }

    override fun onClick(context: Context) {
        showComposeDialog(context) {
            var balanceInput by remember { mutableStateOf(balance ?: "") }

            AlertDialogContent(
                title = { Text("修改显示余额") },
                text = {
                    TextField(
                        value = balanceInput,
                        onValueChange = { balanceInput = it },
                        label = { Text("零钱余额 (留空不修改)") })
                },
                confirmButton = {
                    Button(onClick = {
                        balance = if (!balanceInput.isBlank())
                            balanceInput
                        else
                            null
                        onDismiss()
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } }
            )
        }
    }
}
