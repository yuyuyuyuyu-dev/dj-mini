package dev.yuyuyuyuyu.djmini.similar

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.LiteRtException
import com.google.ai.edge.litert.TensorBuffer

internal class SoundScorer(
    context: Context,
    environment: Environment,
) : AutoCloseable {
    private val model = CompiledModel.create(context.assets, MODEL, CompiledModel.Options(Accelerator.CPU), environment)
    private val inputs = mutableListOf<TensorBuffer>()
    private val outputs = mutableListOf<TensorBuffer>()

    init {
        try {
            inputs += model.createInputBuffers()
            outputs += model.createOutputBuffers()
        } catch (e: LiteRtException) {
            close()
            throw e
        }
    }

    fun scores(window: FloatArray): FloatArray {
        inputs[0].writeFloat(window)
        model.run(inputs, outputs)
        return outputs[0].readFloat()
    }

    override fun close() {
        (inputs + outputs).forEach { it.close() }
        model.close()
    }

    private companion object {
        const val MODEL = "yamnet.tflite"
    }
}
