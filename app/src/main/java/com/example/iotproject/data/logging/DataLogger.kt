package com.example.iotproject.data.logging

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.iotproject.data.model.AssessmentResult
import com.example.iotproject.data.model.GnssConstellationSummary
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.SensorSnapshot
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataLogger(private val context: Context) {

    private var currentFile: File? = null
    private var writer: BufferedWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    var isRecording: Boolean = false
        private set

    var recordedSamplesCount: Long = 0L
        private set

    var currentRecordingFile: File? = null
        private set

    fun startNewSession(): File {
        stopSession()

        val logsDir = File(context.getExternalFilesDir(null), "gnss_sensor_logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }

        val filename = "gnss_log_${fileDateFormat.format(Date())}.csv"
        val file = File(logsDir, filename)
        currentFile = file
        currentRecordingFile = file
        recordedSamplesCount = 0L

        writer = BufferedWriter(FileWriter(file, true))
        val header = "timestamp_ms,iso_time,latitude,longitude,altitude_m,accuracy_m,speed_mps,bearing_deg," +
                "gnss_state,accel_x,accel_y,accel_z,linear_accel_x,linear_accel_y,linear_accel_z," +
                "gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,heading_deg,step_count,satellites_total," +
                "satellites_used,avg_cn0,is_stationary,assessment_reasons\n"
        writer?.write(header)
        writer?.flush()

        isRecording = true
        return file
    }

    @Synchronized
    fun logSample(
        location: LocationData?,
        sensors: SensorSnapshot,
        gnssSummary: GnssConstellationSummary,
        assessment: AssessmentResult
    ) {
        if (!isRecording || writer == null) return

        try {
            val now = System.currentTimeMillis()
            val isoTime = dateFormat.format(Date(now))
            val reasonsStr = assessment.reasons.joinToString(" | ").replace(",", ";")

            val lat = location?.latitude ?: Double.NaN
            val lon = location?.longitude ?: Double.NaN
            val alt = location?.altitude ?: Double.NaN
            val acc = location?.accuracy ?: Float.NaN
            val speed = location?.speed ?: Float.NaN
            val bearing = location?.bearing ?: Float.NaN

            val row = buildString {
                append("$now,$isoTime,$lat,$lon,$alt,$acc,$speed,$bearing,")
                append("${assessment.state.name},")
                append("${sensors.accelerometer.x},${sensors.accelerometer.y},${sensors.accelerometer.z},")
                append("${sensors.linearAcceleration.x},${sensors.linearAcceleration.y},${sensors.linearAcceleration.z},")
                append("${sensors.gyroscope.x},${sensors.gyroscope.y},${sensors.gyroscope.z},")
                append("${sensors.magnetometer.x},${sensors.magnetometer.y},${sensors.magnetometer.z},")
                append("${sensors.rotation.azimuthDegrees},")
                append("${sensors.steps.totalSteps},")
                append("${gnssSummary.totalSatellites},${gnssSummary.usedInFixCount},")
                append("${gnssSummary.avgCn0},")
                append("${sensors.isDeviceStationary},")
                append("\"$reasonsStr\"\n")
            }

            writer?.write(row)
            recordedSamplesCount++

            if (recordedSamplesCount % 10 == 0L) {
                writer?.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSession(): File? {
        if (!isRecording) return currentFile
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            writer = null
            isRecording = false
        }
        return currentFile
    }

    fun getLogFiles(): List<File> {
        val logsDir = File(context.getExternalFilesDir(null), "gnss_sensor_logs")
        if (!logsDir.exists()) return emptyList()
        return logsDir.listFiles { file -> file.extension == "csv" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun shareLogFile(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GNSS & Sensor Log: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
