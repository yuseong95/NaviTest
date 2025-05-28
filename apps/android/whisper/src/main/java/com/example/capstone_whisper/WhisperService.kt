package com.example.capstone_whisper;

import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.media3.common.util.Log
import com.example.capstone_whisper.asr.PorcupineWakeWordDetector
import com.example.capstone_whisper.asr.Recorder
import com.example.capstone_whisper.asr.Whisper
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt
import java.io.File

class WhisperService(
    private val context: Context,
    private val onResult: (String) -> Unit
) {
    private val recorder = Recorder(context)
    private val whisper = Whisper(context)
    private val isRecording = AtomicBoolean(false)
    private var wakeWordDetector: PorcupineWakeWordDetector? = null

    //porcupine 사용 시 사용하는 함수
    fun start() {
        wakeWordDetector = PorcupineWakeWordDetector(
            context = context,
            onWakeWordDetected = {
                if (isRecording.compareAndSet(false, true)) {
                    Toast.makeText(context, "whisper detected!", Toast.LENGTH_SHORT).show()
                    stopWakeWordDetection()
                    playDetectVoiceAndStart()
                }
            }
        )
        wakeWordDetector?.start()
    }

    // 버튼 사용 시 사용하는 start()
    fun startWithoutWakeWord() {
        if (isRecording.compareAndSet(false, true)) {
            Toast.makeText(context, "🎤 테스트 인식 시작 (Porcupine 없이)", Toast.LENGTH_SHORT).show()
            startRecordingAndTranscribe()  // 🔄 바로 녹음 시작
        }
    }

    fun stop() {
        wakeWordDetector?.stop()
        isRecording.set(false)
    }

    private fun stopWakeWordDetection() {
        wakeWordDetector?.stop()
    }

    private fun restartWakeWordDetection() {
        wakeWordDetector?.start()
    }

    private fun playDetectVoiceAndStart() {
        try {
            val afd = context.assets.openFd("dingdong.wav")
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                start()
            }

            mediaPlayer.setOnCompletionListener {
                mediaPlayer.release()
                startRecordingAndTranscribe()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            startRecordingAndTranscribe()
        }
    }

    private fun startRecordingAndTranscribe() {
        recorder.startRecording()

        val silenceThreshold = 0.05f    // 입력 감도 설정 0.05f - 0.005f 사이에서 조절
        val silenceLimit = 1500L        // 무음 감지 시간
        var lastSoundTime = System.currentTimeMillis()
        var alreadyStopped = false

        recorder.read { buffer, readSize ->
            if (alreadyStopped) return@read

            val hasSound = !isSilent(buffer, readSize, silenceThreshold)
            if (hasSound) lastSoundTime = System.currentTimeMillis()

            if (System.currentTimeMillis() - lastSoundTime >= silenceLimit) {
                alreadyStopped = true
                recorder.stopRecording()
                isRecording.set(false)
                restartWakeWordDetection()

                CoroutineScope(Dispatchers.IO).launch {
                    val raw = recorder.getRawData()
                    val result = if (raw.size() == 0) {
                        "⚠️ 음성이 감지되지 않았습니다."
                    } else {
                        whisper.transcribe(raw)
                    }
                    withContext(Dispatchers.Main) {
                        onResult(result)
                        Log.d("WhisperDebug", "📝 Transcription Result: $result")
                    }
                }
            }
        }
    }

    private fun isSilent(buffer: ShortArray, readSize: Int, threshold: Float): Boolean {
        var sum = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i] / 32768.0f
            sum += sample * sample
        }
        val rms = sqrt(sum / readSize)
        return rms < threshold
    }
}
