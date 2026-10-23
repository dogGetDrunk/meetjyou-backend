package com.dogGetDrunk.meetjyou.user.dto

// Bio: Korean (complete syllables + standalone jamo such as "ㅋㅋ"), English letters, digits, whitespace.
// Punctuation and emoji stay rejected per the product spec (MyPage > profile edit > bio field).
const val BIO_PATTERN = "^[a-zA-Z0-9가-힣ㄱ-ㅎㅏ-ㅣ\\s]*$"
