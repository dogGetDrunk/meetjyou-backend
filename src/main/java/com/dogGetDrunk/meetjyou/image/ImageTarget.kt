package com.dogGetDrunk.meetjyou.image

import java.util.UUID

enum class ImageOperation {
    UPLOAD,
    DOWNLOAD
}

enum class ImageTarget {
    USER_PROFILE_ORIGINAL {
        override fun toObjectName(uuid: UUID): String =
            "user/profile/${uuid}-original.jpg"
    },
    USER_PROFILE_THUMBNAIL {
        override fun toObjectName(uuid: UUID): String =
            "user/profile/${uuid}-thumbnail.jpg"
    },
    POST_ORIGINAL {
        override fun toObjectName(uuid: UUID): String =
            "post/${uuid}-original.jpg"
    },
    POST_THUMBNAIL {
        override fun toObjectName(uuid: UUID): String =
            "post/${uuid}-thumbnail.jpg"
    },
    PARTY_ORIGINAL {
        override fun toObjectName(uuid: UUID): String =
            "party/${uuid}-original.jpg"
    },
    PARTY_THUMBNAIL {
        override fun toObjectName(uuid: UUID): String =
            "party/${uuid}-thumbnail.jpg"
    };

    abstract fun toObjectName(uuid: UUID): String
}
