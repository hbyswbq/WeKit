package dev.ujhhgtg.wekit.features.items.moments

import android.content.Context
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.features.api.net.models.protobuf.TimelineObjectProto
import dev.ujhhgtg.wekit.features.api.ui.WeMomentsApi
import dev.ujhhgtg.wekit.features.api.ui.WeMomentsContextMenuApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.ui.utils.SendIcon
import dev.ujhhgtg.wekit.ui.utils.ShareIcon
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.Intent
import dev.ujhhgtg.wekit.utils.android.showToast
import dev.ujhhgtg.wekit.utils.android.showToastSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.LinkedList

@Feature(name = "转发 & 一键转发", categories = ["朋友圈"], description = "转发他人的朋友圈")
object ReMoment : SwitchFeature(), WeMomentsContextMenuApi.IMenuItemsProvider {

    private val TAG = This.Class.simpleName

    override fun onEnable() {
        WeMomentsContextMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeMomentsContextMenuApi.removeProvider(this)
    }

    override fun getMenuItems(): List<WeMomentsContextMenuApi.MenuItem> {
        return listOf(
            WeMomentsContextMenuApi.MenuItem(
                777013,
                "转发",
                ShareIcon,
                { _, _ -> true },
            ) { moment ->
                try {
                    repostMoment(moment)
                } catch (e: Throwable) {
                    WeLogger.e(TAG, "forward failed", e)
                }
            },
            WeMomentsContextMenuApi.MenuItem(
                777014,
                "一键转发",
                SendIcon,
                { _, _ -> true },
            ) { moment ->
                try {
                    quickRepostMoment(moment)
                } catch (e: Throwable) {
                    WeLogger.e(TAG, "quick forward failed", e)
                }
            },
        )
    }

    private fun getCachedImagePath(media: TimelineObjectProto.MediaObjProto,
                                   nativeMedia: Any): String? {
        return try {
            val pg = WeMomentsApi.methodGetAccSnsPath.method.invoke(null) as String
            val mediaId = media.id ?: return null
            val dir = WeMomentsApi.methodGetMediaFilePath.method.invoke(null, pg, mediaId) as String

            val bigName = WeMomentsApi.methodGetSnsBigName.method.invoke(null, nativeMedia) as String
            val bigPath = dir + bigName

            val exists = WeMomentsApi.vfsFileExists(bigPath)
            if (exists) {
                bigPath
            } else {
                showToast("警告: 正在使用缩略图, 建议先查看一次图片以下载原图!")
                val thumbName = WeMomentsApi.methodGetSnsThumbName.method.invoke(null, nativeMedia) as String
                val thumbPath = dir + thumbName
                if (WeMomentsApi.vfsFileExists(thumbPath)) {
                    thumbPath
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            showToast("获取缓存图片路径失败!")
            WeLogger.e(TAG, "failed to get cached image path", e)
            null
        }
    }

    private fun copyVfsFile(src: String, dst: String): Boolean {
        return WeMomentsApi.copyVfsFile(src, dst)
    }

    private fun getNativeMediaListFromNativeTimeline(nativeTimeline: Any): LinkedList<*> {
        val nativeContentObj = nativeTimeline.reflekt().getField("ContentObj")!!
        return nativeContentObj.reflekt().lastField {
            type = LinkedList::class
        }.get()!! as LinkedList<*>
    }

    // --- 提取的共享逻辑公共方法 ---

    private data class MomentData(
        val activity: Context,
        val contentText: String,
        val type: Int,
        val mediaList: List<TimelineObjectProto.MediaObjProto>,
        val nativeMediaList: LinkedList<*>
    )

    private fun extractMomentData(context: WeMomentsContextMenuApi.MomentsContext): MomentData? {
        val activity = context.activity
        val snsInfo = context.snsInfo ?: return null
        val timelineProto = WeMomentsApi.getTimelineProto(snsInfo) ?: return null
        val contentObj = timelineProto.contentObj ?: return null
        val nativeTimeline = context.timelineObject ?: return null

        return MomentData(
            activity = activity,
            contentText = timelineProto.contentDesc ?: "",
            type = contentObj.type,
            mediaList = contentObj.mediaList,
            nativeMediaList = getNativeMediaListFromNativeTimeline(nativeTimeline)
        )
    }

    private fun prepareImagePaths(mediaList: List<TimelineObjectProto.MediaObjProto>, nativeMediaList: LinkedList<*>): ArrayList<String>? {
        if (mediaList.isEmpty()) return null
        val tempPaths = ArrayList<String>()
        for (index in mediaList.indices) {
            val cachedPath = getCachedImagePath(mediaList[index], nativeMediaList[index]) ?: return null
            tempPaths.add(cachedPath)
        }
        return tempPaths
    }

    private fun fetchVideoPath(nativeMediaList: LinkedList<*>): String? {
        if (nativeMediaList.isEmpty()) return null
        val nativeMediaObj = nativeMediaList[0] ?: return null

        return runCatching {
            WeMomentsApi.methodGetSnsVideoPath.method.invoke(null, nativeMediaObj) as? String
        }.getOrElse {
            WeLogger.i(TAG, "err", it)
            null
        }
    }

    // --- 核心业务实现 ---

    private fun repostMoment(context: WeMomentsContextMenuApi.MomentsContext) {
        val data = extractMomentData(context) ?: return
        val activity = data.activity
        val contentText = data.contentText

        when (data.type) {
            1 -> { // 图片
                val tempPaths = prepareImagePaths(data.mediaList, data.nativeMediaList)
                if (tempPaths == null) {
                    showToast(activity, "未找到本地缓存的图片!")
                    return
                }

                activity.startActivity(Intent {
                    setClassName(activity, "com.tencent.mm.plugin.sns.ui.SnsUploadUI")
                    putStringArrayListExtra("sns_kemdia_path_list", tempPaths)
                    putExtra("Kdescription", contentText)
                })
            }
            15, 5 -> { // 视频
                val videoPath = fetchVideoPath(data.nativeMediaList)
                if (videoPath == null) {
                    showToast(activity, "未找到本地缓存的视频, 请播放一次后再转发!")
                    return
                }

                val tempVideo = File(activity.externalCacheDir, "wekit_repost_${System.currentTimeMillis()}.mp4")
                val tempPath = tempVideo.absolutePath

                if (copyVfsFile(videoPath, tempPath)) {
                    activity.startActivity(Intent {
                        setClassName(activity, "com.tencent.mm.plugin.sns.ui.SnsUploadUI")
                        putExtra("Ksnsupload_type", 14)
                        putExtra("KSightPath", tempPath)
                        putExtra("KSightThumbPath", tempPath)
                        putExtra("Kdescription", contentText)
                    })
                } else {
                    showToast("视频文件准备失败!")
                }
            }
            else -> { // 文字
                activity.startActivity(Intent {
                    setClassName(activity, "com.tencent.mm.plugin.sns.ui.SnsUploadUI")
                    putExtra("Ksnsupload_type", 9)
                    putExtra("Kdescription", contentText)
                })
            }
        }
    }

    private fun quickRepostMoment(context: WeMomentsContextMenuApi.MomentsContext) {
        val data = extractMomentData(context) ?: return
        val activity = data.activity
        val contentText = data.contentText

        showToast(activity, "正在一键转发...")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = when (data.type) {
                    1 -> { // 图片
                        val tempPaths = prepareImagePaths(data.mediaList, data.nativeMediaList)
                        if (tempPaths == null) {
                            showToastSuspend(activity, "未找到本地缓存的图片!")
                            false
                        } else {
                            val helper = WeMomentsApi.ctorUploadPackHelper.constructor.newInstance(1, null)
                            WeMomentsApi.methodSetContentDes.method.invoke(helper, contentText)
                            tempPaths.forEach { path ->
                                WeMomentsApi.methodAddImageMediaObjByPath.method.invoke(helper, path, "")
                            }
                            val localId = WeMomentsApi.methodCommit.method.invoke(helper) as Int
                            localId > 0
                        }
                    }
                    15, 5 -> { // 视频
                        val videoPath = fetchVideoPath(data.nativeMediaList)
                        if (videoPath == null) {
                            showToastSuspend(activity, "未找到本地缓存的视频, 请播放一次后再转发!")
                            false
                        } else {
                            val tempVideo = File(activity.externalCacheDir, "wekit_repost_bg_${System.currentTimeMillis()}.mp4")
                            val tempPath = tempVideo.absolutePath

                            if (copyVfsFile(videoPath, tempPath)) {
                                val helper = WeMomentsApi.ctorUploadPackHelper.constructor.newInstance(15, null)
                                WeMomentsApi.methodSetContentDes.method.invoke(helper, contentText)
                                WeMomentsApi.methodAddSightObjectByPath.method.invoke(helper, tempPath, tempPath, "", "")
                                val localId = WeMomentsApi.methodCommit.method.invoke(helper) as Int
                                localId > 0
                            } else {
                                false
                            }
                        }
                    }
                    else -> { // 文字
                        val helper = WeMomentsApi.ctorUploadPackHelper.constructor.newInstance(2, null)
                        WeMomentsApi.methodSetContentDes.method.invoke(helper, contentText)
                        val localId = WeMomentsApi.methodCommit.method.invoke(helper) as Int
                        localId > 0
                    }
                }

                if (success) {
                    showToastSuspend(activity, "已加入发送队列")
                } else {
                    showToastSuspend(activity, "转发失败!")
                }
            } catch (e: Exception) {
                WeLogger.e(TAG, "failed to quick repost", e)
                showToastSuspend(activity, "转发出现异常! 错因:  ${e.message}")
            }
        }
    }
}
