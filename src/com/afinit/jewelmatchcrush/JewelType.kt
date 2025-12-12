package com.afinit.jewelmatchcrush

import com.badlogic.gdx.graphics.Color

/**
 * 보석의 종류를 정의하는 enum
 * 각 보석은 고유한 색상을 가집니다
 */
enum class JewelType(val color: Color) {
    RED(Color.valueOf("E57373")),
    ORANGE(Color.valueOf("FFB74D")),
    YELLOW(Color.valueOf("FFF176")),
    GREEN(Color.valueOf("81C784")),
    BLUE(Color.valueOf("64B5F6")),
    PURPLE(Color.valueOf("BA68C8")),
//    PINK(Color.valueOf("F06292")),
    // 레인보우 블록 (5개 이상 매치 시 생성되는 특수 블록)
    RAINBOW(Color.WHITE);

    companion object {
        // 일반 블록 타입들 (RAINBOW 제외)
        private val normalTypes = values().filter { it != RAINBOW }

        /**
         * 랜덤한 보석 타입을 반환합니다 (RAINBOW 제외)
         */
        fun random(): JewelType = normalTypes.random()
    }
}
