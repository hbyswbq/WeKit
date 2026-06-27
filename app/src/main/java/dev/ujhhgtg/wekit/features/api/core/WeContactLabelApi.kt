package dev.ujhhgtg.wekit.features.api.core

import dev.ujhhgtg.wekit.features.api.core.models.WeContact
import dev.ujhhgtg.wekit.features.core.ApiFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.utils.WeLogger

/**
 * Contact label management API for BeanShell scripts.
 * Mirrors WAuxv's getContactLabelList, getContactByLabelId, getContactByLabelName, modifyContactLabelList.
 *
 * WAuxv original: uses w23.r (ContactLabelStorage) and z23/ NetScene classes via DexKit.
 * WeKit: queries WeChat's SQLite database directly via WeDatabaseApi — simpler, no DexKit needed.
 *
 * WeChat label table (from wechat_8069): contactlabel_ext (labelID, labelName, labelPYFull, labelPYShort,
 * createTime, isTemporary, lastUseTime). Contact-label bindings in rcontact field_contactLabels.
 */
@Feature(name = "联系人标签服务", categories = ["API"], description = "提供联系人标签查询与修改能力")
object WeContactLabelApi : ApiFeature() {

    private val TAG = "WeContactLabelApi"

    data class ContactLabel(val labelId: Int, val labelName: String)

    /**
     * Get all contact labels.
     * @return List of ContactLabel sorted alphabetically by name
     */
    fun getAllLabels(): List<ContactLabel> {
        return try {
            val cursor = WeDatabaseApi.rawQuery(
                "SELECT labelID, labelName FROM contactlabel_ext ORDER BY labelName"
            )
            val labels = mutableListOf<ContactLabel>()
            cursor.use {
                while (it.moveToNext()) {
                    labels.add(
                        ContactLabel(
                            it.getInt(it.getColumnIndexOrThrow("labelID")),
                            it.getString(it.getColumnIndexOrThrow("labelName"))
                        )
                    )
                }
            }
            labels
        } catch (e: Exception) {
            WeLogger.e(TAG, "getAllLabels failed", e)
            emptyList()
        }
    }

    /**
     * Get contacts belonging to a specific label, by label ID.
     */
    fun getContactsByLabelId(labelId: Int): List<String> {
        return try {
            val raw = WeDatabaseApi.rawQuery(
                "SELECT username FROM rcontact WHERE field_contactLabels LIKE ?",
                arrayOf("%$labelId%")
            )
            val wxids = mutableListOf<String>()
            raw.use {
                while (it.moveToNext()) {
                    wxids.add(it.getString(0))
                }
            }
            wxids
        } catch (e: Exception) {
            WeLogger.e(TAG, "getContactsByLabelId failed", e)
            emptyList()
        }
    }

    /**
     * Get contacts belonging to a specific label, by label name.
     * First resolves the label name to label ID, then queries contacts.
     */
    fun getContactsByLabelName(labelName: String): List<String> {
        return try {
            val labelId = getLabelIdByName(labelName) ?: return emptyList()
            getContactsByLabelId(labelId)
        } catch (e: Exception) {
            WeLogger.e(TAG, "getContactsByLabelName failed", e)
            emptyList()
        }
    }

    fun modifyLabel(labelName: String, memberWxIds: List<String>) {
        try {
            WeLogger.i(TAG, "modifyLabel: $labelName with ${memberWxIds.size} members")
            val existingId = getLabelIdByName(labelName)
            if (existingId != null) {
                WeDatabaseApi.rawQuery("UPDATE contactlabel_ext SET labelName = ? WHERE labelID = ?", arrayOf(labelName, existingId.toString()))
            } else {
                val newId = (System.currentTimeMillis() / 1000).toInt()
                val now = System.currentTimeMillis()
                WeDatabaseApi.rawQuery("INSERT INTO contactlabel_ext (labelID, labelName, createTime, lastUseTime) VALUES (?, ?, ?, ?)", arrayOf(newId.toString(), labelName, now.toString(), now.toString()))
            }
        } catch (e: Exception) { WeLogger.e(TAG, "modifyLabel failed", e) }
    }

    /**
     * Get a label ID from its name.
     */
    private fun getLabelIdByName(labelName: String): Int? {
        val cursor = WeDatabaseApi.rawQuery(
            "SELECT labelID FROM contactlabel_ext WHERE labelName = ?",
            arrayOf(labelName)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return null
    }
}
