package com.afinit.jewelmatchcrush

/**
 * 게임의 현재 상태를 나타내는 enum
 */
enum class GameState {
    /**
     * 사용자 입력을 기다리는 상태
     */
    IDLE,

    /**
     * 보석 스왑 애니메이션이 진행 중인 상태
     */
    SWAPPING,

    /**
     * 무효한 스왑을 되돌리는 애니메이션 상태
     */
    SWAPPING_BACK,

    /**
     * 매치를 확인하고 제거하는 상태
     */
    MATCHING,

    /**
     * 중력을 적용하여 보석이 떨어지는 상태
     */
    FALLING,

    /**
     * 새로운 보석을 생성하는 상태
     */
    FILLING,

    /**
     * 게임 종료 상태
     */
    GAME_OVER
}
