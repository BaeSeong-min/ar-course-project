# 실험 프로토타입 (우세안 vs 양안 중간점 기준 UPR)

## 중요 안내
이 코드는 **실제로 빌드·실행해서 검증하지 못했습니다.** 설계와 수식, Android API 사용법은
최대한 정확하게 작성했지만, 아래 항목들은 실기기에서 반드시 확인하고 필요하면 고쳐야 합니다.

- MediaPipe Tasks Vision의 정확한 API 시그니처 (버전에 따라 조금씩 다를 수 있음)
- 홍채 랜드마크 인덱스 (468, 473) — 모델/버전에 따라 다를 수 있음
- `PreviewView`(COMPATIBLE 모드) 내부의 `TextureView`에 접근하는 방식 — AndroidX 버전에 따라
  내부 뷰 구조가 바뀔 수 있는 비공식적인 방법입니다.
- 전면 카메라 좌우 반전(mirror) 여부와 좌표 부호 — `EyeTracker`의 `mirrorX`, `DeviceConfig`의
  오프셋 부호를 실기기에서 눈으로 보면서 맞춰야 합니다.
- 카메라 FOV/초점거리를 Camera2 특성값으로 근사하는 부분 — 정확도가 충분한지 확인 필요.

막히는 부분이 있으면 빌드 오류나 Logcat 내용을 알려주시면 같이 고치겠습니다.

## 파일 구성
```
app/src/main/java/com/example/dualcam/
├── MainActivity.kt         실험 진행 화면 전체 (카메라, UI, 시행 흐름)
├── ReprojectionMath.kt     기준점(우세안/중간점)에 따른 호모그래피 계산
├── EyeTracker.kt           MediaPipe Face Landmarker로 눈 위치 추정
├── ImageUtils.kt           ImageProxy -> Bitmap 변환
├── TrialSequencer.kt       참가자 번호 -> 12개 시행 순서 자동 생성
├── TrialLogger.kt          응답을 CSV로 저장
└── DeviceConfig.kt         기기별로 실측해서 채워야 하는 값
```

## 설정 순서

### 1. 프로젝트에 파일 복사
"Empty Views Activity"로 만든 프로젝트에서, 기존 `MainActivity.kt`를 지우고 위 6개 파일을
`app/src/main/java/com/example/dualcam/`(본인 패키지 경로)에 복사하세요. 패키지명이 다르면
각 파일 맨 위 `package com.example.dualcam` 줄을 프로젝트에 맞게 바꾸세요.

### 2. build.gradle.kts (Module: app)
```kotlin
android {
    defaultConfig {
        minSdk = 30
    }
}

dependencies {
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // MediaPipe Tasks Vision - 버전은 최신 안정 버전으로 확인 후 바꾸세요 [확인 필요]
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    implementation("androidx.appcompat:appcompat:1.7.0")
}
```

### 3. AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.any" />
```

### 4. MediaPipe 얼굴 랜드마크 모델 파일
`app/src/main/assets/face_landmarker.task` 파일이 필요합니다. 이 파일은 MediaPipe의 모델
다운로드 페이지(Google의 MediaPipe Solutions 문서)에서 "Face Landmarker" 모델을 받아
`assets` 폴더에 넣으세요. 정확한 다운로드 URL은 제가 확실히 기억하지 못하니, MediaPipe
공식 문서에서 "Face Landmarker" 모델 다운로드 링크를 직접 찾아 확인해 주세요. **홍채
랜드마크(468~477)가 포함된 버전("refine landmarks" 또는 "with blendshapes" 옵션이 있는
모델)을 받아야 이 코드가 의도대로 동작합니다.**

### 5. DeviceConfig.kt 값 채우기
사용하는 갤럭시 기기를 가로로 들고, 줄자로 다음을 재서 넣으세요.
- `displayWidthM`, `displayHeightM`: 화면의 실제 가로·세로 길이
- `frontCamOffsetXM`, `frontCamOffsetYM`: 화면 중심에서 전면 카메라 구멍까지의 거리
- `backgroundDistanceM`: 실험 환경에서 휴대폰부터 벽(배경)까지의 거리

### 6. 먼저 테스트 모드로 확인
얼굴 추적이 바로 안정적으로 될 가능성은 낮습니다. 설정 화면에서 **"테스트 모드"**를
선택하면 얼굴 추적 없이 시행 순서·UI·CSV 기록 흐름만 먼저 확인할 수 있습니다. 이 부분이
잘 동작하는 것을 먼저 확인한 뒤, 얼굴 추적을 붙이는 순서를 권합니다.

## 실행 후 확인할 것
1. 설정 화면에서 참가자 번호, 우세안을 입력하고 "실험 시작"
2. 전/후면 동시 카메라 프리뷰가 뜨는지 (안 뜨면 기기가 동시 카메라를 지원하지 않는 것)
3. (실제 추적 모드) 전면 미리보기에서 "현재 추정 거리"가 참가자가 가까이/멀리 움직일 때
   비슷한 방향으로 바뀌는지
4. 후면 화면을 좌우로 움직이거나 참가자가 좌우로 움직일 때, 배경 영상이 반대/같은 방향으로
   움직이는지 — 이 방향이 이상하면 `mirrorX`나 좌표 부호를 바꿔보세요.
5. 리커트 버튼을 누르면 다음 시행으로 넘어가고, `Android/data/<패키지명>/files/` 아래에
   CSV가 생기는지 (Android Studio의 Device Explorer로 확인 가능)

## 참고: 호모그래피 계산 방식
`ReprojectionMath`는 배경을 카메라로부터 `backgroundDistanceM` 거리의 평면으로 가정하고,
디스플레이 네 모서리를 지나는 시선이 그 평면과 만나는 점을 계산한 뒤, 카메라의 화각(FOV)을
이용해 그 점이 카메라 이미지의 어느 좌표에 해당하는지 역산합니다. 이렇게 구한 4개 점을
`Matrix.setPolyToPoly`로 화면 전체 사각형에 매핑하면, 결과적으로 원근 변환(호모그래피)이
적용된 영상이 `TextureView`에 그려집니다.
