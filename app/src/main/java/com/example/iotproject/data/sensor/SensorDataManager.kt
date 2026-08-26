package com.example.iotproject.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.iotproject.data.model.RotationVectorData
import com.example.iotproject.data.model.SensorSnapshot
import com.example.iotproject.data.model.StepSensorData
import com.example.iotproject.data.model.Vector3D
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.sqrt

class SensorDataManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _sensorSnapshot = MutableStateFlow(SensorSnapshot())
    val sensorSnapshot: StateFlow<SensorSnapshot> = _sensorSnapshot.asStateFlow()

    @Volatile
    private var currentAccel = Vector3D()
    @Volatile
    private var currentLinearAccel = Vector3D()
    @Volatile
    private var currentGyro = Vector3D()
    @Volatile
    private var currentMag = Vector3D()
    @Volatile
    private var currentRotation = RotationVectorData()
    @Volatile
    private var totalSteps = 0L
    @Volatile
    private var initialStepCounterOffset = -1L
    @Volatile
    private var recentStepCount = 0

    // Low-pass filter for gravity separation when linear acceleration sensor isn't hardware present
    private val gravity = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Sliding window of recent acceleration magnitudes to compute standard deviation/motion energy
    private val recentAccelMagnitudes = ConcurrentLinkedQueue<Float>()
    private val maxWindowSize = 50

    var isListening: Boolean = false
        private set

    fun startListening(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME) {
        if (isListening) return
        isListening = true

        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, samplingPeriodUs)
        }
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, samplingPeriodUs)
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, samplingPeriodUs)
        }
        magnetometerSensor?.let {
            sensorManager.registerListener(this, it, samplingPeriodUs)
        }
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, samplingPeriodUs)
        }
        stepDetectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                currentAccel = Vector3D(x, y, z, event.timestamp)

                // If hardware linear acceleration is missing, isolate dynamic component using alpha filter
                val alpha = 0.8f
                gravity[0] = alpha * gravity[0] + (1 - alpha) * x
                gravity[1] = alpha * gravity[1] + (1 - alpha) * y
                gravity[2] = alpha * gravity[2] + (1 - alpha) * z

                if (linearAccelSensor == null) {
                    val lx = x - gravity[0]
                    val ly = y - gravity[1]
                    val lz = z - gravity[2]
                    currentLinearAccel = Vector3D(lx, ly, lz, event.timestamp)
                }

                val mag = currentLinearAccel.magnitude
                recentAccelMagnitudes.add(mag)
                while (recentAccelMagnitudes.size > maxWindowSize) {
                    recentAccelMagnitudes.poll()
                }

                updateSnapshot()
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                currentLinearAccel = Vector3D(
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    event.timestamp
                )
                updateSnapshot()
            }

            Sensor.TYPE_GYROSCOPE -> {
                currentGyro = Vector3D(
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    event.timestamp
                )
                updateSnapshot()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                currentMag = Vector3D(
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    event.timestamp
                )
                updateSnapshot()
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                val values = event.values
                val rotX = values[0]
                val rotY = values[1]
                val rotZ = values[2]
                val rotScalar = if (values.size >= 4) values[3] else 0f

                SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                val azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat().let {
                    if (it < 0) it + 360f else it
                }
                val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                currentRotation = RotationVectorData(
                    x = rotX,
                    y = rotY,
                    z = rotZ,
                    scalar = rotScalar,
                    azimuthDegrees = azimuthDeg,
                    pitchDegrees = pitchDeg,
                    rollDegrees = rollDeg,
                    timestampNanos = event.timestamp
                )
                updateSnapshot()
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                if (event.values[0] == 1.0f) {
                    recentStepCount++
                    updateSnapshot()
                }
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val count = event.values[0].toLong()
                if (initialStepCounterOffset < 0) {
                    initialStepCounterOffset = count
                }
                totalSteps = count - initialStepCounterOffset
                updateSnapshot()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateSnapshot() {
        val window = recentAccelMagnitudes.toList()
        val avg = if (window.isNotEmpty()) window.average().toFloat() else 0f
        val variance = if (window.isNotEmpty()) {
            window.map { (it - avg) * (it - avg) }.average().toFloat()
        } else 0f
        val stdDev = sqrt(variance)

        // Stationary when both linear dynamic acceleration standard deviation is very low (< 0.25 m/s²)
        // and gyroscope angular velocity is minimal (< 0.2 rad/s)
        val gyroMag = currentGyro.magnitude
        val isStationary = stdDev < 0.25f && currentLinearAccel.magnitude < 0.35f && gyroMag < 0.25f

        _sensorSnapshot.value = SensorSnapshot(
            timestampMs = System.currentTimeMillis(),
            accelerometer = currentAccel,
            linearAcceleration = currentLinearAccel,
            gyroscope = currentGyro,
            magnetometer = currentMag,
            rotation = currentRotation,
            steps = StepSensorData(
                totalSteps = if (totalSteps > 0) totalSteps else recentStepCount.toLong(),
                recentStepsDetected = recentStepCount,
                lastStepTimestampNanos = System.nanoTime()
            ),
            dynamicAccelMagnitude = currentLinearAccel.magnitude,
            isDeviceStationary = isStationary
        )
    }
}
