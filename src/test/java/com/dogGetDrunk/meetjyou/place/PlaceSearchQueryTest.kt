package com.dogGetDrunk.meetjyou.place

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

// AT-007
class PlaceSearchQueryTest : BehaviorSpec({
    given("한글 검색어") {
        then("2글자 토큰을 모두 필수 항목으로 만든다") {
            PlaceSearchQuery.of("카페").terms shouldBe "+\"카페\""
            PlaceSearchQuery.of("스타벅스").terms shouldBe "+\"스타\" +\"타벅\" +\"벅스\""
        }
        then("같은 토큰은 한 번만 넣는다") {
            PlaceSearchQuery.of("하하하").terms shouldBe "+\"하하\""
        }
    }
    given("불용어를 포함한 영문 토큰") {
        then("그 토큰만 빼고 나머지로 찾는다") {
            // Pi, iz, za, aH contain stopword "i" or "a"; zz, Hu, ut stay.
            PlaceSearchQuery.of("PizzaHut").terms shouldBe "+\"zz\" +\"Hu\" +\"ut\""
        }
        then("쓸 토큰이 하나도 없으면 terms는 null") {
            PlaceSearchQuery.of("asia").terms shouldBe null
        }
    }
    given("큰따옴표가 섞인 검색어") {
        then("terms와 LIKE에서는 빼고, 정확 일치는 입력 그대로 비교한다") {
            val query = PlaceSearchQuery.of("카\"페")
            query.terms shouldBe "+\"카페\""
            query.likePattern shouldBe "%카페%"
            query.exact shouldBe "카\"페"
        }
        then("빼고 나서 1글자면 terms는 null") {
            PlaceSearchQuery.of("카\"").terms shouldBe null
        }
    }
    given("LIKE 특수문자") {
        then("이스케이프 문자로 감싼다") {
            PlaceSearchQuery.of("50%_off!").likePattern shouldBe "%50!%!_off!!%"
        }
    }
    given("문자·숫자·밑줄이 아닌 문자") {
        then("단어를 나누고 토큰에 넣지 않는다") {
            PlaceSearchQuery.of("50%할인").terms shouldBe "+\"50\" +\"할인\""
            PlaceSearchQuery.of("언더_바").terms shouldBe "+\"언더\" +\"더_\" +\"_바\""
        }
        then("1글자 단어는 항목을 만들지 않는다") {
            PlaceSearchQuery.of("50%할").terms shouldBe "+\"50\""
            PlaceSearchQuery.of("!표").terms shouldBe null
            PlaceSearchQuery.of("0%할").terms shouldBe null
        }
        then("보조 평면 문자(이모지)도 구분자로 본다") {
            PlaceSearchQuery.of("카😀페").terms shouldBe null
            PlaceSearchQuery.of("카페😀").terms shouldBe "+\"카페\""
        }
    }
})
