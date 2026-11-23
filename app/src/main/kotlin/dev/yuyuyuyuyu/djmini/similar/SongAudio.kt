package dev.yuyuyuyuyu.djmini.similar

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.min

internal class SongAudio(
    private val context: Context,
) {
    fun windows(uri: Uri): List<FloatArray> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: return emptyList()
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val decoder = ClipDecoder(codec, extractor)
                return clipStarts(format).flatMap { decoder.decode(it).toWindows() }
            } finally {
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun clipStarts(format: MediaFormat): List<Long> {
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val clipUs = CLIP_SAMPLES * MICROSECONDS_PER_SECOND / SAMPLE_RATE
        return (0 until CLIPS)
            .map { (durationUs * (2 * it + 1) / (2 * CLIPS) - clipUs / 2).coerceAtLeast(0) }
            .distinct()
    }

    private fun FloatArray.toWindows(): List<FloatArray> =
        (0 until size / WINDOW_SAMPLES).map { copyOfRange(it * WINDOW_SAMPLES, (it + 1) * WINDOW_SAMPLES) }

    private class ClipDecoder(
        private val codec: MediaCodec,
        private val extractor: MediaExtractor,
    ) {
        private var pcm = Pcm(codec.outputFormat)

        fun decode(startUs: Long): FloatArray {
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec.flush()
            val samples = FloatArray(sourceSamples(pcm.sampleRate))
            var count = 0
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()
            while (!outputEnded && count < samples.size) {
                if (!inputEnded) inputEnded = queueInput()
                val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index >= 0) {
                    if (info.presentationTimeUs >= startUs) {
                        count += pcm.readMono(checkNotNull(codec.getOutputBuffer(index)), info, samples, count)
                    }
                    codec.releaseOutputBuffer(index, false)
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    pcm = Pcm(codec.outputFormat)
                }
            }
            return resample(samples.copyOf(count), pcm.sampleRate)
        }

        private fun queueInput(): Boolean {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) return false
            val size = extractor.readSampleData(checkNotNull(codec.getInputBuffer(index)), 0)
            val ended = size < 0
            if (ended) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                extractor.advance()
            }
            return ended
        }

        private fun sourceSamples(sampleRate: Int): Int = ceil(CLIP_SAMPLES.toDouble() * sampleRate / SAMPLE_RATE).toInt()

        private fun resample(
            samples: FloatArray,
            sampleRate: Int,
        ): FloatArray {
            if (samples.isEmpty()) return samples
            val step = sampleRate.toDouble() / SAMPLE_RATE
            return FloatArray((samples.size / step).toInt()) {
                val position = it * step
                val before = position.toInt()
                val after = min(before + 1, samples.lastIndex)
                val fraction = (position - before).toFloat()
                samples[before] * (1 - fraction) + samples[after] * fraction
            }
        }
    }

    private class Pcm(
        format: MediaFormat,
    ) {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        private val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        private val encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

        fun readMono(
            buffer: ByteBuffer,
            info: MediaCodec.BufferInfo,
            into: FloatArray,
            offset: Int,
        ): Int {
            buffer.position(info.offset).limit(info.offset + info.size)
            val ordered = buffer.slice().order(ByteOrder.nativeOrder())
            val values =
                when (encoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> {
                        val shorts = ordered.asShortBuffer()
                        FloatArray(shorts.remaining()) { shorts.get(it) / SHORT_SCALE }
                    }

                    AudioFormat.ENCODING_PCM_FLOAT -> {
                        val floats = ordered.asFloatBuffer()
                        FloatArray(floats.remaining()) { floats.get(it) }
                    }

                    else -> {
                        throw IllegalStateException("Unsupported PCM encoding: $encoding")
                    }
                }
            val frames = min(values.size / channels, into.size - offset)
            for (frame in 0 until frames) {
                var sum = 0f
                for (channel in 0 until channels) sum += values[frame * channels + channel]
                into[offset + frame] = sum / channels
            }
            return frames
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val WINDOW_SAMPLES = 15_600
        private const val CLIPS = 8
        private const val CLIP_SAMPLES = WINDOW_SAMPLES * 2
        private const val MICROSECONDS_PER_SECOND = 1_000_000L
        private const val TIMEOUT_US = 10_000L
        private const val SHORT_SCALE = 32_768f
    }
}
