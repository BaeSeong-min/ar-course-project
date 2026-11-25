package com.example.dualcam

/**
 * 사용하는 갤럭시 기기에 맞게 실측해서 수정하세요. 단위는 모두 미터(m)입니다.
 * (가로 모드 기준)
 */
object DeviceConfig {
    /** 화면 가로 길이 (실제 보이는 디스플레이 영역) */
    var displayWidthM = 0.158f
    /** 화면 세로 길이 */
    var displayHeightM = 0.076f
    /** 전면 카메라가 화면 중심에서 가로로 떨어진 거리 (가로 모드 기준, 보통 0에 가까움) */
    var frontCamOffsetXM = 0f
    /** 전면 카메라가 화면 중심에서 세로로 떨어진 거리. 보통 위쪽에 있으므로 부호 규칙을
     * 코드의 좌표계(위가 +)에 맞춰 직접 확인 후 설정하세요. [확인 필요] */
    var frontCamOffsetYM = 0.075f
    /** 후면 카메라가 화면 중심에서 떨어진 거리 (가로 모드, 오른쪽/위 +). 기기에 맞게 실측 필요 [확인 필요] */
    var backCamOffsetXM = 0f
    var backCamOffsetYM = 0f
    /** 평균 동공간 거리. 참가자별 실측값이 있다면 교체하세요. */
    var ipdM = 0.063f
    /** 실험 환경에서 배경(벽)까지의 거리 */
    var backgroundDistanceM = 2.0f
}
