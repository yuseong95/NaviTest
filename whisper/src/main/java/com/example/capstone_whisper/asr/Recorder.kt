package com.example.capstone_whisper.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.*

class Recorder(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var isRecording = false
    private val pcmStream = ByteArrayOutputStream()

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate,
        channelConfig,
        audioFormat
    )

    private var readCallback: ((ShortArray, Int) -> Unit)? = null

    /**
     * 녹음 시작 (권한 확인 포함)
     */
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            ).apply { startRecording() }
        } catch (e: SecurityException) {
            recorder = null
            throw e
        }

        isRecording = true
        pcmStream.reset()

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {
                    for (i in 0 until read) {
                        val sample = buffer[i]
                        pcmStream.write(sample.toInt() and 0xFF)
                        pcmStream.write(sample.toInt().shr(8) and 0xFF)
                    }
                    readCallback?.invoke(buffer, read)
                }

                delay(20)
            }
        }

    }

    /**
     * 녹음 중지
     */
    fun stopRecording() {
        isRecording = false
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        readCallback = null
    }

    /**
     * 녹음된 데이터 반환 (ByteArrayOutputStream 형태)
     */
    fun getRawData(): ByteArrayOutputStream {
        return pcmStream
    }

    /**
     * 실시간 오디오 버퍼 처리 콜백 설정
     * @param callback (ShortArray, readSize) -> Unit
     */
    fun read(callback: (ShortArray, Int) -> Unit) {
        this.readCallback = callback
    }
/*
    fun savePcmAsWav(pcmData: ByteArray, wavFile: File) {
        val sampleRate = 16000
        val channels = 1
        val byteRate = sampleRate * 2 * channels
        val totalDataLen = pcmData.size + 36
        val totalAudioLen = pcmData.size

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * channels).toByte(); header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        wavFile.outputStream().use {
            it.write(header)
            it.write(pcmData)
        }
    }

    fun DataOutputStream.writeIntLE(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value shr 8) and 0xFF)
        writeByte((value shr 16) and 0xFF)
        writeByte((value shr 24) and 0xFF)
    }

    fun DataOutputStream.writeShortLE(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value shr 8) and 0xFF)
    }*/
}
