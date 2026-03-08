package com.dogGetDrunk.meetjyou.image

import java.util.UUID

enum class ImageOperation {
    UPLOAD,
    DOWNLOAD
}

enum class ImageTarget {
    USER_PROFILE_ORIGINAL {
        override fun toObjectName(uuid: UUID): String =
            "image/user/profile/${uuid}-original.jpg"
    },
    USER_PROFILE_THUMBNAIL {
        override fun toObjectName(uuid: UUID): String =
            "image/user/profile/${uuid}-thumbnail.jpg"
    },
    POST_ORIGINAL {
        override fun toObjectName(uuid: UUID): String =
            "image/post/${uuid}-original.jpg"
    },
    POST_THUMBNAIL {
        override fun toObjectName(uuid: UUID): String =
            "image/post/${uuid}-thumbnail.jpg"
    },
    PARTY_ORIGINAL {
        override fun toObjectName(uuid: UUID): String =
            "image/party/${uuid}-original.jpg"
    },
    PARTY_THUMBNAIL {
        override fun toObjectName(uuid: UUID): String =
            "image/party/${uuid}-thumbnail.jpg"
    };

    abstract fun toObjectName(uuid: UUID): String
}
