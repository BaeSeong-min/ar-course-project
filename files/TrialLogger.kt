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
            file.writeText(
                "participant,repetition,order_index,distance_cm,mode,likert_connectedness,preferred_mode,timestamp\n"
            )
        }
    }

    fun logResponse(
        participantId: Int,
        trial: Trial,
        likertConnectedness: Int,
        preferredMode: String? = null
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$participantId,${trial.repetition},${trial.orderIndex},${trial.distanceCm}," +
            "${trial.mode},$likertConnectedness,${preferredMode ?: ""},$timestamp\n"
        FileWriter(file, true).use { it.append(line) }
    }

    fun filePath(): String = file.absolutePath
}
