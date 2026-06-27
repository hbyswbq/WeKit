package dev.ujhhgtg.wekit.features.items.moments

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tencent.mm.plugin.sns.ui.SnsUploadUI
import com.tencent.mm.ui.widget.button.WeButton
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.DefaultColumn
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.findViewWhich
import dev.ujhhgtg.wekit.ui.utils.rootView
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.android.getTopMostActivity
import java.util.concurrent.CountDownLatch

@Feature(name = "自定义尾巴", categories = ["朋友圈"], description = "自定义发表朋友圈显示的应用来源")
object CustomAppId : SwitchFeature(), IResolveDex {

    private val methodCommitSnsInfo by dexMethod {
        matcher {
            usingEqStrings("MicroMsg.UploadPackHelper", "commit sns info ret %d, typeFlag %d sightMd5 %s")
        }
    }

    private val methodSetSdkAppId by dexMethod {
        searchPackages("com.tencent.mm.plugin.sns.model")
        matcher {
            usingEqStrings("setSdkId", "com.tencent.mm.plugin.sns.model.UploadPackHelper")
        }
    }

    private val methodSetSdkAppName by dexMethod {
        searchPackages("com.tencent.mm.plugin.sns.model")
        matcher {
            usingEqStrings("setSdkAppName", "com.tencent.mm.plugin.sns.model.UploadPackHelper")
        }
    }

    var appId = ""
    var appName =""

    override fun onEnable() {
        SnsUploadUI::class.hookBeforeOnCreate {
            val button = (thisObject as Activity).rootView.findViewWhich<WeButton> { it is WeButton }!!
            button.setOnLongClickListener { view ->
                showComposeDialog(view.context) {

                    AlertDialogContent(title = { Text("自定义尾巴") },
                        text = {
                            DefaultColumn {
                                OutlinedTextField(
                                    value = appId,
                                    onValueChange = { appId = it },
                                    label = { Text("应用 ID (留空不更改)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = appName,
                                    onValueChange = { appName = it },
                                    label = { Text("应用名称 (留空不更改)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        dismissButton = { TextButton(onDismiss) { Text("取消") } },
                        confirmButton = { Button(onClick = {
                            if (appId.isNotBlank()) {
                                methodSetSdkAppId.method.invoke(thisObject, appId)
                            }
                            if (appName.isNotBlank()) {
                                methodSetSdkAppName.method.invoke(thisObject, appName)
                            }
                            latch.countDown()
                        }) { Text("确定") } })
                }
            }
        }

        methodCommitSnsInfo.hookBefore {
            val activity = getTopMostActivity()!!
            val latch = CountDownLatch(1)


            latch.await()
        }
    }
}
