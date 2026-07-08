package com.dogGetDrunk.meetjyou.terms

enum class TermsType {
    AGE_OVER_14 {
        override val objectKeyPrefix = "terms/age"
    },
    TERMS_OF_SERVICE {
        override val objectKeyPrefix = "terms/service"
    },
    PRIVACY_COLLECTION_USE {
        override val objectKeyPrefix = "terms/privacy"
    },
    MARKETING_SNS_EVENTS {
        override val objectKeyPrefix = "terms/marketing-sns"
    },
    MARKETING_EMAIL_EVENTS {
        override val objectKeyPrefix = "terms/marketing-email"
    },
    ;

    abstract val objectKeyPrefix: String

    fun toObjectKey(version: String): String = "$objectKeyPrefix/v$version.html"
}
