package app.tiinyremote

import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps model loading apart from models at work. A load copies gigabytes over the PCIe link between the CPU side and
 * the NPU; the NPU faults seen so far (pcie_hdma_timeout) are timeouts on that link. So a load waits until no model is
 * writing (chat answers, creations, transcriptions), at most 5 minutes. Only requests from this app are known here:
 * something started in Tiiny Studio isn't.
 */
object NpuGate {
    private val working = AtomicInteger(0)

    /** Is a model writing for this app right now? */
    val busy get() = working.get() > 0

    /** A speed test has the NPU: Always ready loads nothing meanwhile (it would skew the measurements). */
    @Volatile
    var testing = false

    /** How many requests are writing right now (2 or more: models working side by side). */
    val count get() = working.get()

    /** Runs a request that has a model at work (a chat answer, a creation…). */
    fun <T> work(block: () -> T): T {
        working.incrementAndGet()
        try {
            return block()
        } finally {
            working.decrementAndGet()
        }
    }

    /** Before a load (blocking, on an IO thread): waits until no model is writing, at most 5 minutes. */
    fun awaitQuiet(model: String) {
        if (!busy) return
        val name = model.substringAfterLast('/')
        ApiLog.add("Loading $name waits until the answer being written is finished (a load during writing may upset the NPU).", "Models")
        val until = System.currentTimeMillis() + 5 * 60_000
        while (busy && System.currentTimeMillis() < until) Thread.sleep(500)
        if (busy) ApiLog.add("Still writing after 5 minutes: loading $name anyway.", "Models")
    }
}
