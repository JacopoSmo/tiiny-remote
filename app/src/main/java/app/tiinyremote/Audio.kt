package app.tiinyremote

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records from the microphone straight into a 16 kHz mono 16-bit WAV, the format the Tiiny's speech recognition
 * wants (Tiiny Studio converts to exactly this before transcribing). Also fine as a voice-cloning clip.
 */
class WavRecorder {
    private val rate = 16_000
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile
    var recording = false
        private set

    /** 20 minutes at most (about 38 MB): a forgotten recording can't fill the phone's memory. */
    private val maxBytes = rate * 2 * 60 * 20

    val seconds get() = pcm.size() / (rate * 2.0)

    @SuppressLint("MissingPermission") // the caller asks for RECORD_AUDIO first
    fun start() {
        if (recording) return
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate),
        )
        pcm.reset()
        r.startRecording()
        record = r
        recording = true
        thread = Thread {
            val buf = ByteArray(4096)
            while (recording) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0 && pcm.size() < maxBytes) synchronized(pcm) { pcm.write(buf, 0, n) }
            }
        }.also { it.start() }
    }

    /** Stops and returns the recording as WAV bytes. */
    fun stop(): ByteArray {
        recording = false
        thread?.join(1000)
        record?.let {
            try {
                it.stop()
            } catch (e: Exception) {
            }
            it.release()
        }
        record = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        return Wav.build(data, rate, 1)
    }
}

object Wav {
    fun build(pcm: ByteArray, rate: Int, channels: Int): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
            .putInt(rate).putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        b.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return b.array()
    }

    /** (fmt chunk bytes, sample data) of a WAV file, or null if it isn't one. */
    private fun split(wav: ByteArray): Pair<ByteArray, ByteArray>? {
        if (wav.size < 12 || String(wav, 0, 4) != "RIFF" || String(wav, 8, 4) != "WAVE") return null
        var i = 12
        var fmt: ByteArray? = null
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        while (i + 8 <= wav.size) {
            val id = String(wav, i, 4)
            val size = bb.getInt(i + 4)
            val start = i + 8
            val end = minOf(wav.size, start + maxOf(size, 0))
            if (id == "fmt ") fmt = wav.copyOfRange(start, end)
            if (id == "data") return (fmt ?: return null) to wav.copyOfRange(start, end)
            i = end + (size and 1)
        }
        return null
    }

    /**
     * The Tiiny's speech-to-text fails on long audio (90 s works, 120 s doesn't). A 16 kHz mono 16-bit WAV over 80 s
     * comes back in ~60 s parts, each cut at the quietest tenth of a second between its 50th and 60th second; anything
     * else comes back whole (Tiiny Studio's asr_parts, the same cuts).
     */
    fun asrParts(wav: ByteArray): List<ByteArray> {
        val (fmt, data) = split(wav) ?: return listOf(wav)
        if (fmt.size < 16) return listOf(wav)
        val f = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
        val rate = 16_000
        if (f.getShort(0).toInt() != 1 || f.getShort(2).toInt() != 1 || f.getInt(4) != rate || f.getShort(14).toInt() != 16) return listOf(wav)
        val s = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s)
        val one = 80 * rate
        val piece = 60 * rate
        val look = 10 * rate
        val win = rate / 10
        if (s.size <= one) return listOf(wav)
        val out = ArrayList<ByteArray>()
        var start = 0
        fun pcm(from: Int, to: Int): ByteArray {
            val b = ByteBuffer.allocate((to - from) * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in from until to) b.putShort(s[i])
            return b.array()
        }
        while (s.size - start > one) {
            var bestAt = start + piece - look
            var best = Long.MAX_VALUE
            var at = start + piece - look
            while (at <= start + piece - win) {
                var e = 0L
                for (i in at until at + win) e += kotlin.math.abs(s[i].toInt())
                if (e < best) {
                    best = e
                    bestAt = at
                }
                at += win
            }
            val cut = bestAt + win / 2
            out.add(build(pcm(start, cut), rate, 1))
            start = cut
        }
        out.add(build(pcm(start, s.size), rate, 1))
        return out
    }

    /**
     * A WAV as the Tiiny's speech-to-text wants it: 16-bit, 16 kHz, mono. Other rates, stereo, 8/24/32-bit and float
     * WAVs come back empty-handed from it (a 24 or 48 kHz voice clip gave an empty text), so they're converted
     * (Tiiny Studio's to_asr_wav). Anything that isn't a WAV it can read is returned as it is.
     */
    fun forAsr(wav: ByteArray): ByteArray {
        val (fmt, data) = split(wav) ?: return wav
        if (fmt.size < 16) return wav
        val f = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
        var tag = f.getShort(0).toInt() and 0xFFFF
        val ch = f.getShort(2).toInt().coerceAtLeast(1)
        val rate = f.getInt(4)
        val bits = f.getShort(14).toInt()
        if (tag == 0xFFFE && fmt.size >= 26) tag = f.getShort(24).toInt() and 0xFFFF // extensible: the real format
        if (tag == 1 && ch == 1 && rate == 16_000 && bits == 16) return wav
        val bytes = bits / 8
        if (rate <= 0 || bytes !in 1..4 || (tag != 1 && !(tag == 3 && bits == 32))) return wav
        val frames = data.size / (bytes * ch)
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        fun sample(i: Int): Int {
            val at = i * bytes
            return when {
                tag == 3 -> (b.getFloat(at).coerceIn(-1f, 1f) * 32767).toInt()
                bytes == 1 -> ((data[at].toInt() and 0xFF) - 128) shl 8
                bytes == 2 -> b.getShort(at).toInt()
                bytes == 3 -> (data[at + 1].toInt() and 0xFF) or (data[at + 2].toInt() shl 8)
                else -> b.getInt(at) shr 16
            }
        }
        val mono = IntArray(frames) { fr -> var sum = 0; for (c in 0 until ch) sum += sample(fr * ch + c); sum / ch }
        val step = rate / 16_000.0
        val n = (frames / step).toInt()
        val out = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        val k = step.toInt()
        for (i in 0 until n) {
            val v = if (step == k.toDouble() && k >= 1) {
                // 32/48 kHz: the average of each group (a simple low-pass)
                var sum = 0; for (j in 0 until k) sum += mono[(i * k + j).coerceAtMost(frames - 1)]; sum / k
            } else {
                val x = i * step
                val lo = x.toInt().coerceAtMost(frames - 1)
                val hi = (lo + 1).coerceAtMost(frames - 1)
                (mono[lo] + (mono[hi] - mono[lo]) * (x - lo)).toInt()
            }
            out.putShort(v.coerceIn(-32768, 32767).toShort())
        }
        return build(out.array(), 16_000, 1)
    }

    /** Joins WAV pieces of the same format into one (speech made in parts). */
    fun join(parts: List<ByteArray>): ByteArray {
        if (parts.size == 1) return parts[0]
        val first = split(parts[0]) ?: return parts[0]
        val fmt = first.first
        val data = ByteArrayOutputStream()
        for (p in parts) split(p)?.let { data.write(it.second) }
        val pcm = data.toByteArray()
        val b = ByteBuffer.allocate(20 + fmt.size + 8 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(4 + 8 + fmt.size + 8 + pcm.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(fmt.size).put(fmt)
        b.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return b.array()
    }
}

/** Plays one file at a time. `playing` is screen state, so every Play/Stop button shows the truth. */
object Player {
    private var mp: MediaPlayer? = null
    var playing: String? by mutableStateOf<String?>(null)
        private set

    fun toggle(file: File) {
        if (playing == file.name) {
            stop()
            return
        }
        stop()
        val p = MediaPlayer()
        try {
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            mp = p
            playing = file.name
        } catch (e: Exception) {
            p.release()
        }
    }

    fun stop() {
        mp?.let {
            try {
                it.stop()
            } catch (e: Exception) {
            }
            it.release()
        }
        mp = null
        playing = null
    }
}
