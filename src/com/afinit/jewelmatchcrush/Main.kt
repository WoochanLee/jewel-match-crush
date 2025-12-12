package com.afinit.jewelmatchcrush

import com.badlogic.gdx.Game

/**
 * Jewel Match Crush 게임의 메인 클래스
 * 애니팡 스타일의 매치-3 퍼즐 게임입니다
 */
class Main : Game() {
    override fun create() {
        // 게임 화면으로 전환
        setScreen(GameScreen())
    }

    override fun dispose() {
        super.dispose()
        screen?.dispose()
    }
}
