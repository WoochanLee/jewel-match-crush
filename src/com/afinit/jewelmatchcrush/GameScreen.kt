package com.afinit.jewelmatchcrush

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound

/**
 * 메인 게임 화면
 * 게임 로직과 렌더링을 담당합니다
 */
class GameScreen : Screen {
    private val board = Board()
    private var gameState = GameState.IDLE
    private val camera = OrthographicCamera().apply {
        setToOrtho(false, 540f, 960f)
    }
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val font = BitmapFont().apply {
        color = Color.WHITE
        data.setScale(2f)
    }

    // 배경 음악 로드
    private val backgroundMusic: Music = Gdx.audio.newMusic(Gdx.files.internal("background.mp3"))
    // 효과음 로드
    private val crushSound: Sound = Gdx.audio.newSound(Gdx.files.internal("crush.wav"))
    private val gameOverSound: Sound = Gdx.audio.newSound(Gdx.files.internal("game_over.wav"))

    // 최고 점수 관리
    private val prefs = Gdx.app.getPreferences("JewelMatchCrush")
    private var highScore = prefs.getInteger("highScore", 0)

    // 보석 텍스처 로드 (3x3 그리드 구조, 마지막 줄 1개 좌측 정렬)
    private val jewelTexture = Texture(Gdx.files.internal("jewels.png"))
    private val jewelRegions = Array(7) { i ->
        val cols = 3
        val rows = 3
        val cellWidth = jewelTexture.width / cols
        val cellHeight = jewelTexture.height / rows

        val row = i / cols
        val col = i % cols
        TextureRegion(jewelTexture, col * cellWidth, row * cellHeight, cellWidth, cellHeight)
    }

    // 드래그 입력을 위한 변수
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var isDragging = false
    private var draggedJewel: Jewel? = null
    private var dragStartRow = -1
    private var dragStartCol = -1

    private var matchDelayTimer = 0f

    // 게임 타이머
    private var gameTimer = TIME_LIMIT
    private val isTimerMode = true

    // 스왑 애니메이션을 위한 변수
    private var swapJewel1: Jewel? = null
    private var swapJewel2: Jewel? = null
    private var isValidSwap = false

    // 마지막으로 드래그한 보석 (특수 블록 생성용)
    private var lastDraggedJewel: Jewel? = null

    // 연쇄(Combo) 카운터
    private var comboCount = 0
    private var comboTimer = 0f

    // 연쇄 중 발생하는 유효하지 않은 스왑을 관리하기 위한 상태 머신
    private sealed class InvalidSwapState(val jewel1: Jewel, val jewel2: Jewel) {
        // 1. 잘못된 위치로 이동하는 애니메이션 상태
        class AnimatingForward(jewel1: Jewel, jewel2: Jewel) : InvalidSwapState(jewel1, jewel2)
        // 2. 되돌아가기 전 잠시 기다리는 상태
        class WaitingToRevert(jewel1: Jewel, jewel2: Jewel, var timer: Float = 0f) : InvalidSwapState(jewel1, jewel2)
        // 3. 원래 위치로 돌아오는 애니메이션 상태
        class AnimatingBack(jewel1: Jewel, jewel2: Jewel) : InvalidSwapState(jewel1, jewel2)
    }
    private var invalidSwapState: InvalidSwapState? = null

    // 연쇄 중 유효한 스왑 애니메이션을 위한 변수
    private var concurrentSwap: Pair<Jewel, Jewel>? = null

    // 힌트 시스템
    private var idleTimer = 0f  // 블록이 터지지 않은 시간
    private var hintJewels: Set<Jewel>? = null  // 힌트로 표시할 보석들
    private var hintAnimTimer = 0f  // 힌트 애니메이션 타이머

    init {
        // 보드의 각 보석에 초기 위치 설정
        initializeJewelPositions()

        // 배경 음악 설정 및 재생
        backgroundMusic.isLooping = true
        backgroundMusic.volume = 0.5f // 볼륨 조절 (선택 사항)
        backgroundMusic.play()
    }

    /**
     * 보드의 오프셋을 계산합니다 (화면 중앙 배치)
     */
    private fun getBoardOffsetX(): Float {
        val boardWidth = Board.BOARD_SIZE * (JEWEL_SIZE + JEWEL_PADDING) - JEWEL_PADDING
        return (camera.viewportWidth - boardWidth) / 2
    }

    private fun getBoardOffsetY(): Float {
        val boardHeight = Board.BOARD_SIZE * (JEWEL_SIZE + JEWEL_PADDING) - JEWEL_PADDING
        return (camera.viewportHeight - boardHeight) / 2 - 50f // 중앙보다 약간 아래
    }

    /**
     * 각 보석의 렌더링 위치를 초기화합니다
     */
    private fun initializeJewelPositions() {
        for (row in 0 until Board.BOARD_SIZE) {
            for (col in 0 until Board.BOARD_SIZE) {
                board.getJewel(row, col)?.let { jewel ->
                    val x = getBoardOffsetX() + col * (JEWEL_SIZE + JEWEL_PADDING)
                    val y = getBoardOffsetY() + row * (JEWEL_SIZE + JEWEL_PADDING)
                    jewel.x = x
                    jewel.y = y
                    jewel.targetX = x
                    jewel.targetY = y
                }
            }
        }
    }

    /**
     * 보석의 목표 위치를 계산합니다
     */
    private fun updateJewelTargetPosition(jewel: Jewel) {
        val targetX = getBoardOffsetX() + jewel.col * (JEWEL_SIZE + JEWEL_PADDING)
        val targetY = getBoardOffsetY() + jewel.row * (JEWEL_SIZE + JEWEL_PADDING)
        jewel.targetX = targetX
        jewel.targetY = targetY
        jewel.isAnimating = true
    }

    override fun show() {}

    override fun render(delta: Float) {
        // 화면 클리어 (따뜻한 크림색 배경)
        Gdx.gl.glClearColor(0.98f, 0.96f, 0.90f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()

        // 게임 로직 업데이트
        update(delta)

        // 렌더링
        renderBoard()
        renderParticles()
        renderUI()
    }

    private fun update(delta: Float) {
        if (gameState == GameState.GAME_OVER) {
            return
        }

        // 타이머 업데이트
        if (isTimerMode && gameState != GameState.GAME_OVER) {
            gameTimer -= delta
            if (gameTimer <= 0) {
                gameTimer = 0f
                gameState = GameState.GAME_OVER
                gameOverSound.play()
                checkHighScore()
                return
            }
        }

        // 콤보 타이머 업데이트
        if (comboTimer > 0f) {
            comboTimer -= delta
            if (comboTimer <= 0f) {
                comboTimer = 0f
                comboCount = 0
            }
        }

        updateParticles(delta)

        // 항상 사용자 입력을 처리
        handleInput()

        // 연쇄 중 유효한 스왑 애니메이션 처리
        concurrentSwap?.let { (jewel1, jewel2) ->
            val animation1Done = jewel1.updateAnimation(delta, ANIMATION_SPEED)
            val animation2Done = jewel2.updateAnimation(delta, ANIMATION_SPEED)

            if (animation1Done && animation2Done) {
                // concurrentSwap 완료 시 매치 확인은 기존 상태 머신에 맡김
                // 매치가 있으면 MATCHING 상태로 전환하여 일관된 처리
                concurrentSwap = null
                if (board.hasMatches()) {
                    gameState = GameState.MATCHING
                    matchDelayTimer = 0f
                }
            }
        }

        // 연쇄 중 유효하지 않은 스왑 상태 머신 처리
        when (val state = invalidSwapState) {
            is InvalidSwapState.AnimatingForward -> {
                val done1 = state.jewel1.updateAnimation(delta, ANIMATION_SPEED)
                val done2 = state.jewel2.updateAnimation(delta, ANIMATION_SPEED)
                if (done1 && done2) {
                    invalidSwapState = InvalidSwapState.WaitingToRevert(state.jewel1, state.jewel2)
                }
            }
            is InvalidSwapState.WaitingToRevert -> {
                state.timer += delta
                if (state.timer >= REVERT_TIME) {
                    board.forceSwapJewels(state.jewel1.row, state.jewel1.col, state.jewel2.row, state.jewel2.col)
                    updateJewelTargetPosition(state.jewel1)
                    updateJewelTargetPosition(state.jewel2)
                    invalidSwapState = InvalidSwapState.AnimatingBack(state.jewel1, state.jewel2)
                }
            }
            is InvalidSwapState.AnimatingBack -> {
                val done1 = state.jewel1.updateAnimation(delta, ANIMATION_SPEED)
                val done2 = state.jewel2.updateAnimation(delta, ANIMATION_SPEED)
                if (done1 && done2) {
                    invalidSwapState = null
                }
            }
            null -> { /* Do nothing */ }
        }

        when (gameState) {
            GameState.IDLE -> {
                // 힌트 타이머 업데이트
                idleTimer += delta
                if (idleTimer >= HINT_DELAY && hintJewels == null) {
                    // 5초 이상 IDLE 상태면 힌트 표시
                    val hint = board.findHint()
                    if (hint != null) {
                        // 드래그 대상 보석과 매치될 보석들을 모두 포함
                        hintJewels = hint.second + hint.first
                    }
                }
                // 힌트 애니메이션 타이머 업데이트
                if (hintJewels != null) {
                    hintAnimTimer += delta
                }
            }
            GameState.SWAPPING -> updateSwapAnimation(delta)
            GameState.SWAPPING_BACK -> updateSwappingBackAnimation(delta)
            GameState.MATCHING -> {
                matchDelayTimer += delta
                if (matchDelayTimer >= MATCH_DELAY) {
                    matchDelayTimer = 0f
                    // 드래그한 보석 전달 (4개 이상 매치 시 특수 블록 생성)
                    val removedJewels = board.removeMatches(lastDraggedJewel)
                    if (removedJewels.isNotEmpty()) {
                        crushSound.play()
                        removedJewels.forEach { spawnExplosion(it) } // 이펙트 생성

                        val removedCount = removedJewels.size
                        comboCount++
                        val comboBonus = removedCount * 10 * comboCount
                        board.addScore(comboBonus)
                        comboTimer = COMBO_TIME_LIMIT
                        // 블록이 터졌으므로 힌트 타이머 리셋
                        resetHint()
                        startFallingState()
                    } else {
                        gameState = GameState.IDLE
                        lastDraggedJewel = null  // 매치 처리 완료 후 초기화
                    }
                }
            }
            GameState.FALLING -> updateFallingAnimation(delta)
            GameState.FILLING -> updateFillingAnimation(delta)
            GameState.GAME_OVER -> {}
        }
    }

    /**
     * 힌트 상태를 리셋합니다
     */
    private fun resetHint() {
        idleTimer = 0f
        hintJewels = null
        hintAnimTimer = 0f
    }

    /**
     * 레인보우 블록을 폭발시킵니다
     */
    private fun explodeRainbowJewel(rainbowJewel: Jewel) {
        val removedJewels = board.explodeRainbowJewel(rainbowJewel)
        if (removedJewels.isNotEmpty()) {
            crushSound.play()
            removedJewels.forEach { spawnExplosion(it) }

            val removedCount = removedJewels.size
            comboCount++
            val comboBonus = removedCount * 10 * comboCount
            board.addScore(comboBonus)
            comboTimer = COMBO_TIME_LIMIT

            // 힌트 리셋
            resetHint()

            // 낙하 상태로 전환
            startFallingState()
        }
    }

    /**
     * 사용자 입력을 처리합니다 (드래그 방식)
     */
    private fun handleInput() {
        // 터치 시작
        if (Gdx.input.justTouched()) {
            val touchPos = Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
            camera.unproject(touchPos)

            dragStartX = touchPos.x
            dragStartY = touchPos.y

            val col = ((touchPos.x - getBoardOffsetX()) / (JEWEL_SIZE + JEWEL_PADDING)).toInt()
            val row = ((touchPos.y - getBoardOffsetY()) / (JEWEL_SIZE + JEWEL_PADDING)).toInt()

            if (row in 0 until Board.BOARD_SIZE && col in 0 until Board.BOARD_SIZE) {
                val touchedJewel = board.getJewel(row, col)

                // 레인보우 블록 터치 시 즉시 폭발
                if (touchedJewel != null && touchedJewel.isRainbow && gameState == GameState.IDLE) {
                    explodeRainbowJewel(touchedJewel)
                    return
                }

                draggedJewel = touchedJewel
                dragStartRow = row
                dragStartCol = col
                isDragging = true
                // 드래그 시작 시 힌트 리셋
                resetHint()
            }
        }

        // 드래그 중
        if (isDragging && Gdx.input.isTouched()) {
            val touchPos = Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
            camera.unproject(touchPos)

            val deltaX = touchPos.x - dragStartX
            val deltaY = touchPos.y - dragStartY

            // 최소 드래그 거리
            val minDragDistance = JEWEL_SIZE / 3

            if (kotlin.math.abs(deltaX) > minDragDistance || kotlin.math.abs(deltaY) > minDragDistance) {
                // 드래그 방향 결정 (상하좌우 중 가장 큰 방향)
                val targetRow: Int
                val targetCol: Int

                if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                    // 좌우 드래그
                    targetRow = dragStartRow
                    targetCol = if (deltaX > 0) dragStartCol + 1 else dragStartCol - 1
                } else {
                    // 상하 드래그
                    targetCol = dragStartCol
                    targetRow = if (deltaY > 0) dragStartRow + 1 else dragStartRow - 1
                }

                // 유효한 범위 내의 인접한 블록인지 확인
                if (targetRow in 0 until Board.BOARD_SIZE && targetCol in 0 until Board.BOARD_SIZE) {
                    draggedJewel?.let { dragged ->
                        val targetJewel = board.getJewel(targetRow, targetCol)
                        targetJewel?.let { target ->
                            if (gameState == GameState.IDLE) {
                                // IDLE 상태일 때: 기존 스왑 로직 사용
                                isValidSwap = board.isValidSwap(dragStartRow, dragStartCol, targetRow, targetCol)
                                board.forceSwapJewels(dragStartRow, dragStartCol, targetRow, targetCol)
                                swapJewel1 = dragged
                                swapJewel2 = target
                                lastDraggedJewel = dragged  // 드래그한 보석 저장
                                updateJewelTargetPosition(dragged)
                                updateJewelTargetPosition(target)
                                gameState = GameState.SWAPPING
                            } else {
                                // IDLE 상태가 아닐 때: 연쇄 중 스왑
                                // 다른 스왑 애니메이션이 진행 중일 때는 새로운 입력을 받지 않음
                                if (invalidSwapState == null && concurrentSwap == null) {
                                    if (board.isValidSwap(dragStartRow, dragStartCol, targetRow, targetCol)) {
                                        // 유효한 스왑은 애니메이션 후 매치 제거
                                        board.forceSwapJewels(dragStartRow, dragStartCol, targetRow, targetCol)
                                        updateJewelTargetPosition(dragged)
                                        updateJewelTargetPosition(target)
                                        lastDraggedJewel = dragged  // 드래그한 보석 저장
                                        concurrentSwap = Pair(dragged, target)
                                    } else {
                                        // 유효하지 않은 스왑은 애니메이션 후 되돌림
                                        board.forceSwapJewels(dragStartRow, dragStartCol, targetRow, targetCol)
                                        updateJewelTargetPosition(dragged)
                                        updateJewelTargetPosition(target)
                                        invalidSwapState = InvalidSwapState.AnimatingForward(dragged, target)
                                    }
                                }
                            }
                        }
                    }
                }

                // 드래그 종료
                isDragging = false
                draggedJewel = null
            }
        }

        // 터치 종료
        if (!Gdx.input.isTouched()) {
            isDragging = false
            draggedJewel = null
        }
    }

    /**
     * 스왑 애니메이션을 업데이트합니다
     */
    private fun updateSwapAnimation(delta: Float) {
        val animation1Done = swapJewel1?.updateAnimation(delta, ANIMATION_SPEED) ?: true
        val animation2Done = swapJewel2?.updateAnimation(delta, ANIMATION_SPEED) ?: true

        if (animation1Done && animation2Done) {
            if (isValidSwap) {
                // 유효한 스왑 - 매치 확인으로 진행
                gameState = GameState.MATCHING
            } else {
                // 무효한 스왑 - 되돌리기 애니메이션 시작
                swapJewel1?.let { jewel1 ->
                    swapJewel2?.let { jewel2 ->
                        // 다시 스왑해서 원래 위치로
                        board.forceSwapJewels(jewel1.row, jewel1.col, jewel2.row, jewel2.col)
                        updateJewelTargetPosition(jewel1)
                        updateJewelTargetPosition(jewel2)
                        gameState = GameState.SWAPPING_BACK
                    }
                }
            }
        }
    }

    /**
     * 무효한 스왑을 되돌리는 애니메이션을 업데이트합니다
     */
    private fun updateSwappingBackAnimation(delta: Float) {
        val animation1Done = swapJewel1?.updateAnimation(delta, ANIMATION_SPEED) ?: true
        val animation2Done = swapJewel2?.updateAnimation(delta, ANIMATION_SPEED) ?: true

        if (animation1Done && animation2Done) {
            // 애니메이션 완료 - 다시 IDLE 상태로
            swapJewel1 = null
            swapJewel2 = null
            gameState = GameState.IDLE
        }
    }

    /**
     * 낙하 상태를 시작하고 보석 위치를 설정합니다.
     */
    private fun startFallingState() {
        gameState = GameState.FALLING

        // 동시 스왑 중인 블록이 없을 때만 중력 적용
        // 스왑 중인 블록이 있으면 해당 블록의 row가 잘못 변경될 수 있음
        if (concurrentSwap == null && invalidSwapState == null) {
            board.applyGravity()

            // 모든 보석의 목표 위치 업데이트
            for (row in 0 until Board.BOARD_SIZE) {
                for (col in 0 until Board.BOARD_SIZE) {
                    board.getJewel(row, col)?.let { jewel ->
                        updateJewelTargetPosition(jewel)
                    }
                }
            }
        }
    }

    /**
     * 중력 애니메이션을 업데이트합니다
     */
    private fun updateFallingAnimation(delta: Float) {
        var hasAnimation = false

        // 스왑 중인 블록이 없고 아직 중력이 적용되지 않았다면 중력 적용
        // (startFallingState에서 스왑 중이어서 중력을 못 적용한 경우)
        if (concurrentSwap == null && invalidSwapState == null) {
            if (board.applyGravity()) {
                // 중력으로 인해 블록 이동이 발생하면 목표 위치 업데이트
                for (row in 0 until Board.BOARD_SIZE) {
                    for (col in 0 until Board.BOARD_SIZE) {
                        board.getJewel(row, col)?.let { jewel ->
                            updateJewelTargetPosition(jewel)
                        }
                    }
                }
            }
        }

        // 애니메이션 업데이트 (스왑 중인 보석 제외)
        for (row in 0 until Board.BOARD_SIZE) {
            for (col in 0 until Board.BOARD_SIZE) {
                board.getJewel(row, col)?.let { jewel ->
                    if (jewel.isAnimating) {
                        if (jewel != concurrentSwap?.first && jewel != concurrentSwap?.second &&
                            jewel != invalidSwapState?.jewel1 && jewel != invalidSwapState?.jewel2) {
                            if (!jewel.updateAnimation(delta, ANIMATION_SPEED)) {
                                hasAnimation = true
                            }
                        }
                    }
                }
            }
        }

        // concurrentSwap 또는 invalidSwapState 애니메이션이 진행 중이면 다른 애니메이션이 계속되어야 함
        if (concurrentSwap != null || invalidSwapState != null) {
            hasAnimation = true
        }

        if (!hasAnimation) {
            gameState = GameState.FILLING
        }
    }

    /**
     * 새 보석 생성 애니메이션을 업데이트합니다
     */
    private fun updateFillingAnimation(delta: Float) {
        var hasAnimation = false

        // 스왑 중인 블록이 있으면 빈 공간 채우기를 지연
        // 스왑 중인 블록 위치가 잘못 처리될 수 있음
        if (concurrentSwap != null || invalidSwapState != null) {
            // 스왑 애니메이션만 업데이트하고 대기
            hasAnimation = true
        } else {
            // 먼저 빈 공간 확인 및 새 보석 생성
            board.fillEmptySpaces()

            // 새 보석 위치 초기화 (위에서 떨어지도록)
            // 각 열별로 새 보석의 개수를 추적하여 이어진 채로 내려오게 함
            //
            // 좌표 시스템: row 0이 화면 아래, row 7이 화면 위
            // 중력 적용 후 빈 공간은 위쪽(높은 row)에 생김
            //
            // "이어진 줄" 애니메이션을 위해:
            // - 모든 새 블록이 목표 위치로부터 같은 거리만큼 떨어진 곳에서 시작
            // - 이렇게 하면 아래쪽 블록이 먼저 도착하고, 위쪽 블록이 순차적으로 도착
            // - 블록들이 연결된 채로 내려오는 것처럼 보임
            val newJewelCountPerCol = IntArray(Board.BOARD_SIZE)

            // 먼저 각 열별로 새 블록 개수를 세기
            for (row in 0 until Board.BOARD_SIZE) {
                for (col in 0 until Board.BOARD_SIZE) {
                    board.getJewel(row, col)?.let { jewel ->
                        if (jewel.x == 0f && jewel.y == 0f) {
                            newJewelCountPerCol[col]++
                        }
                    }
                }
            }

            // 각 열별로 현재 처리 중인 블록 인덱스
            val currentIndexPerCol = IntArray(Board.BOARD_SIZE)

            // 위쪽 row부터 처리 (row 7 → row 0)
            // 가장 위쪽 블록이 가장 높은 시작점, 아래쪽 블록이 낮은 시작점
            for (row in Board.BOARD_SIZE - 1 downTo 0) {
                for (col in 0 until Board.BOARD_SIZE) {
                    board.getJewel(row, col)?.let { jewel ->
                        if (jewel.x == 0f && jewel.y == 0f) {
                            val targetX = getBoardOffsetX() + col * (JEWEL_SIZE + JEWEL_PADDING)
                            val targetY = getBoardOffsetY() + row * (JEWEL_SIZE + JEWEL_PADDING)

                            // 보드 위쪽(화면 밖)에서 시작
                            // 가장 위쪽 블록(row가 큰 것)이 가장 높은 곳에서 시작
                            // 아래로 갈수록 낮은 곳에서 시작하여 연결된 줄처럼 보이게 함
                            val boardTop = getBoardOffsetY() + Board.BOARD_SIZE * (JEWEL_SIZE + JEWEL_PADDING)
                            val totalNewBlocks = newJewelCountPerCol[col]
                            val blockIndex = currentIndexPerCol[col] // 0이 가장 위, totalNewBlocks-1이 가장 아래
                            val offset = (totalNewBlocks - 1 - blockIndex) * (JEWEL_SIZE + JEWEL_PADDING)

                            jewel.x = targetX
                            jewel.y = boardTop + offset
                            jewel.targetX = targetX
                            jewel.targetY = targetY
                            jewel.isAnimating = true
                            hasAnimation = true

                            currentIndexPerCol[col]++
                        }
                    }
                }
            }

            // 애니메이션 업데이트
            for (row in 0 until Board.BOARD_SIZE) {
                for (col in 0 until Board.BOARD_SIZE) {
                    board.getJewel(row, col)?.let { jewel ->
                        if (jewel.isAnimating) {
                            if (!jewel.updateAnimation(delta, ANIMATION_SPEED * 1.5f)) { // 조금 더 빠르게
                                hasAnimation = true
                            }
                        }
                    }
                }
            }
        }

        // 애니메이션이 끝나면 매치 확인
        if (!hasAnimation) {
            // 매치가 있는지 확인 (연쇄 효과)
            gameState = if (board.hasMatches()) {
                // 연쇄 매치 시에는 드래그한 보석 정보 초기화 (연쇄로 특수 블록 생성 방지)
                lastDraggedJewel = null
                GameState.MATCHING
            } else {
                // 가능한 이동이 없으면 게임 오버
                lastDraggedJewel = null
                if (!board.hasPossibleMoves()) {
                    gameOverSound.play()
                    checkHighScore()
                    GameState.GAME_OVER
                } else {
                    GameState.IDLE
                }
            }
        }
    }

    /**
     * 최고 점수를 확인하고 갱신합니다
     */
    private fun checkHighScore() {
        if (board.score > highScore) {
            highScore = board.score
            prefs.putInteger("highScore", highScore)
            prefs.flush()
        }
    }

    /**
     * 게임 보드를 렌더링합니다
     */
    private fun renderBoard() {
        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        // 보드 배경 그리기
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.9f, 0.85f, 0.8f, 1f) // 보드 배경색
        val boardWidth = Board.BOARD_SIZE * (JEWEL_SIZE + JEWEL_PADDING) - JEWEL_PADDING + 20f
        val boardHeight = Board.BOARD_SIZE * (JEWEL_SIZE + JEWEL_PADDING) - JEWEL_PADDING + 20f
        shapeRenderer.rect(getBoardOffsetX() - 10f, getBoardOffsetY() - 10f, boardWidth, boardHeight)
        shapeRenderer.end()

        // 보석 그리기
        batch.begin()

        for (row in 0 until Board.BOARD_SIZE) {
            for (col in 0 until Board.BOARD_SIZE) {
                board.getJewel(row, col)?.let { jewel ->
                    // 레인보우 블록은 별도로 렌더링하므로 건너뜀
                    if (!jewel.isMarkedForRemoval && !jewel.isRainbow) {
                        val region = jewelRegions[jewel.type.ordinal]

                        // 드래그 중인 보석에 하이라이트 (약간 키워서 표시)
                        if (jewel == draggedJewel) {
                            batch.draw(region, jewel.x - 5, jewel.y - 5, JEWEL_SIZE + 10, JEWEL_SIZE + 10)
                        } else {
                            // 일반 보석 그리기
                            batch.draw(region, jewel.x, jewel.y, JEWEL_SIZE, JEWEL_SIZE)
                        }
                    }
                }
            }
        }

        batch.end()

        // 특수 블록 테두리 그리기
        renderSpecialJewelBorders()

        // 레인보우 블록 렌더링
        renderRainbowJewels()

        // 힌트 테두리 그리기
        renderHintBorders()
    }

    /**
     * 특수 블록의 테두리를 렌더링합니다
     */
    private fun renderSpecialJewelBorders() {
        shapeRenderer.projectionMatrix = camera.combined

        // 특수 블록 테두리 (흰색 두꺼운 테두리 + 별 모양 효과)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        Gdx.gl.glLineWidth(4f)

        for (row in 0 until Board.BOARD_SIZE) {
            for (col in 0 until Board.BOARD_SIZE) {
                board.getJewel(row, col)?.let { jewel ->
                    if (jewel.isSpecial && !jewel.isMarkedForRemoval) {
                        // 반짝이는 흰색 테두리
                        shapeRenderer.color = Color.WHITE
                        shapeRenderer.rect(jewel.x - 2, jewel.y - 2, JEWEL_SIZE + 4, JEWEL_SIZE + 4)

                        // 금색 내부 테두리
                        shapeRenderer.color = Color.GOLD
                        shapeRenderer.rect(jewel.x + 2, jewel.y + 2, JEWEL_SIZE - 4, JEWEL_SIZE - 4)
                    }
                }
            }
        }

        shapeRenderer.end()
        Gdx.gl.glLineWidth(1f)

        // 특수 블록 별 표시 (채워진 별)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (row in 0 until Board.BOARD_SIZE) {
            for (col in 0 until Board.BOARD_SIZE) {
                board.getJewel(row, col)?.let { jewel ->
                    if (jewel.isSpecial && !jewel.isMarkedForRemoval) {
                        // 중앙에 작은 별 표시
                        val centerX = jewel.x + JEWEL_SIZE / 2
                        val centerY = jewel.y + JEWEL_SIZE / 2
                        shapeRenderer.color = Color.WHITE

                        // 십자 모양으로 별 효과
                        val starSize = 8f
                        shapeRenderer.rectLine(centerX - starSize, centerY, centerX + starSize, centerY, 3f)
                        shapeRenderer.rectLine(centerX, centerY - starSize, centerX, centerY + starSize, 3f)
                        // 대각선
                        val diagSize = starSize * 0.7f
                        shapeRenderer.rectLine(centerX - diagSize, centerY - diagSize, centerX + diagSize, centerY + diagSize, 2f)
                        shapeRenderer.rectLine(centerX - diagSize, centerY + diagSize, centerX + diagSize, centerY - diagSize, 2f)
                    }
                }
            }
        }
        shapeRenderer.end()
    }

    // 레인보우 애니메이션 타이머
    private var rainbowAnimTimer = 0f

    /**
     * 레인보우 블록을 렌더링합니다 (무지개 효과)
     */
    private fun renderRainbowJewels() {
        rainbowAnimTimer += Gdx.graphics.deltaTime

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = camera.combined

        for (row in 0 until Board.BOARD_SIZE) {
            for (col in 0 until Board.BOARD_SIZE) {
                board.getJewel(row, col)?.let { jewel ->
                    if (jewel.isRainbow && !jewel.isMarkedForRemoval) {
                        val centerX = jewel.x + JEWEL_SIZE / 2
                        val centerY = jewel.y + JEWEL_SIZE / 2

                        // 무지개 색상 순환
                        val hue = (rainbowAnimTimer * 100f) % 360f

                        // 배경 원 (무지개 색상)
                        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                        val rainbowColor = hsvToColor(hue, 0.8f, 1f)
                        shapeRenderer.color = rainbowColor
                        shapeRenderer.circle(centerX, centerY, JEWEL_SIZE / 2 - 2)

                        // 내부 흰색 원
                        shapeRenderer.color = Color(1f, 1f, 1f, 0.9f)
                        shapeRenderer.circle(centerX, centerY, JEWEL_SIZE / 2 - 8)

                        // 십자 패턴 (가로, 세로, 대각선 표시)
                        val lineLength = JEWEL_SIZE / 2 - 5
                        shapeRenderer.color = Color(hue / 360f, 0.5f, 1f, 0.8f)
                        // 가로선
                        shapeRenderer.rectLine(centerX - lineLength, centerY, centerX + lineLength, centerY, 3f)
                        // 세로선
                        shapeRenderer.rectLine(centerX, centerY - lineLength, centerX, centerY + lineLength, 3f)
                        // 대각선
                        val diagLength = lineLength * 0.7f
                        shapeRenderer.rectLine(centerX - diagLength, centerY - diagLength, centerX + diagLength, centerY + diagLength, 2f)
                        shapeRenderer.rectLine(centerX - diagLength, centerY + diagLength, centerX + diagLength, centerY - diagLength, 2f)

                        shapeRenderer.end()

                        // 외곽 테두리 (펄스 효과)
                        val pulse = (kotlin.math.sin(rainbowAnimTimer * 6f) + 1f) / 2f
                        val borderSize = 3f + pulse * 2f

                        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                        Gdx.gl.glLineWidth(borderSize)
                        shapeRenderer.color = hsvToColor((hue + 180f) % 360f, 1f, 1f)
                        shapeRenderer.circle(centerX, centerY, JEWEL_SIZE / 2)
                        shapeRenderer.end()
                        Gdx.gl.glLineWidth(1f)
                    }
                }
            }
        }

        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * HSV 색상을 RGB Color로 변환합니다
     */
    private fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = v - c

        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Color(r + m, g + m, b + m, 1f)
    }

    /**
     * 힌트 보석의 테두리를 렌더링합니다 (밝아졌다 흐려졌다 하는 효과)
     */
    private fun renderHintBorders() {
        val hints = hintJewels ?: return

        // 밝아졌다 흐려졌다 하는 효과 (sin 함수 사용)
        val pulseSpeed = 5f
        val pulse = (kotlin.math.sin(hintAnimTimer * pulseSpeed) + 1f) / 2f
        val alpha = pulse * 0.5f + 0.3f  // 0.3 ~ 0.8
        val scale = pulse * 4f + 2f  // 테두리 크기도 펄스 (2 ~ 6)

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = camera.combined

        // 1. 반투명 채우기 효과
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (jewel in hints) {
            if (!jewel.isMarkedForRemoval) {
                // 밝은 노란색 반투명 오버레이
                shapeRenderer.color = Color(1f, 1f, 0f, alpha * 0.4f)
                shapeRenderer.rect(jewel.x, jewel.y, JEWEL_SIZE, JEWEL_SIZE)
            }
        }
        shapeRenderer.end()

        // 2. 두꺼운 테두리
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        Gdx.gl.glLineWidth(5f)

        for (jewel in hints) {
            if (!jewel.isMarkedForRemoval) {
                // 바깥쪽 흰색 테두리 (글로우 효과)
                shapeRenderer.color = Color(1f, 1f, 1f, alpha * 0.7f)
                shapeRenderer.rect(jewel.x - scale - 1, jewel.y - scale - 1, JEWEL_SIZE + (scale + 1) * 2, JEWEL_SIZE + (scale + 1) * 2)

                // 안쪽 밝은 노란색 테두리
                shapeRenderer.color = Color(1f, 0.85f, 0f, alpha + 0.2f)
                shapeRenderer.rect(jewel.x - scale, jewel.y - scale, JEWEL_SIZE + scale * 2, JEWEL_SIZE + scale * 2)
            }
        }

        shapeRenderer.end()
        Gdx.gl.glLineWidth(1f)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * UI를 렌더링합니다 (점수, 타이머 등)
     */
    private fun renderUI() {
        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        // 1. 상단 정보 바 배경
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.5f, 0.3f, 0.1f, 1f) // 갈색 바
        shapeRenderer.rect(0f, camera.viewportHeight - 120f, camera.viewportWidth, 120f)

        // 2. 타이머 게이지 바
        if (isTimerMode) {
            val timerRatio = gameTimer / TIME_LIMIT
            val barWidth = camera.viewportWidth - 40f
            val barHeight = 15f // 조금 더 얇게
            val barX = 20f
            val barY = camera.viewportHeight - 100f // 위치 조정
            val radius = barHeight / 2

            // 게이지 배경 (라운드 처리 - 캡슐 형태)
            shapeRenderer.color = Color.valueOf("E0E0E0") // 연한 회색 배경
            // 좌측 원
            shapeRenderer.circle(barX + radius, barY + radius, radius)
            // 우측 원
            shapeRenderer.circle(barX + barWidth - radius, barY + radius, radius)
            // 중앙 사각형
            shapeRenderer.rect(barX + radius, barY, barWidth - 2 * radius, barHeight)

            // 게이지 내용 (모던한 색상)
            shapeRenderer.color = when {
                timerRatio > 0.5f -> Color.valueOf("4DB6AC") // Teal
                timerRatio > 0.2f -> Color.valueOf("FFD54F") // Amber
                else -> Color.valueOf("E57373") // Soft Red
            }

            // 채워지는 부분의 너비
            val fillWidth = barWidth * timerRatio

            // 채워지는 부분 그리기 (너비가 반지름보다 작을 때의 예외처리는 복잡하므로 단순화)
            if (fillWidth > 2 * radius) {
                // 좌측 원
                shapeRenderer.circle(barX + radius, barY + radius, radius)
                // 우측 원 (끝부분)
                shapeRenderer.circle(barX + fillWidth - radius, barY + radius, radius)
                // 중앙 사각형
                shapeRenderer.rect(barX + radius, barY, fillWidth - 2 * radius, barHeight)
            } else if (fillWidth > 0) {
                // 매우 짧을 때는 그냥 좌측에 작은 원으로 표시
                shapeRenderer.circle(barX + radius, barY + radius, radius * (fillWidth / (2 * radius)))
            }
        }
        shapeRenderer.end()

        // 3. 텍스트 정보 (점수, 콤보 등)
        batch.begin()

        // 점수 (상단 중앙, 위치 위로 조정)
        font.data.setScale(2.5f)
        val scoreText = "Score: ${board.score}"
        val layout = com.badlogic.gdx.graphics.g2d.GlyphLayout(font, scoreText)
        font.draw(batch, scoreText, (camera.viewportWidth - layout.width) / 2, camera.viewportHeight - 25f)

        // 최고 점수 표시 (점수 아래, 위치 위로 조정)
        font.data.setScale(1.5f)
        val highScoreText = "Best: $highScore"
        val hsLayout = com.badlogic.gdx.graphics.g2d.GlyphLayout(font, highScoreText)
        font.draw(batch, highScoreText, (camera.viewportWidth - hsLayout.width) / 2, camera.viewportHeight - 60f)

        // 콤보 표시 (보드 상단)
        if (comboCount > 0) {
            font.data.setScale(3f)
            font.color = Color.ORANGE
            val comboText = "$comboCount COMBO!"
            val comboLayout = com.badlogic.gdx.graphics.g2d.GlyphLayout(font, comboText)
            font.draw(batch, comboText, (camera.viewportWidth - comboLayout.width) / 2, getBoardOffsetY() + Board.BOARD_SIZE * (JEWEL_SIZE + JEWEL_PADDING) + 60f)
            font.color = Color.WHITE
        }

        // 게임 오버 메시지
        if (gameState == GameState.GAME_OVER) {
            // 반투명 배경
            batch.end()
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color(0f, 0f, 0f, 0.7f)
            shapeRenderer.rect(0f, 0f, camera.viewportWidth, camera.viewportHeight)
            shapeRenderer.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)
            batch.begin()

            val centerX = camera.viewportWidth / 2
            val centerY = camera.viewportHeight / 2

            font.data.setScale(3.5f)
            val gameOverText = "GAME OVER"
            val goLayout = com.badlogic.gdx.graphics.g2d.GlyphLayout(font, gameOverText)
            font.draw(batch, gameOverText, centerX - goLayout.width / 2, centerY + 100f)

            font.data.setScale(2.5f)
            val finalScoreText = "Final Score: ${board.score}"
            val fsLayout = com.badlogic.gdx.graphics.g2d.GlyphLayout(font, finalScoreText)
            font.draw(batch, finalScoreText, centerX - fsLayout.width / 2, centerY)

            val restartText = "Click to restart"
            val rsLayout = com.badlogic.gdx.graphics.g2d.GlyphLayout(font, restartText)
            font.draw(batch, restartText, centerX - rsLayout.width / 2, centerY - 80f)

            if (Gdx.input.justTouched()) {
                restart()
            }
        }

        batch.end()
    }

    /**
     * 게임을 재시작합니다
     */
    private fun restart() {
        gameState = GameState.IDLE
        isDragging = false
        draggedJewel = null
        dragStartRow = -1
        dragStartCol = -1
        matchDelayTimer = 0f
        gameTimer = TIME_LIMIT
        swapJewel1 = null
        swapJewel2 = null
        lastDraggedJewel = null
        comboCount = 0
        comboTimer = 0f
        invalidSwapState = null
        concurrentSwap = null
        particles.clear()
        // 힌트 리셋
        idleTimer = 0f
        hintJewels = null
        hintAnimTimer = 0f

        // 보드 리셋 (새로운 보석 배치)
        board.reset()
        initializeJewelPositions()
    }

    override fun resize(width: Int, height: Int) {
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
        // 화면 크기 변경 시 보드 위치 재계산
        initializeJewelPositions()
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {}

    // 파티클 시스템
    private val particles = com.badlogic.gdx.utils.Array<Particle>()

    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float,
        val color: Color, val size: Float
    )

    private fun spawnExplosion(jewel: Jewel) {
        val centerX = jewel.x + JEWEL_SIZE / 2
        val centerY = jewel.y + JEWEL_SIZE / 2

        // 보석 색상 파티클
        for (i in 0 until 15) {
            val angle = Math.random() * Math.PI * 2
            val speed = Math.random() * 200 + 100 // 100 ~ 300 speed
            val vx = (kotlin.math.cos(angle) * speed).toFloat()
            val vy = (kotlin.math.sin(angle) * speed).toFloat()
            val life = (Math.random() * 0.5 + 0.3).toFloat()
            val size = (Math.random() * 8 + 4).toFloat()

            particles.add(Particle(centerX, centerY, vx, vy, life, life, jewel.type.color, size))
        }

        // 흰색 반짝임
        for (i in 0 until 5) {
            val angle = Math.random() * Math.PI * 2
            val speed = Math.random() * 300 + 200
            val vx = (kotlin.math.cos(angle) * speed).toFloat()
            val vy = (kotlin.math.sin(angle) * speed).toFloat()
            val life = (Math.random() * 0.3 + 0.1).toFloat()
            val size = (Math.random() * 5 + 2).toFloat()

            particles.add(Particle(centerX, centerY, vx, vy, life, life, Color.WHITE, size))
        }
    }

    private fun updateParticles(delta: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= delta
            if (p.life <= 0) {
                iter.remove()
            } else {
                p.x += p.vx * delta
                p.y += p.vy * delta
                p.vy -= 500f * delta // Gravity for particles
            }
        }
    }

    private fun renderParticles() {
        if (particles.isEmpty) return

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        for (p in particles) {
            val alpha = p.life / p.maxLife
            shapeRenderer.color = Color(p.color.r, p.color.g, p.color.b, alpha)
            shapeRenderer.circle(p.x, p.y, p.size / 2)
        }

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        font.dispose()
        jewelTexture.dispose()
        backgroundMusic.dispose()
        crushSound.dispose()
        gameOverSound.dispose()
    }

    companion object {
        private const val JEWEL_SIZE = 60f
        private const val JEWEL_PADDING = 5f
        private const val ANIMATION_SPEED = 500f // 픽셀/초
        private const val MATCH_DELAY = 0.3f // 매치 제거 전 딜레이
        private const val TIME_LIMIT = 60f // 60초 제한
        private const val REVERT_TIME = 0.25f
        private const val COMBO_TIME_LIMIT = 2.0f
        private const val HINT_DELAY = 5.0f // 힌트 표시까지 대기 시간 (초)
    }
}
