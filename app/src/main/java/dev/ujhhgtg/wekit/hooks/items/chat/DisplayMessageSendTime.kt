package dev.ujhhgtg.wekit.hooks.items.chat

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.robv.android.xposed.XC_MethodHook
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.hooks.api.ui.WeChatMessageViewApi
import dev.ujhhgtg.wekit.hooks.core.ClickableHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.preferences.WePrefs.Companion.prefOption
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.formatEpoch
import java.lang.reflect.Field


@HookItem(name = "显示消息时间", categories = ["聊天"], description = "显示精确消息发送时间")
object DisplayMessageSendTime : ClickableHookItem(),
    WeChatMessageViewApi.ICreateViewListener {

    override fun onEnable() {
        WeChatMessageViewApi.addListener(this)
    }

    override fun onDisable() {
        WeChatMessageViewApi.removeListener(this)
    }

    private lateinit var avatarField: Field

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        param: XC_MethodHook.MethodHookParam,
        view: View
    ) {
        val tag = view.tag ?: return
        val msgInfo = WeChatMessageViewApi.getMsgInfoFromParam(param)
        val text = formatEpoch(msgInfo.createTime, pattern)

        val time = tag.reflekt()
            .firstField {
                name = "timeTV"
                superclass()
            }
            .get() as? TextView? ?: return

        time.visibility = View.VISIBLE
        time.text = text
        time.setTextColor(android.graphics.Color.GRAY)

        val context = time.context

        // 1. Convert 16dp to pixels dynamically so it matches standard screen-edge spacing
        val edgeMarginPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12f,
            context.resources.displayMetrics
        ).toInt()

        // 2. Make the paddings above and below the time smaller (2dp)
        val verticalPaddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f,
            context.resources.displayMetrics
        ).toInt()
        time.setPadding(time.paddingLeft, verticalPaddingPx, time.paddingRight, verticalPaddingPx)

        val lp = time.layoutParams as? RelativeLayout.LayoutParams
        if (lp != null) {
            // Always remove WeChat's default horizontal centering rule
            lp.removeRule(RelativeLayout.CENTER_HORIZONTAL)

            // 3. Conditional alignment based on who sent the message
            if (msgInfo.isSelfSender) {
                // Align to the Right (End)
                lp.removeRule(RelativeLayout.ALIGN_PARENT_START)
                lp.addRule(RelativeLayout.ALIGN_PARENT_END)

                lp.marginEnd = edgeMarginPx
                lp.marginStart = 0 // Clear opposing margin to prevent bugs on view recycling

                time.gravity = Gravity.END
            } else {
                // Align to the Left (Start)
                lp.removeRule(RelativeLayout.ALIGN_PARENT_END)
                lp.addRule(RelativeLayout.ALIGN_PARENT_START)

                // Resolve avatar to check if it's currently hidden
                if (!::avatarField.isInitialized) {
                    avatarField = tag.reflekt()
                        .firstField { name = "avatarIV"; superclass() }.self
                }
                val avatar = avatarField.get(tag) as View?
                val avatarContainer = avatar?.parent as? View ?: avatar

                if (avatarContainer != null && avatarContainer.visibility != View.VISIBLE) {
                    // If the avatar is hidden, shift the timestamp right to align under the bubble.
                    // Uses measured width if available; otherwise falls back to 52dp (40dp avatar + 12dp spacing).
                    val avatarWidthPx = if (avatarContainer.width > 0) {
                        avatarContainer.width
                    } else {
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            52f,
                            context.resources.displayMetrics
                        ).toInt()
                    }
                    lp.marginStart = edgeMarginPx + avatarWidthPx
                } else {
                    // Default edge spacing when avatar is visible
                    lp.marginStart = edgeMarginPx
                }

                lp.marginEnd = 0 // Clear opposing margin to prevent bugs on view recycling
                time.gravity = Gravity.START
            }

            time.layoutParams = lp
        }
    }

    private var pattern by prefOption("msg_time_pattern", "yyyy/MM/dd HH:mm:ss")

    override fun onClick(context: Context) {
        showComposeDialog(context) {
            var input by remember { mutableStateOf(pattern) }

            AlertDialogContent(title = { Text("显示消息时间") },
                text = {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("时间格式 (Java)") })
                },
                confirmButton = {
                    Button(onClick = {
                        pattern = input
                        onDismiss()
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
        }
    }
}
