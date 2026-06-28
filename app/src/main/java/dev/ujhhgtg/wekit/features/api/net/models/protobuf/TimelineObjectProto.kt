@file:OptIn(ExperimentalSerializationApi::class)

package dev.ujhhgtg.wekit.features.api.net.models.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class TimelineObjectProto(
    @ProtoNumber(1) val id: String? = null,
    @ProtoNumber(2) val userName: String? = null,
    @ProtoNumber(3) val privated: Int = 0,
    @ProtoNumber(4) val createTime: Int = 0,
    @ProtoNumber(5) val contentDesc: String? = null,
    @ProtoNumber(8) val contentObj: ContentObjProto? = null
) {
    @Serializable
    data class ContentObjProto(
        @ProtoNumber(1) val id: String? = null,
        @ProtoNumber(2) val type: Int = 0,
        @ProtoNumber(3) val title: String? = null,
        @ProtoNumber(4) val description: String? = null,
        @ProtoNumber(5) val mediaList: List<MediaObjProto> = emptyList()
    )

    @Serializable
    data class MediaObjProto(
        @ProtoNumber(1) val id: String? = null,
        @ProtoNumber(2) val type: Int = 0,
        @ProtoNumber(3) val title: String? = null,
        @ProtoNumber(4) val description: String? = null,
        @ProtoNumber(6) val url: String? = null,
        @ProtoNumber(7) val width: Int = 0,
        @ProtoNumber(8) val height: Int = 0,
        @ProtoNumber(9) val thumbUrl: String? = null
    )
}
