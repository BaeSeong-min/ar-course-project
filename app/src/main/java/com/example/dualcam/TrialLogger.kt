package com.example.dualcam

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 시행 결과를 CSV로 저장합니다.
 * 저장 위치: Android/data/<package>/files/trial_results_P<id>.csv
 * (앱 전용 외부 저장소라 별도 저장소 권한이 필요 없습니다.)
 * PC로 꺼내려면: adb pull 또는 Android Studio의 Device Explorer를 쓰세요.
 */
class TrialLogger(context: Context, participantId: Int) {

    private val file: File = File(
        context.getExternalFilesDir(null),
        "trial_results_P${participantId}.csv"
    )

    init {
        if (!file.exists()) {
            // 평가 점수는 외부 평가지에 기록하므로, 여기서는 어떤 조건이 언제 보였는지만 남깁니다.
            // 평가지의 순번(order_index)과 이 CSV의 order_index를 맞춰서 나중에 합치면 됩니다.
            file.writeText(
                "participant,repetition,order_index,distance_cm,mode,shown_at\n"
            )
        }
    }

    /** 시행이 화면에 표시되기 시작한 시각을 기록합니다. */
    fun logTrialShown(participantId: Int, trial: Trial) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$participantId,${trial.repetition},${trial.orderIndex},${trial.distanceCm}," +
                "${trial.mode},$timestamp\n"
        FileWriter(file, true).use { it.append(line) }
    }

    fun filePath(): String = file.absolutePath
}