package com.example.dualcam

import kotlin.math.tan

/** 디스플레이 중심을 원점으로 하는 눈 위치 (미터 단위) */
data class EyePosition(val x: Float, val y: Float, val z: Float)

enum class ReferenceMode { DOMINANT_EYE, BINOCULAR_MIDPOINT }
enum class DominantEyeSide { LEFT, RIGHT }

/**
 * 카메라 시야각(FOV). Camera2 특성값(SENSOR_INFO_PHYSICAL_SIZE,
 * LENS_INFO_AVAILABLE_FOCAL_LENGTHS)으로 계산해서 넣는 것을 권장합니다.
 * 값을 못 구하면 일반적인 후면 카메라 대략값(기본값)을 씁니다. [확인 필요]
 */
data class CameraFov(val horizontalDeg: Float = 65f, val verticalDeg: Float = 50f)

/**
 * 눈 위치를 기준으로, 후면 카메라 영상에서 디스플레이에 표시해야 할 영역(4개 모서리)을
 * 계산합니다. 배경은 카메라로부터 backgroundDistanceM 만큼 떨어진 평면이라고 가정합니다
 * (논문과 동일한 단순화: 단일 평면 호모그래피).
 */
class ReprojectionMath(
    private val displayWidthM: Float,
    private val displayHeightM: Float,
    private val backgroundDistanceM: Float,
    private val cameraFov: CameraFov = CameraFov(),
    /** 후면 카메라가 디스플레이 중심에서 떨어진 위치 (m, 오른쪽/위가 +). 호모그래피 기준점 보정용 */
    private val backCamOffsetXM: Float = 0f,
    private val backCamOffsetYM: Float = 0f,
    /** 후면 프리뷰 뷰의 가로/세로 비율. PreviewView가 FILL_CENTER로 크롭하는 만큼 FOV를 보정. 0이면 보정 안 함 */
    private val previewAspect: Float = 0f
) {

    /**
     * 조건(기준점 모드)에 따라 실제 계산에 쓸 눈 위치를 결정합니다.
     * rawMidpoint: 전면 카메라로 추정한 "양안 중간점" 위치 (디스플레이 좌표계, m)
     */
    fun resolveReferencePoint(
        rawMidpoint: EyePosition,
        mode: ReferenceMode,
        dominantSide: DominantEyeSide,
        ipdM: Float
    ): EyePosition {
        return when (mode) {
            ReferenceMode.BINOCULAR_MIDPOINT -> rawMidpoint
            ReferenceMode.DOMINANT_EYE -> {
                val sign = if (dominantSide == DominantEyeSide.RIGHT) 1f else -1f
                // 전면 카메라 영상은 보통 좌우 반전(미러)되어 있습니다.
                // 실기기에서 참가자가 오른쪽으로 움직일 때 x가 올바른 방향으로 바뀌는지
                // 반드시 확인하세요. 반대라면 EyeTracker의 mirrorX를 바꾸세요. [확인 필요]
                rawMidpoint.copy(x = rawMidpoint.x + sign * (ipdM / 2f))
            }
        }
    }

    /**
     * 디스플레이 네 모서리에 대응하는, 후면 카메라 이미지 내 정규화 좌표(0~1)를 계산합니다.
     * 반환 순서: 좌상, 우상, 우하, 좌하 (Matrix.setPolyToPoly 호출 순서와 맞춤)
     */
    fun computeSourceQuadNormalized(eye: EyePosition): FloatArray {
        val hw = displayWidthM / 2f
        val hh = displayHeightM / 2f
        val corners = listOf(
            -hw to hh, hw to hh, hw to -hh, -hw to -hh
        )

        val ez = eye.z.coerceAtLeast(0.05f) // 0 나눗셈 방지
        val t = (ez + backgroundDistanceM) / ez

        // 센서 스트림의 반 FOV 탄젠트 (전체 센서가 스트림이라고 가정)
        var tanH = tan(Math.toRadians(cameraFov.horizontalDeg.toDouble()) / 2.0)
        var tanV = tan(Math.toRadians(cameraFov.verticalDeg.toDouble()) / 2.0)
        // PreviewView FILL_CENTER 크롭 보정: 뷰 비율이 스트림 비율과 다르면 한 축이 잘려 보임
        if (previewAspect > 0f) {
            val streamAspect = tanH / tanV
            if (previewAspect > streamAspect) tanV = tanH / previewAspect   // 세로가 잘림
            else tanH = tanV * previewAspect                                 // 가로가 잘림
        }
        val visibleWidthAtD = (2.0 * backgroundDistanceM * tanH).toFloat()
        val visibleHeightAtD = (2.0 * backgroundDistanceM * tanV).toFloat()

        val result = FloatArray(8)
        corners.forEachIndexed { i, (dx, dy) ->
            val worldX = eye.x + t * (dx - eye.x)
            val worldY = eye.y + t * (dy - eye.y)

            // 배경 평면 좌표(디스플레이 중심 기준)를 후면 카메라 광축 기준으로 변환
            val camX = worldX - backCamOffsetXM
            val camY = worldY - backCamOffsetYM

            val normX = 0.5f + camX / visibleWidthAtD
            val normY = 0.5f - camY / visibleHeightAtD // 이미지 좌표는 위가 0

            result[i * 2] = normX
            result[i * 2 + 1] = normY
        }
        return result
    }
}
