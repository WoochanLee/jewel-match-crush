package com.afinit.jewelmatchcrush

import kotlin.math.abs

/**
 * 게임 보드를 관리하는 클래스
 * 8x8 그리드의 보석들을 관리하고 게임 로직을 처리합니다
 */
class Board {
    private val grid: Array<Array<Jewel?>> = Array(BOARD_SIZE) { arrayOfNulls(BOARD_SIZE) }
    var score: Int = 0
        private set

    init {
        initialize()
    }

    /**
     * 보드를 초기화하고 매치가 없는 초기 상태를 생성합니다
     */
    private fun initialize() {
        // 먼저 랜덤하게 보석을 배치
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                grid[row][col] = Jewel(JewelType.random(), row, col)
            }
        }

        // 초기 매치가 있다면 제거
        while (hasMatches()) {
            removeMatches()
            applyGravity()
            fillEmptySpaces()
        }
    }

    /**
     * 두 보석의 위치를 강제로 교환합니다 (유효성 검사 없이)
     */
    fun forceSwapJewels(row1: Int, col1: Int, row2: Int, col2: Int) {
        // 범위 체크
        if (!isValidPosition(row1, col1) || !isValidPosition(row2, col2)) {
            return
        }

        // 스왑
        val temp = grid[row1][col1]
        grid[row1][col1] = grid[row2][col2]
        grid[row2][col2] = temp

        // 행/열 정보 업데이트
        grid[row1][col1]?.apply {
            row = row1
            col = col1
        }
        grid[row2][col2]?.apply {
            row = row2
            col = col2
        }
    }

    /**
     * 두 보석을 스왑했을 때 매치가 발생하는지 확인합니다
     * @return 매치가 발생하면 true, 아니면 false
     */
    fun isValidSwap(row1: Int, col1: Int, row2: Int, col2: Int): Boolean {
        // 범위 체크
        if (!isValidPosition(row1, col1) || !isValidPosition(row2, col2)) {
            return false
        }

        // 인접한 위치인지 체크
        if (!isAdjacent(row1, col1, row2, col2)) {
            return false
        }

        // 임시로 스왑
        val temp = grid[row1][col1]
        grid[row1][col1] = grid[row2][col2]
        grid[row2][col2] = temp

        // 매치 확인
        val hasMatch = hasMatches()

        // 원상복구
        val temp2 = grid[row1][col1]
        grid[row1][col1] = grid[row2][col2]
        grid[row2][col2] = temp2

        return hasMatch
    }

    /**
     * 두 보석의 위치를 교환합니다 (기존 메서드 - 호환성 유지)
     * @return 스왑이 유효한 경우(매치가 발생하는 경우) true, 아니면 false
     */
    fun swapJewels(row1: Int, col1: Int, row2: Int, col2: Int): Boolean {
        val isValid = isValidSwap(row1, col1, row2, col2)
        if (isValid) {
            forceSwapJewels(row1, col1, row2, col2)
        }
        return isValid
    }

    /**
     * 두 위치가 인접한지 확인합니다
     */
    private fun isAdjacent(row1: Int, col1: Int, row2: Int, col2: Int): Boolean {
        val rowDiff = abs(row1 - row2)
        val colDiff = abs(col1 - col2)
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
    }

    /**
     * 위치가 유효한지 확인합니다
     */
    private fun isValidPosition(row: Int, col: Int): Boolean =
        row in 0 until BOARD_SIZE && col in 0 until BOARD_SIZE

    /**
     * 보드에 매치(3개 이상 연속)가 있는지 확인합니다
     */
    fun hasMatches(): Boolean = findMatches().isNotEmpty()

    /**
     * 모든 매치를 찾아 반환합니다
     * @return 매치된 보석들의 집합
     */
    fun findMatches(): Set<Jewel> {
        val matches = mutableSetOf<Jewel>()

        // 가로 방향 매치 검사
        for (row in 0 until BOARD_SIZE) {
            var col = 0
            while (col < BOARD_SIZE - 2) {
                val jewel = grid[row][col] ?: run { col++; continue }
                val type = jewel.type
                val currentMatch = mutableListOf(jewel)

                // 같은 타입이 연속되는지 확인
                var checkCol = col + 1
                while (checkCol < BOARD_SIZE && grid[row][checkCol]?.type == type) {
                    grid[row][checkCol]?.let { currentMatch.add(it) }
                    checkCol++
                }

                // 3개 이상이면 매치
                if (currentMatch.size >= 3) {
                    matches.addAll(currentMatch)
                }
                col++
            }
        }

        // 세로 방향 매치 검사
        for (col in 0 until BOARD_SIZE) {
            var row = 0
            while (row < BOARD_SIZE - 2) {
                val jewel = grid[row][col] ?: run { row++; continue }
                val type = jewel.type
                val currentMatch = mutableListOf(jewel)

                // 같은 타입이 연속되는지 확인
                var checkRow = row + 1
                while (checkRow < BOARD_SIZE && grid[checkRow][col]?.type == type) {
                    grid[checkRow][col]?.let { currentMatch.add(it) }
                    checkRow++
                }

                // 3개 이상이면 매치
                if (currentMatch.size >= 3) {
                    matches.addAll(currentMatch)
                }
                row++
            }
        }

        return matches
    }

    /**
     * 특정 보석이 포함된 매치의 크기를 반환합니다
     * @return 해당 보석이 포함된 가장 큰 매치의 크기 (매치가 없으면 0)
     */
    fun getMatchSizeForJewel(jewel: Jewel): Int {
        var maxSize = 0

        // 가로 방향 매치 검사
        val horizontalMatch = mutableListOf<Jewel>()
        var col = jewel.col
        // 왼쪽으로 탐색
        while (col >= 0 && grid[jewel.row][col]?.type == jewel.type) {
            grid[jewel.row][col]?.let { horizontalMatch.add(it) }
            col--
        }
        // 오른쪽으로 탐색
        col = jewel.col + 1
        while (col < BOARD_SIZE && grid[jewel.row][col]?.type == jewel.type) {
            grid[jewel.row][col]?.let { horizontalMatch.add(it) }
            col++
        }
        if (horizontalMatch.size >= 3) {
            maxSize = maxOf(maxSize, horizontalMatch.size)
        }

        // 세로 방향 매치 검사
        val verticalMatch = mutableListOf<Jewel>()
        var row = jewel.row
        // 아래로 탐색
        while (row >= 0 && grid[row][jewel.col]?.type == jewel.type) {
            grid[row][jewel.col]?.let { verticalMatch.add(it) }
            row--
        }
        // 위로 탐색
        row = jewel.row + 1
        while (row < BOARD_SIZE && grid[row][jewel.col]?.type == jewel.type) {
            grid[row][jewel.col]?.let { verticalMatch.add(it) }
            row++
        }
        if (verticalMatch.size >= 3) {
            maxSize = maxOf(maxSize, verticalMatch.size)
        }

        return maxSize
    }

    /**
     * 매치된 보석들을 제거합니다 (특수 블록 생성 제외)
     * @param draggedJewel 드래그한 보석 (4개 이상 매치 시 특수 블록으로 변환)
     * @return 제거된 보석들의 리스트
     */
    fun removeMatches(draggedJewel: Jewel? = null): List<Jewel> {
        val matches = findMatches().toMutableSet()

        // 특수 블록이 매치에 포함되어 있으면 주변 블록도 추가
        addSurroundingJewelsForSpecials(matches)

        // 특수 블록 생성 대상 (하나만 생성)
        var convertedJewel: Jewel? = null

        // 5개 이상 매치 그룹을 먼저 찾아서 레인보우 블록 생성
        // (가로+세로 합쳐서 5개 이상인 경우도 포함)
        val fivePlusMatches = find5PlusMatchGroups()
        if (fivePlusMatches.isNotEmpty()) {
            for (matchGroup in fivePlusMatches) {
                // 드래그한 보석이 5개 이상 매치에 포함되면 우선적으로 레인보우 블록으로 변환
                if (draggedJewel != null && matchGroup.contains(draggedJewel) && !draggedJewel.isRainbow) {
                    draggedJewel.type = JewelType.RAINBOW
                    draggedJewel.isSpecial = false // 레인보우는 isSpecial 사용 안함
                    draggedJewel.isMarkedForRemoval = false
                    convertedJewel = draggedJewel
                    break
                }
            }
            // 드래그한 보석이 아니면 랜덤 블록을 레인보우로 변환
            if (convertedJewel == null) {
                for (matchGroup in fivePlusMatches) {
                    val candidates = matchGroup.filter { !it.isRainbow && !it.isSpecial && matches.contains(it) }
                    if (candidates.isNotEmpty()) {
                        val randomJewel = candidates.random()
                        randomJewel.type = JewelType.RAINBOW
                        randomJewel.isSpecial = false
                        randomJewel.isMarkedForRemoval = false
                        convertedJewel = randomJewel
                        break
                    }
                }
            }
        }

        // 4개 매치 그룹 처리 (5개 이상에서 이미 변환된 경우 제외)
        if (convertedJewel == null) {
            // 드래그한 보석이 4개 매치에 포함되면 우선적으로 특수 블록으로 변환
            if (draggedJewel != null && matches.contains(draggedJewel) && !draggedJewel.isSpecial && !draggedJewel.isRainbow) {
                val matchSize = getMatchSizeForJewel(draggedJewel)
                if (matchSize >= 4) {
                    draggedJewel.isSpecial = true
                    draggedJewel.isMarkedForRemoval = false
                    convertedJewel = draggedJewel
                }
            }

            // 드래그한 보석이 아닌 경우에도 4개 이상 매치가 있으면 랜덤 블록을 특수 블록으로 변환
            if (convertedJewel == null) {
                val fourPlusMatches = find4PlusMatchGroups()
                for (matchGroup in fourPlusMatches) {
                    // 이미 특수 블록이거나 레인보우인 것은 제외
                    val candidates = matchGroup.filter { !it.isSpecial && !it.isRainbow && matches.contains(it) }
                    if (candidates.isNotEmpty()) {
                        val randomJewel = candidates.random()
                        randomJewel.isSpecial = true
                        randomJewel.isMarkedForRemoval = false
                        convertedJewel = randomJewel
                        break // 한 번에 하나의 특수 블록만 생성
                    }
                }
            }
        }

        // 매치된 보석들 제거 (특수 블록으로 변환된 것 제외)
        val removedJewels = mutableListOf<Jewel>()
        matches.forEach { jewel ->
            if (jewel != convertedJewel) {
                jewel.isMarkedForRemoval = true
                grid[jewel.row][jewel.col] = null
                removedJewels.add(jewel)
            }
        }

        return removedJewels
    }

    /**
     * 5개 이상 매치 그룹들을 찾아 반환합니다.
     * 가로+세로 합쳐서 5개 이상인 경우도 포함합니다.
     */
    private fun find5PlusMatchGroups(): List<Set<Jewel>> {
        val result = mutableListOf<Set<Jewel>>()
        val processed = mutableSetOf<Jewel>()

        // 모든 매치된 보석들에 대해 연결된 매치 그룹 찾기
        val allMatches = findMatches()

        for (jewel in allMatches) {
            if (processed.contains(jewel) || jewel.isRainbow) continue

            // 이 보석과 연결된 모든 매치 보석들 찾기
            val connectedMatch = findConnectedMatchGroup(jewel, allMatches)

            if (connectedMatch.size >= 5) {
                result.add(connectedMatch)
            }

            processed.addAll(connectedMatch)
        }

        return result
    }

    /**
     * 특정 보석과 연결된 모든 매치 보석들을 찾습니다.
     * 가로/세로로 이어진 같은 타입의 매치된 보석들을 모두 포함합니다.
     */
    private fun findConnectedMatchGroup(startJewel: Jewel, allMatches: Set<Jewel>): Set<Jewel> {
        val connected = mutableSetOf<Jewel>()
        val toProcess = mutableListOf(startJewel)
        val type = startJewel.type

        while (toProcess.isNotEmpty()) {
            val current = toProcess.removeAt(0)
            if (connected.contains(current)) continue
            if (!allMatches.contains(current)) continue
            if (current.type != type) continue
            if (current.isRainbow) continue

            connected.add(current)

            // 인접한 4방향 확인 (상하좌우)
            val neighbors = listOf(
                grid.getOrNull(current.row - 1)?.getOrNull(current.col),
                grid.getOrNull(current.row + 1)?.getOrNull(current.col),
                grid.getOrNull(current.row)?.getOrNull(current.col - 1),
                grid.getOrNull(current.row)?.getOrNull(current.col + 1)
            )

            for (neighbor in neighbors) {
                if (neighbor != null && !connected.contains(neighbor) &&
                    allMatches.contains(neighbor) && neighbor.type == type && !neighbor.isRainbow) {
                    toProcess.add(neighbor)
                }
            }
        }

        return connected
    }

    /**
     * 레인보우 블록을 터트려서 가로, 세로, 대각선 전체를 제거합니다.
     * 다른 레인보우 블록이 포함되면 연쇄 폭발합니다.
     * @param rainbowJewel 터트릴 레인보우 블록
     * @return 제거된 보석들의 리스트
     */
    fun explodeRainbowJewel(rainbowJewel: Jewel): List<Jewel> {
        if (!rainbowJewel.isRainbow) return emptyList()

        val toRemove = mutableSetOf<Jewel>()
        val processedRainbows = mutableSetOf<Jewel>()
        val rainbowsToProcess = mutableListOf(rainbowJewel)

        // 연쇄 레인보우 폭발 처리
        while (rainbowsToProcess.isNotEmpty()) {
            val currentRainbow = rainbowsToProcess.removeAt(0)
            if (processedRainbows.contains(currentRainbow)) continue
            processedRainbows.add(currentRainbow)

            val row = currentRainbow.row
            val col = currentRainbow.col

            // 레인보우 블록 자신 추가
            toRemove.add(currentRainbow)

            // 가로줄 전체
            for (c in 0 until BOARD_SIZE) {
                grid[row][c]?.let { jewel ->
                    toRemove.add(jewel)
                    // 다른 레인보우 블록이면 연쇄 폭발 대상에 추가
                    if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                        rainbowsToProcess.add(jewel)
                    }
                }
            }

            // 세로줄 전체
            for (r in 0 until BOARD_SIZE) {
                grid[r][col]?.let { jewel ->
                    toRemove.add(jewel)
                    if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                        rainbowsToProcess.add(jewel)
                    }
                }
            }

            // 대각선 (좌상단 → 우하단 방향)
            var r = row - minOf(row, col)
            var c = col - minOf(row, col)
            while (r < BOARD_SIZE && c < BOARD_SIZE) {
                grid[r][c]?.let { jewel ->
                    toRemove.add(jewel)
                    if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                        rainbowsToProcess.add(jewel)
                    }
                }
                r++
                c++
            }

            // 대각선 (우상단 → 좌하단 방향)
            r = row - minOf(row, BOARD_SIZE - 1 - col)
            c = col + minOf(row, BOARD_SIZE - 1 - col)
            while (r < BOARD_SIZE && c >= 0) {
                grid[r][c]?.let { jewel ->
                    toRemove.add(jewel)
                    if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                        rainbowsToProcess.add(jewel)
                    }
                }
                r++
                c--
            }
        }

        // 모든 블록 제거
        val removedJewels = mutableListOf<Jewel>()
        toRemove.forEach { jewel ->
            jewel.isMarkedForRemoval = true
            grid[jewel.row][jewel.col] = null
            removedJewels.add(jewel)
        }

        return removedJewels
    }

    /**
     * 특수 블록이 포함된 경우 주변 8방향 블록을 matches에 추가합니다.
     * 주변 블록 중 특수 블록이 있으면 재귀적으로 그 주변도 추가합니다.
     * 레인보우 블록이 포함되면 가로/세로/대각선 전체를 추가합니다.
     */
    private fun addSurroundingJewelsForSpecials(matches: MutableSet<Jewel>) {
        val processed = mutableSetOf<Jewel>()
        val processedRainbows = mutableSetOf<Jewel>()
        val toProcess = matches.filter { it.isSpecial }.toMutableList()
        val rainbowsToProcess = matches.filter { it.isRainbow }.toMutableList()

        // 먼저 특수 블록 처리
        while (toProcess.isNotEmpty()) {
            val special = toProcess.removeAt(0)
            if (processed.contains(special)) continue
            processed.add(special)

            // 주변 8방향의 블록들을 추가
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val newRow = special.row + dr
                    val newCol = special.col + dc
                    if (isValidPosition(newRow, newCol)) {
                        grid[newRow][newCol]?.let { neighbor ->
                            if (!neighbor.isMarkedForRemoval && !matches.contains(neighbor)) {
                                matches.add(neighbor)
                                // 주변 블록이 특수 블록이면 다음에 처리
                                if (neighbor.isSpecial && !processed.contains(neighbor)) {
                                    toProcess.add(neighbor)
                                }
                                // 주변 블록이 레인보우 블록이면 레인보우 처리 대기열에 추가
                                if (neighbor.isRainbow && !processedRainbows.contains(neighbor)) {
                                    rainbowsToProcess.add(neighbor)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 레인보우 블록 연쇄 처리
        while (rainbowsToProcess.isNotEmpty()) {
            val rainbow = rainbowsToProcess.removeAt(0)
            if (processedRainbows.contains(rainbow)) continue
            processedRainbows.add(rainbow)

            val row = rainbow.row
            val col = rainbow.col

            // 가로줄 전체
            for (c in 0 until BOARD_SIZE) {
                grid[row][c]?.let { jewel ->
                    if (!matches.contains(jewel)) {
                        matches.add(jewel)
                        if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                            rainbowsToProcess.add(jewel)
                        }
                        if (jewel.isSpecial && !processed.contains(jewel)) {
                            toProcess.add(jewel)
                        }
                    }
                }
            }

            // 세로줄 전체
            for (r in 0 until BOARD_SIZE) {
                grid[r][col]?.let { jewel ->
                    if (!matches.contains(jewel)) {
                        matches.add(jewel)
                        if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                            rainbowsToProcess.add(jewel)
                        }
                        if (jewel.isSpecial && !processed.contains(jewel)) {
                            toProcess.add(jewel)
                        }
                    }
                }
            }

            // 대각선 (좌상단 → 우하단 방향)
            var r = row - minOf(row, col)
            var c = col - minOf(row, col)
            while (r < BOARD_SIZE && c < BOARD_SIZE) {
                grid[r][c]?.let { jewel ->
                    if (!matches.contains(jewel)) {
                        matches.add(jewel)
                        if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                            rainbowsToProcess.add(jewel)
                        }
                        if (jewel.isSpecial && !processed.contains(jewel)) {
                            toProcess.add(jewel)
                        }
                    }
                }
                r++
                c++
            }

            // 대각선 (우상단 → 좌하단 방향)
            r = row - minOf(row, BOARD_SIZE - 1 - col)
            c = col + minOf(row, BOARD_SIZE - 1 - col)
            while (r < BOARD_SIZE && c >= 0) {
                grid[r][c]?.let { jewel ->
                    if (!matches.contains(jewel)) {
                        matches.add(jewel)
                        if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                            rainbowsToProcess.add(jewel)
                        }
                        if (jewel.isSpecial && !processed.contains(jewel)) {
                            toProcess.add(jewel)
                        }
                    }
                }
                r++
                c--
            }
        }

        // 레인보우에 의해 추가된 특수 블록들도 처리
        while (toProcess.isNotEmpty()) {
            val special = toProcess.removeAt(0)
            if (processed.contains(special)) continue
            processed.add(special)

            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val newRow = special.row + dr
                    val newCol = special.col + dc
                    if (isValidPosition(newRow, newCol)) {
                        grid[newRow][newCol]?.let { neighbor ->
                            if (!neighbor.isMarkedForRemoval && !matches.contains(neighbor)) {
                                matches.add(neighbor)
                                if (neighbor.isSpecial && !processed.contains(neighbor)) {
                                    toProcess.add(neighbor)
                                }
                                if (neighbor.isRainbow && !processedRainbows.contains(neighbor)) {
                                    rainbowsToProcess.add(neighbor)
                                    // 새로운 레인보우가 추가되면 다시 레인보우 처리 루프 실행 필요
                                }
                            }
                        }
                    }
                }
            }
        }

        // 마지막으로 남은 레인보우 처리
        while (rainbowsToProcess.isNotEmpty()) {
            val rainbow = rainbowsToProcess.removeAt(0)
            if (processedRainbows.contains(rainbow)) continue
            processedRainbows.add(rainbow)

            val row = rainbow.row
            val col = rainbow.col

            for (c in 0 until BOARD_SIZE) {
                grid[row][c]?.let { jewel ->
                    if (!matches.contains(jewel)) {
                        matches.add(jewel)
                        if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                            rainbowsToProcess.add(jewel)
                        }
                    }
                }
            }

            for (r in 0 until BOARD_SIZE) {
                grid[r][col]?.let { jewel ->
                    if (!matches.contains(jewel)) {
                        matches.add(jewel)
                        if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                            rainbowsToProcess.add(jewel)
                        }
                    }
                }
            }

            var r = row - minOf(row, col)
            var c = col - minOf(row, col)
            while (r < BOARD_SIZE && c < BOARD_SIZE) {
                grid[r][c]?.let { jewel ->
                    if (!matches.contains(jewel)) {
                        matches.add(jewel)
                        if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                            rainbowsToProcess.add(jewel)
                        }
                    }
                }
                r++
                c++
            }

            r = row - minOf(row, BOARD_SIZE - 1 - col)
            c = col + minOf(row, BOARD_SIZE - 1 - col)
            while (r < BOARD_SIZE && c >= 0) {
                grid[r][c]?.let { jewel ->
                    if (!matches.contains(jewel)) {
                        matches.add(jewel)
                        if (jewel.isRainbow && !processedRainbows.contains(jewel)) {
                            rainbowsToProcess.add(jewel)
                        }
                    }
                }
                r++
                c--
            }
        }
    }

    /**
     * 4개 이상 연속 매치 그룹들을 찾아 반환합니다.
     */
    private fun find4PlusMatchGroups(): List<List<Jewel>> {
        val result = mutableListOf<List<Jewel>>()

        // 가로 방향 4개 이상 매치 검사
        for (row in 0 until BOARD_SIZE) {
            var col = 0
            while (col < BOARD_SIZE - 3) {
                val jewel = grid[row][col] ?: run { col++; continue }
                val type = jewel.type
                val currentMatch = mutableListOf(jewel)

                var checkCol = col + 1
                while (checkCol < BOARD_SIZE && grid[row][checkCol]?.type == type) {
                    grid[row][checkCol]?.let { currentMatch.add(it) }
                    checkCol++
                }

                if (currentMatch.size >= 4) {
                    result.add(currentMatch)
                }
                col++
            }
        }

        // 세로 방향 4개 이상 매치 검사
        for (col in 0 until BOARD_SIZE) {
            var row = 0
            while (row < BOARD_SIZE - 3) {
                val jewel = grid[row][col] ?: run { row++; continue }
                val type = jewel.type
                val currentMatch = mutableListOf(jewel)

                var checkRow = row + 1
                while (checkRow < BOARD_SIZE && grid[checkRow][col]?.type == type) {
                    grid[checkRow][col]?.let { currentMatch.add(it) }
                    checkRow++
                }

                if (currentMatch.size >= 4) {
                    result.add(currentMatch)
                }
                row++
            }
        }

        return result
    }

    /**
     * 중력을 적용하여 빈 공간으로 보석을 떨어뜨립니다
     * 위의 보석들이 아래로 떨어지고, 빈 공간은 맨 위에 생깁니다
     * @return 이동이 발생했는지 여부
     */
    fun applyGravity(): Boolean {
        var moved = false

        // 각 열에 대해 아래에서 위로 검사
        for (col in 0 until BOARD_SIZE) {
            var writeRow = 0 // 보석을 쓸 위치 (맨 아래부터 시작)

            // 아래에서 위로 올라가면서 null이 아닌 보석들을 아래로 밀착
            for (readRow in 0 until BOARD_SIZE) {
                grid[readRow][col]?.let { jewel ->
                    if (readRow != writeRow) {
                        // 보석을 아래로 이동
                        grid[writeRow][col] = jewel
                        jewel.row = writeRow
                        grid[readRow][col] = null
                        moved = true
                    }
                    writeRow++
                }
            }
        }

        return moved
    }

    /**
     * 빈 공간을 새로운 보석으로 채웁니다
     */
    fun fillEmptySpaces() {
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                if (grid[row][col] == null) {
                    grid[row][col] = Jewel(JewelType.random(), row, col)
                }
            }
        }
    }

    /**
     * 보드에 가능한 이동이 있는지 확인합니다
     * (게임 오버 방지)
     */
    fun hasPossibleMoves(): Boolean {
        // 모든 인접한 쌍에 대해 스왑을 시뮬레이션
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                // 오른쪽과 스왑 시도
                if (col < BOARD_SIZE - 1) {
                    if (wouldCreateMatch(row, col, row, col + 1)) {
                        return true
                    }
                }
                // 아래쪽과 스왑 시도
                if (row < BOARD_SIZE - 1) {
                    if (wouldCreateMatch(row, col, row + 1, col)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 두 보석을 스왑했을 때 매치가 발생하는지 확인합니다
     * (실제로 스왑하지 않고 시뮬레이션만 수행)
     */
    private fun wouldCreateMatch(row1: Int, col1: Int, row2: Int, col2: Int): Boolean {
        // 임시 스왑
        val temp = grid[row1][col1]
        grid[row1][col1] = grid[row2][col2]
        grid[row2][col2] = temp

        // 매치 확인
        val hasMatch = hasMatches()

        // 원상복구
        val temp2 = grid[row1][col1]
        grid[row1][col1] = grid[row2][col2]
        grid[row2][col2] = temp2

        return hasMatch
    }

    /**
     * 특정 위치의 보석을 반환합니다
     */
    fun getJewel(row: Int, col: Int): Jewel? =
        if (isValidPosition(row, col)) grid[row][col] else null

    /**
     * 점수를 추가합니다
     */
    fun addScore(points: Int) {
        score += points
    }

    /**
     * 점수를 초기화합니다
     */
    fun resetScore() {
        score = 0
    }

    /**
     * 보드를 완전히 리셋합니다 (점수 포함)
     */
    fun reset() {
        score = 0
        initialize()
    }

    /**
     * 힌트를 찾아 반환합니다.
     * @return 스왑할 보석과 매치될 보석들의 Pair, 없으면 null
     *         first: 스왑할 보석 (드래그 대상)
     *         second: 스왑 후 매치될 보석들 (함께 터질 블록들)
     */
    fun findHint(): Pair<Jewel, Set<Jewel>>? {
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val jewel = grid[row][col] ?: continue

                // 오른쪽과 스왑 시도
                if (col < BOARD_SIZE - 1) {
                    val rightJewel = grid[row][col + 1]
                    if (rightJewel != null) {
                        val matchedJewels = getMatchesAfterSwap(row, col, row, col + 1)
                        if (matchedJewels.isNotEmpty()) {
                            return Pair(jewel, matchedJewels)
                        }
                    }
                }

                // 아래쪽과 스왑 시도
                if (row < BOARD_SIZE - 1) {
                    val belowJewel = grid[row + 1][col]
                    if (belowJewel != null) {
                        val matchedJewels = getMatchesAfterSwap(row, col, row + 1, col)
                        if (matchedJewels.isNotEmpty()) {
                            return Pair(jewel, matchedJewels)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * 두 보석을 스왑했을 때 매치될 보석들을 반환합니다.
     */
    private fun getMatchesAfterSwap(row1: Int, col1: Int, row2: Int, col2: Int): Set<Jewel> {
        // 임시 스왑
        val temp = grid[row1][col1]
        grid[row1][col1] = grid[row2][col2]
        grid[row2][col2] = temp

        // 매치 확인
        val matches = findMatches()

        // 원상복구
        val temp2 = grid[row1][col1]
        grid[row1][col1] = grid[row2][col2]
        grid[row2][col2] = temp2

        return matches
    }

    companion object {
        const val BOARD_SIZE = 8
    }
}
