package com.example.dualcam

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.hypot

/**
 * 전면 카메라 프레임에서 MediaPipe Face Landmarker로 양안 중간점(눈) 위치를 추정합니다.
 *
 * ** 중요: 이 클래스는 실제 빌드·실행으로 검증하지 못했습니다. **
 * - MediaPipe Tasks Vision의 정확한 API(버전별 메서드 시그니처)는 공식 문서로 재확인하세요.
 * - 홍채 중심 랜드마크 인덱스(468, 473)는 "refine landmarks"(홍채 포함) 모델 기준입니다.
 *   사용하는 모델/버전에 따라 다를 수 있으니 반드시 확인하세요.
 * - face_landmarker.task 모델 파일은 app/src/main/assets/ 에 직접 받아 넣어야 합니다.
 */
class EyeTracker(
    context: Context,
    private val frontCamOffsetXM: Float,
    private val frontCamOffsetYM: Float,
    private val realIpdM: Float,
    private val mirrorX: Boolean = true,
    private val onEyeUpdate: (EyePosition) -> Unit
) {
    companion object {
        private const val TAG = "EyeTracker"
        private const val LEFT_IRIS_CENTER = 468   // [확인 필요]
        private const val RIGHT_IRIS_CENTER = 473  // [확인 필요]
    }

    private val appContext = context.applicationContext
    private var landmarker: FaceLandmarker? = null
    private var frontFocalPx: Float = 0f

    fun setFrontIntrinsics(focalLengthPx: Float) {
        frontFocalPx = focalLengthPx
    }

    fun start(modelAssetPath: String = "face_landmarker.task") {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAssetPath)
                .build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setResultListener(this::onResult)
                .setErrorListener { e -> Log.e(TAG, "FaceLandmarker 오류: ${e.message}") }
                .build()
            landmarker = FaceLandmarker.createFromOptions(appContext, options)
        } catch (e: Exception) {
            Log.e(TAG, "FaceLandmarker 초기화 실패", e)
        }
    }

    /** 전면 카메라 ImageAnalysis 프레임마다 호출하세요. */
    fun analyze(bitmap: Bitmap, timestampMs: Long) {
        val lm = landmarker ?: return
        val mpImage = BitmapImageBuilder(bitmap).build()
        lm.detectAsync(mpImage, timestampMs)
    }

    private fun onResult(result: FaceLandmarkerResult, input: MPImage) {
        val faces = result.faceLandmarks()
        if (faces.isEmpty()) return
        val landmarks = faces[0]
        if (landmarks.size <= RIGHT_IRIS_CENTER) {
            Log.w(TAG, "홍채 랜드마크 없음 - refine landmarks 옵션 확인 필요")
            return
        }

        val imgW = input.width.toFloat()
        val imgH = input.height.toFloat()

        val leftX = landmarks[LEFT_IRIS_CENTER].x() * imgW
        val leftY = landmarks[LEFT_IRIS_CENTER].y() * imgH
        val rightX = landmarks[RIGHT_IRIS_CENTER].x() * imgW
        val rightY = landmarks[RIGHT_IRIS_CENTER].y() * imgH

        val ipdPx = hypot((leftX - rightX).toDouble(), (leftY - rightY).toDouble()).toFloat()
        if (ipdPx <= 1f || frontFocalPx <= 0f) return

        // 거리 추정: Z = f * 실제IPD / 영상속IPD(픽셀)
        val z = frontFocalPx * realIpdM / ipdPx

        val midPxX = (leftX + rightX) / 2f
        val midPxY = (leftY + rightY) / 2f
        val cx = imgW / 2f
        val cy = imgH / 2f

        var camX = (midPxX - cx) * z / frontFocalPx
        val camY = -(midPxY - cy) * z / frontFocalPx // 이미지 y는 아래로 증가 -> 부호 반전

        if (mirrorX) camX = -camX // 전면 카메라 미러링 보정 [확인 필요]

        val ex = camX + frontCamOffsetXM
        val ey = camY + frontCamOffsetYM

        onEyeUpdate(EyePosition(ex, ey, z))
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }
}
