package com.example.dualcam

enum class EyeRefMode { DOMINANT, MIDPOINT }

data class Trial(
    val repetition: Int,     // 1 또는 2
    val distanceCm: Float,   // 30, 37.5, 45
    val mode: EyeRefMode,
    val orderIndex: Int      // 이 참가자의 전체 시행 중 순번 (1~12)
)

/**
 * 참가자 번호(1부터 시작)에 따라 12개 시행의 순서를 결정합니다.
 *
 * - 거리 순서: 3x3 라틴 스퀘어를 참가자 번호로 순환 배정하고, 두 반복 모두 같은 순서를 유지합니다.
 * - 보정 방식 순서: 반복 1에서는 참가자 홀/짝에 따라 결정하고,
 *   반복 2에서는 각 거리마다 순서를 반대로 하여 순서 효과를 줄입니다.
 *
 * 참가자 6명 기준: 1,4번 → 거리순서 그룹0 / 2,5번 → 그룹1 / 3,6번 → 그룹2
 */
object TrialSequencer {

    private val latinSquare = listOf(
        listOf(30f, 37.5f, 45f),
        listOf(37.5f, 45f, 30f),
        listOf(45f, 30f, 37.5f)
    )

    fun generate(participantId: Int): List<Trial> {
        require(participantId >= 1) { "participantId는 1 이상이어야 합니다" }

        val distanceOrder = latinSquare[(participantId - 1) % 3]
        val isOddParticipant = participantId % 2 == 1

        val trials = mutableListOf<Trial>()
        var order = 1

        for (rep in 1..2) {
            for (distance in distanceOrder) {
                val firstIsMidpoint = if (rep == 1) isOddParticipant else !isOddParticipant
                val modeOrder = if (firstIsMidpoint) {
                    listOf(EyeRefMode.MIDPOINT, EyeRefMode.DOMINANT)
                } else {
                    listOf(EyeRefMode.DOMINANT, EyeRefMode.MIDPOINT)
                }
                for (mode in modeOrder) {
                    trials.add(Trial(rep, distance, mode, order))
                    order++
                }
            }
        }
        return trials
    }

    /** 반복 1과 반복 2 사이, 몇 번째 시행 뒤에 30초 휴식을 넣을지 (6번째 시행 뒤) */
    const val REST_AFTER_ORDER_INDEX = 6
}
