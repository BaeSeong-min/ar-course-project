package com.example.dualcam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.atan

class MainActivity : AppCompatActivity() {

    // ---- 화면(뷰) ----
    private lateinit var setupContainer: LinearLayout
    private lateinit var runningContainer: LinearLayout
    private lateinit var endContainer: LinearLayout

    private lateinit var participantIdInput: EditText
    private lateinit var dominantEyeGroup: RadioGroup
    private lateinit var testModeGroup: RadioGroup

    private lateinit var backPreview: PreviewView
    private lateinit var frontPreview: PreviewView
    private lateinit var trialInfoText: TextView
    private lateinit var liveDistanceText: TextView
    private lateinit var nextButton: Button
    private lateinit var restOverlay: TextView
    private lateinit var endText: TextView

    // ---- 실험 상태 ----
    private var participantId: Int = 1
    private var dominantSide: DominantEyeSide = DominantEyeSide.RIGHT
    private var trials: List<Trial> = emptyList()
    private var currentTrialIdx = 0
    private lateinit var trialLogger: TrialLogger

    // ---- 카메라/추적 ----
    private var reprojection: ReprojectionMath? = null
    private var eyeTracker: EyeTracker? = null
    private var backTextureView: TextureView? = null
    private var currentEye: EyePosition = EyePosition(0f, 0f, DeviceConfig.backgroundDistanceM)

    // ---- 테스트 모드 (얼굴 추적 없이 수동으로 눈 위치 지정) ----
    private var testMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        showSetup()

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    // ======================================================================
    // UI 구성 (XML 없이 코드로 생성)
    // ======================================================================

    private fun buildUi() {
        // 바깥 틀을 FrameLayout으로 둬서, 설정/실험/종료 화면 각각이 전체 화면을 채우도록 합니다.
        // (LinearLayout을 쓰면 내용물 높이만큼만 차지하고 나머지가 빈 배경으로 남는 문제가 있었습니다.)
        val root = android.widget.FrameLayout(this)
        val fullScreenParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )

        // ---- 설정 화면 ----
        setupContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        setupContainer.addView(TextView(this).apply { text = "참가자 번호 (1~6)"; textSize = 16f })
        participantIdInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        setupContainer.addView(participantIdInput)

        setupContainer.addView(TextView(this).apply {
            text = "조준 우세안 (마일스 검사 결과)"; textSize = 16f; setPadding(0, 32, 0, 0)
        })
        dominantEyeGroup = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
        dominantEyeGroup.addView(RadioButton(this).apply { id = 1; text = "오른쪽"; isChecked = true })
        dominantEyeGroup.addView(RadioButton(this).apply { id = 2; text = "왼쪽" })
        setupContainer.addView(dominantEyeGroup)

        setupContainer.addView(TextView(this).apply {
            text = "모드 선택"; textSize = 16f; setPadding(0, 32, 0, 0)
        })
        testModeGroup = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
        testModeGroup.addView(RadioButton(this).apply { id = 3; text = "실제 얼굴 추적"; isChecked = true })
        testModeGroup.addView(RadioButton(this).apply { id = 4; text = "테스트 모드(수동 슬라이더)" })
        setupContainer.addView(testModeGroup)

        val startBtn = Button(this).apply {
            text = "실험 시작"
            setOnClickListener { onStartClicked() }
        }
        setupContainer.addView(startBtn).also {
            (startBtn.layoutParams as LinearLayout.LayoutParams).topMargin = 48
        }

        // ---- 실험 진행 화면 ----
        // 카메라 화면이 전체를 채우고, 실험자용 안내/버튼은 그 위에 떠 있는 형태(FrameLayout)
        val runningFrame = android.widget.FrameLayout(this)

        backPreview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        runningFrame.addView(backPreview)

        // 전면 프리뷰는 모니터링용으로 구석에 작게
        frontPreview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = android.widget.FrameLayout.LayoutParams(180, 240).apply {
                gravity = Gravity.END or Gravity.TOP
                topMargin = 16; rightMargin = 16
            }
        }
        runningFrame.addView(frontPreview)

        // 실험자용 안내 + 버튼을 반투명 패널로 하단에 띄움 (카메라 화면 위에 겹침)
        val controlPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xAA000000.toInt())
            setPadding(24, 16, 24, 16)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
        }
        trialInfoText = TextView(this).apply { textSize = 13f; setTextColor(0xFFFFFFFF.toInt()) }
        liveDistanceText = TextView(this).apply { textSize = 13f; setTextColor(0xFFFFFFFF.toInt()) }
        nextButton = Button(this).apply {
            text = "다음 시행"
            setOnClickListener { onNextClicked() }
        }
        restOverlay = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
            visibility = View.GONE
        }
        controlPanel.addView(trialInfoText)
        controlPanel.addView(liveDistanceText)
        controlPanel.addView(nextButton)
        controlPanel.addView(restOverlay)
        runningFrame.addView(controlPanel)

        runningContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(
                runningFrame,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        // ---- 종료 화면 ----
        endContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(32, 64, 32, 32)
        }
        endText = TextView(this).apply { textSize = 16f }
        endContainer.addView(endText)

        root.addView(setupContainer, fullScreenParams)
        root.addView(runningContainer, android.widget.FrameLayout.LayoutParams(fullScreenParams))
        root.addView(endContainer, android.widget.FrameLayout.LayoutParams(fullScreenParams))
        setContentView(root)
    }

    private fun showSetup() {
        setupContainer.visibility = View.VISIBLE
        runningContainer.visibility = View.GONE
        endContainer.visibility = View.GONE
    }

    private fun showRunning() {
        setupContainer.visibility = View.GONE
        runningContainer.visibility = View.VISIBLE
        endContainer.visibility = View.GONE
    }

    private fun showEnd(message: String) {
        setupContainer.visibility = View.GONE
        runningContainer.visibility = View.GONE
        endContainer.visibility = View.VISIBLE
        endText.text = message
    }

    // ======================================================================
    // 실험 시작
    // ======================================================================

    private fun onStartClicked() {
        participantId = participantIdInput.text.toString().toIntOrNull() ?: 1
        dominantSide = if (dominantEyeGroup.checkedRadioButtonId == 1) DominantEyeSide.RIGHT else DominantEyeSide.LEFT
        testMode = testModeGroup.checkedRadioButtonId == 4

        trials = TrialSequencer.generate(participantId)
        currentTrialIdx = 0
        trialLogger = TrialLogger(this, participantId)

        reprojection = ReprojectionMath(
            displayWidthM = DeviceConfig.displayWidthM,
            displayHeightM = DeviceConfig.displayHeightM,
            backgroundDistanceM = DeviceConfig.backgroundDistanceM,
            backCamOffsetXM = DeviceConfig.backCamOffsetXM,
            backCamOffsetYM = DeviceConfig.backCamOffsetYM
        )

        showRunning()
        if (!hasCameraPermission()) {
            Toast.makeText(this, "카메라 권한을 허용해주세요", Toast.LENGTH_LONG).show()
            return
        }
        startDualCamera()
        showCurrentTrial(logShown = true)
    }

    // ======================================================================
    // 카메라 설정 (전/후면 동시 바인딩 + 눈 추적 + 워프)
    // ======================================================================

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startDualCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()

            val concurrentInfos = provider.availableConcurrentCameraInfos
            val supported = concurrentInfos.any { combo ->
                combo.any { lensFacing(it) == CameraCharacteristics.LENS_FACING_FRONT } &&
                        combo.any { lensFacing(it) == CameraCharacteristics.LENS_FACING_BACK }
            }
            if (!supported) {
                Toast.makeText(this, "이 기기는 전/후면 동시 카메라를 지원하지 않습니다", Toast.LENGTH_LONG).show()
                return@addListener
            }

            // PreviewView는 화면 회전을 스스로 처리하므로 Preview에는 setTargetRotation을
            // 따로 주지 않습니다(중복 적용되면 오히려 90도 더 돌아가 보입니다).
            // ImageAnalysis는 자동으로 돌아가지 않는 원본 버퍼라서 여기에만 지정합니다.
            val rotation = backPreview.display?.rotation ?: android.view.Surface.ROTATION_0

            // 후면: 프리뷰만
            val backPreviewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(backPreview.surfaceProvider)
            }
            val backGroup = UseCaseGroup.Builder().addUseCase(backPreviewUseCase).build()

            // 전면: 프리뷰(모니터링용) + ImageAnalysis(얼굴 추적용)
            val frontPreviewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(frontPreview.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .build()

            if (!testMode) {
                setupEyeTracker()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                    val bmp = imageProxy.toBitmap()
                    if (bmp != null) {
                        eyeTracker?.analyze(bmp, System.currentTimeMillis())
                    }
                    imageProxy.close()
                }
            } else {
                setupTestModeSliders()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { it.close() }
            }

            val frontGroup = UseCaseGroup.Builder()
                .addUseCase(frontPreviewUseCase)
                .addUseCase(analysis)
                .build()

            val backConfig = SingleCameraConfig(CameraSelector.DEFAULT_BACK_CAMERA, backGroup, this)
            val frontConfig = SingleCameraConfig(CameraSelector.DEFAULT_FRONT_CAMERA, frontGroup, this)

            try {
                provider.unbindAll()
                provider.bindToLifecycle(listOf(backConfig, frontConfig))

                // 카메라 특성으로 FOV / 초점거리(px) 추정 [확인 필요: 근사값]
                val backChar = Camera2CameraInfo.extractCameraCharacteristics(
                    provider.availableCameraInfos.first { lensFacing(it) == CameraCharacteristics.LENS_FACING_BACK }
                )
                val (hFov, vFov) = getCameraFovDeg(backChar)
                reprojection = ReprojectionMath(
                    DeviceConfig.displayWidthM, DeviceConfig.displayHeightM,
                    DeviceConfig.backgroundDistanceM, CameraFov(hFov, vFov),
                    DeviceConfig.backCamOffsetXM, DeviceConfig.backCamOffsetYM,
                    previewAspect = if (backPreview.height > 0) backPreview.width.toFloat() / backPreview.height else 0f
                )

                if (!testMode) {
                    val frontChar = Camera2CameraInfo.extractCameraCharacteristics(
                        provider.availableCameraInfos.first { lensFacing(it) == CameraCharacteristics.LENS_FACING_FRONT }
                    )
                    // 전면 이미지 가로 해상도를 모를 때는 대략 640으로 가정 [확인 필요: 실제 analysis 해상도로 교체]
                    val focalPx = getFocalLengthPx(frontChar, assumedImageWidthPx = 640)
                    eyeTracker?.setFrontIntrinsics(focalPx)
                }

                // 후면 PreviewView(COMPATIBLE) 내부의 TextureView를 찾아 워프 적용에 사용
                backPreview.post {
                    backTextureView = findTextureView(backPreview)
                    startWarpLoop()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "카메라 바인딩 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun lensFacing(info: CameraInfo): Int? =
        Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.LENS_FACING)

    private fun findTextureView(view: View): TextureView? {
        if (view is TextureView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextureView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun getCameraFovDeg(characteristics: android.hardware.camera2.CameraCharacteristics): Pair<Float, Float> {
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        if (sensorSize == null || focalLengths == null || focalLengths.isEmpty()) return 65f to 50f
        val f = focalLengths[0]
        val hFov = Math.toDegrees(2 * atan((sensorSize.width / 2f) / f).toDouble()).toFloat()
        val vFov = Math.toDegrees(2 * atan((sensorSize.height / 2f) / f).toDouble()).toFloat()
        return hFov to vFov
    }

    private fun getFocalLengthPx(
        characteristics: android.hardware.camera2.CameraCharacteristics,
        assumedImageWidthPx: Int
    ): Float {
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 0f
        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        if (focalLengths == null || focalLengths.isEmpty()) return 0f
        val f = focalLengths[0]
        return f * assumedImageWidthPx / sensorSize.width
    }

    private fun setupEyeTracker() {
        eyeTracker = EyeTracker(
            context = this,
            frontCamOffsetXM = DeviceConfig.frontCamOffsetXM,
            frontCamOffsetYM = DeviceConfig.frontCamOffsetYM,
            realIpdM = DeviceConfig.ipdM
        ) { eye ->
            currentEye = eye
            runOnUiThread {
                liveDistanceText.text = "현재 추정 거리: %.1f cm (목표: %.1f cm)".format(
                    eye.z * 100f, currentTrial()?.distanceCm ?: 0f
                )
            }
        }
        eyeTracker?.start()
    }

    private fun setupTestModeSliders() {
        // 테스트 모드: 얼굴 추적 없이 대화상자 느낌의 간단한 슬라이더로 ez만 조절
        // (ex, ey는 0으로 고정, 필요하면 여기에 SeekBar를 추가해 확장하세요)
        val target = currentTrial()?.distanceCm ?: 37.5f
        currentEye = EyePosition(0f, 0f, target / 100f)
        liveDistanceText.text = "[테스트 모드] 고정 거리: ${target} cm"
    }

    // 워프를 매 프레임 다시 그릴 필요는 없고, 추적값이 바뀔 때마다 갱신합니다.
    private fun startWarpLoop() {
        window.decorView.postDelayed(object : Runnable {
            override fun run() {
                applyWarp()
                window.decorView.postDelayed(this, 100) // 10Hz로 갱신
            }
        }, 100)
    }

    private fun applyWarp() {
        val tv = backTextureView ?: return
        val repro = reprojection ?: return
        val trial = currentTrial() ?: return

        val refPoint = repro.resolveReferencePoint(
            rawMidpoint = currentEye,
            mode = if (trial.mode == EyeRefMode.DOMINANT) ReferenceMode.DOMINANT_EYE else ReferenceMode.BINOCULAR_MIDPOINT,
            dominantSide = dominantSide,
            ipdM = DeviceConfig.ipdM
        )

        val quadNorm = repro.computeSourceQuadNormalized(refPoint)
        val w = tv.width.toFloat()
        val h = tv.height.toFloat()
        if (w <= 0f || h <= 0f) return

        val src = FloatArray(8)
        for (i in 0 until 4) {
            src[i * 2] = quadNorm[i * 2] * w
            src[i * 2 + 1] = quadNorm[i * 2 + 1] * h
        }
        // dst: TextureView 전체 사각형 (좌상, 우상, 우하, 좌하)
        val dst = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        tv.setTransform(matrix)
    }

    // ======================================================================
    // 시행 진행
    // ======================================================================

    private fun currentTrial(): Trial? = trials.getOrNull(currentTrialIdx)

    /**
     * 현재 시행 정보를 화면에 띄웁니다. logShown=true면 이 시행이 "표시되기 시작했다"는
     * 기록을 CSV에 남깁니다(평가 점수는 외부 평가지에 기록하므로, 여기서는 어떤 조건이
     * 언제 보였는지만 남겨 나중에 평가지의 순번과 맞춰볼 수 있게 합니다).
     */
    private fun showCurrentTrial(logShown: Boolean = false) {
        val trial = currentTrial() ?: run { finishExperiment(); return }
        val modeKor = if (trial.mode == EyeRefMode.DOMINANT) "우세안 기준" else "양안 중간점 기준"
        trialInfoText.text = "반복 ${trial.repetition} / 거리 ${trial.distanceCm}cm / 방식: $modeKor " +
                "(순번 ${trial.orderIndex}/12)\n참가자를 화면에서 ${trial.distanceCm}cm 거리로 위치시켜주세요."

        if (logShown) trialLogger.logTrialShown(participantId, trial)
        if (testMode) setupTestModeSliders()
    }

    private fun onNextClicked() {
        val trial = currentTrial() ?: return

        // 반복1의 6번째 시행 뒤 30초 휴식
        if (trial.orderIndex == TrialSequencer.REST_AFTER_ORDER_INDEX) {
            currentTrialIdx++
            startRest()
        } else {
            currentTrialIdx++
            showCurrentTrial(logShown = true)
        }
    }

    private fun startRest() {
        nextButton.visibility = View.GONE
        restOverlay.visibility = View.VISIBLE
        object : CountDownTimer(30_000, 1000) {
            override fun onTick(msUntilFinished: Long) {
                restOverlay.text = "휴식 중... ${msUntilFinished / 1000}초 남음"
            }
            override fun onFinish() {
                restOverlay.visibility = View.GONE
                nextButton.visibility = View.VISIBLE
                showCurrentTrial(logShown = true)
            }
        }.start()
    }

    private fun finishExperiment() {
        eyeTracker?.close()
        showEnd("실험이 종료되었습니다.\n결과 파일: ${trialLogger.filePath()}")
    }
}