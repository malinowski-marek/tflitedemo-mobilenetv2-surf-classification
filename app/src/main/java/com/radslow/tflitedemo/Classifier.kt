package com.radslow.tflitedemo

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*

class Classifier(assetManager: AssetManager) {

    private val labels: List<String>
    private val model: Interpreter

    init {
        model = Interpreter(getModelByteBuffer(assetManager, MODEL_PATH))
        labels = getLabels(assetManager, LABELS_PATH)
    }

    fun recognize(data: ByteArray): List<Recognition> {
        val result = Array(1) { FloatArray(labels.size) }

        val unscaledBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        val bitmap =
            Bitmap.createScaledBitmap(unscaledBitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, false)

        val byteBuffer = ByteBuffer
            .allocateDirect(BATCH_SIZE * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * BYTES_PER_CHANNEL * PIXEL_SIZE)
            .apply { order(ByteOrder.nativeOrder()) }

        val intValues = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until MODEL_INPUT_SIZE) {
            for (j in 0 until MODEL_INPUT_SIZE) {
                val pixelValue = intValues[pixel++]
                byteBuffer.putFloat((pixelValue shr 16 and 0xFF) / 255f)
                byteBuffer.putFloat((pixelValue shr 8 and 0xFF) / 255f)
                byteBuffer.putFloat((pixelValue and 0xFF) / 255f)
            }
        }

        model.run(byteBuffer, result)
        return parseResults(result)
    }

    private fun parseResults(result: Array<FloatArray>): List<Recognition> {

        val recognitions = mutableListOf<Recognition>()

        labels.forEachIndexed { index, label ->
            val probability = result[0][index]
            recognitions.add(Recognition(label, probability))
        }

        return recognitions.sortedByDescending { it.probability }
    }


    @Throws(IOException::class)
    private fun getModelByteBuffer(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            .asReadOnlyBuffer()
    }

    @Throws(IOException::class)
    private fun getLabels(assetManager: AssetManager, labelPath: String): List<String> {
        val labelList = ArrayList<String>()
        val reader = BufferedReader(InputStreamReader(assetManager.open(labelPath)))
        while (true) {
            val line = reader.readLine() ?: break
            labelList.add(line)
        }
        reader.close()
        return labelList
    }

    companion object {
        private const val BATCH_SIZE = 1 // process only 1 image at a time
        private const val MODEL_INPUT_SIZE = 224 // 224x224
        private const val BYTES_PER_CHANNEL = 4 // float size
        private const val PIXEL_SIZE = 3 // rgb

        private const val LABELS_PATH = "labels.txt"
        private const val MODEL_PATH = "surf_model.tflite"
    }

}