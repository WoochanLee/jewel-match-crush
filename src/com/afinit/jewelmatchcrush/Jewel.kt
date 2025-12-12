package com.afinit.jewelmatchcrush

import kotlin.math.sqrt

/**
 * 개별 보석을 나타내는 클래스
 * 보드의 각 셀에 위치하며, 타입과 위치 정보를 가집니다
 */
class Jewel(
    var type: JewelType,
    var row: Int,
    var col: Int
) {
    // 애니메이션을 위한 실제 렌더링 위치
    var x: Float = 0f
    var y: Float = 0f

    // 애니메이션 목표 위치
    var targetX: Float = 0f
    var targetY: Float = 0f

    // 매치되어 제거될 예정인지 여부
    var isMarkedForRemoval: Boolean = false

    // 애니메이션 진행 중인지 여부
    var isAnimating: Boolean = false

    // 특수 블록 여부 (4개 이상 매치 시 생성)
    var isSpecial: Boolean = false

    // 레인보우 블록 여부 (타입이 RAINBOW인지 확인)
    val isRainbow: Boolean
        get() = type == JewelType.RAINBOW

    /**
     * 목표 위치로 부드럽게 이동하는 애니메이션 업데이트
     * @param delta 프레임 간 시간 차이
     * @param speed 이동 속도
     * @return 애니메이션이 완료되었는지 여부
     */
    fun updateAnimation(delta: Float, speed: Float): Boolean {
        if (!isAnimating) {
            return true
        }

        val dx = targetX - x
        val dy = targetY - y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < 1f) {
            x = targetX
            y = targetY
            isAnimating = false
            return true
        }

        var moveDistance = speed * delta
        if (moveDistance > distance) {
            moveDistance = distance
        }

        x += (dx / distance) * moveDistance
        y += (dy / distance) * moveDistance

        return false
    }
}
