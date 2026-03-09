package com.dogGetDrunk.meetjyou.terms.dto

import com.dogGetDrunk.meetjyou.terms.Terms
import com.dogGetDrunk.meetjyou.terms.TermsType

data class GetTermsResponse(
    val termsUuid: String,
    val type: TermsType,
    val version: String,
    val displayText: String,
    val required: Boolean,
    val hasContent: Boolean,
) {
    companion object {
        fun of(terms: Terms): GetTermsResponse {
            return GetTermsResponse(
                termsUuid = terms.uuid.toString(),
                type = terms.type,
                version = terms.version,
                displayText = terms.displayText,
                required = terms.required,
                hasContent = terms.contentObjectKey.isNotBlank(),
            )
        }
    }
}
