package app.tiinyremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMathTest {
    private val eps = 1e-6

    @Test fun medianOddEvenEmpty() {
        assertEquals(2.0, SpeedMath.median(listOf(3.0, 1.0, 2.0)), eps)
        assertEquals(2.5, SpeedMath.median(listOf(4.0, 1.0, 2.0, 3.0)), eps)
        assertTrue(SpeedMath.median(emptyList()).isNaN())
    }

    @Test fun cleanKeepsTwoValuesUntouched() {
        val (kept, out) = SpeedMath.clean(listOf(1.0, 100.0), 0.0)
        assertEquals(listOf(1.0, 100.0), kept)
        assertTrue(out.isEmpty())
    }

    @Test fun cleanDropsAClearOutlier() {
        val (kept, out) = SpeedMath.clean(listOf(20.0, 21.0, 20.5, 39.3), 2.0)
        assertEquals(listOf(39.3), out)
        assertEquals(3, kept.size)
    }

    @Test fun cleanRespectsTheFloor() {
        // 1 apart is within the floor of 5: nothing goes, even though it's far in MAD terms.
        val (_, out) = SpeedMath.clean(listOf(10.0, 10.0, 10.0, 11.0), 5.0)
        assertTrue(out.isEmpty())
    }

    @Test fun cleanLeavesAtLeastThree() {
        val (kept, out) = SpeedMath.clean(listOf(10.0, 10.1, 9.9, 50.0, -40.0), 1.0)
        assertEquals(2, out.size)
        assertEquals(3, kept.size)
        // Of 3 values only one can go.
        val (k3, o3) = SpeedMath.clean(listOf(10.0, 10.0, 90.0), 1.0)
        assertEquals(1, o3.size)
        assertEquals(2, k3.size)
    }

    @Test fun gainIsHowMuchSooner() {
        assertEquals(20.0, SpeedMath.gain(10.0, 8.0), eps)
        assertEquals(-10.0, SpeedMath.gain(10.0, 11.0), eps)
    }

    @Test fun verdictWords() {
        assertTrue(SpeedMath.verdictWord(18.0).endsWith("worth it"))
        assertTrue(SpeedMath.verdictWord(5.0).endsWith("a little faster"))
        assertTrue(SpeedMath.verdictWord(1.0).startsWith("no gain"))
        assertTrue(SpeedMath.verdictWord(-4.0).startsWith("about the same"))
        assertTrue(SpeedMath.verdictWord(-12.0).endsWith("better one after the other"))
    }

    private fun run(bigRate: Double = 10.0, smallRate: Double = 20.0, both: Double = 0.8, help: Double = 1.0, sj: Double = 19.5, bj: Double = 30.0): SpeedRun {
        // 300 tokens each; alone at the given rates; at once each slower by [both]; the helper twice at [help].
        val b = Took(300, 300 / bigRate)
        val s = Took(300, 300 / smallRate)
        return SpeedRun(b, s, Took(300, 300 / (bigRate * both)), Took(300, 300 / (smallRate * both)),
            Took(300, 300 / (smallRate * help)), Took(300, 300 / (smallRate * help)), sj, bj)
    }

    @Test fun analyzeAveragesAndGains() {
        val raw = SpeedRaw("b", "Big", "s", "Small", listOf(run(), run(), run()))
        val res = SpeedMath.analyze(raw, 1L)
        assertEquals(10.0, res.bigRate, eps)
        assertEquals(20.0, res.smallRate, eps)
        // Alone: 30 s + 15 s = 45 s one after the other; at once the slower one takes 30/0.8 = 37.5 s: 16.7% sooner.
        assertEquals((1 - 37.5 / 45.0) * 100, res.record.bothGain, 1e-6)
        // The helper twice at the same speed as alone: 15 s each, 30 s in turn vs 15 s at once: 50% sooner.
        assertEquals(50.0, res.record.helperGain, 1e-6)
        assertEquals(19.5, res.record.smallJob, eps)
        assertEquals(3, res.record.runs)
        assertTrue(res.text.contains("Average of 3 runs."))
        assertTrue(res.text.contains("Small is 100% faster than Big: it condenses web pages."))
    }

    @Test fun pagesRuleAndRounding() {
        // 24.6% faster rounds to 25% but isn't enough: the text must not say "+25%".
        val raw = SpeedRaw("b", "Big", "s", "Small", listOf(run(bigRate = 10.0, smallRate = 12.46)))
        val text = SpeedMath.analyze(raw, 1L).text
        assertTrue(text, text.contains("isn't at least 25% faster than Big (+24.9%)"))
        assertTrue(text.contains("Big reads web pages itself."))
    }

    @Test fun leftOutRunsAreReported() {
        val raw = SpeedRaw("b", "Big", "s", "Small", listOf(run(), run()), left = 1)
        assertTrue(SpeedMath.analyze(raw, 1L).text.contains("1 run left out after errors."))
    }

    @Test fun prefixVerdict() {
        assertTrue(SpeedMath.reuses(PrefixCheck("m", 12000, 8.0, 1.2, 1.4)))
        assertFalse(SpeedMath.reuses(PrefixCheck("m", 12000, 8.0, 7.6, 7.9)))
        // Tiny waits: half a second at least.
        assertFalse(SpeedMath.reuses(PrefixCheck("m", 12000, 0.6, 0.3, 0.3)))
        val raw = SpeedRaw("b", "Big", "s", "Small", listOf(run()), prefix = listOf(PrefixCheck("Big", 11800, 8.0, 1.2, 1.4)))
        val res = SpeedMath.analyze(raw, 1L)
        assertEquals(8.0, res.record.prefixCold, eps)
        assertTrue(res.text.contains("the Tiiny reuses what it has read"))
    }

    @Test fun measuredHelperRule() {
        val models = listOf(
            Triple("q4b", "Qwen3-4B", 25.0),
            Triple("gpt", "gpt-oss-20b", 27.0),
            Triple("fast", "Fast-2B", 12.0),
            Triple("huge", "Huge-35B", 49.0),
        )
        val jobs = mapOf("Qwen3-4B" to 19.5, "gpt-oss-20b" to 38.0, "Fast-2B" to 21.0, "Huge-35B" to 15.0)
        val got = SpeedMath.measuredHelpers(jobs, models, setOf("q4b"))
        // Fast-2B: within 15% and light; gpt-oss: too slow; Huge: quick but far heavier on the NPU.
        assertEquals(setOf("fast"), got)
        // No recommended helper measured: nothing earned.
        assertTrue(SpeedMath.measuredHelpers(jobs, models, setOf("missing")).isEmpty())
    }

    @Test fun rawSurvivesTheTripFromTheNas() {
        val raw = SpeedRaw("b", "Big", "s", "Small", listOf(run(), run(sj = 18.0)), 1, listOf(PrefixCheck("Big", 11800, 8.0, 1.2, 1.4)))
        val back = SpeedMath.raw(SpeedMath.rawJson(raw))
        assertEquals(raw, back)
    }

    @Test fun recordSurvivesSaving() {
        val r = SpeedRecord(5L, "Big", "Small", 3, 10.0, 20.0, 16.0, 1.0, 0, 19.5, 30.0, 8.0, 1.2, "nas")
        assertEquals(r, SpeedRecord.from(r.json()))
        // An old record (before 4.8.0) reads with its new fields empty.
        val old = SpeedRecord.from(org.json.JSONObject("{\"at\":1,\"big\":\"B\",\"small\":\"S\",\"runs\":1,\"big_rate\":1,\"small_rate\":2,\"both\":3,\"helper\":4,\"outliers\":0}"))
        assertTrue(old.smallJob.isNaN() && old.prefixCold.isNaN())
        assertEquals("phone", old.by)
    }

    @Test fun chatWaitBands() {
        val b = ChatWaits.bands(listOf(1000 to 1.0, 2000 to 3.0, 10_000 to 5.0, 40_000 to 12.0, 0 to 99.0))
        assertEquals(listOf(Triple("short chats", 2.0, 2), Triple("medium", 5.0, 1), Triple("long", 12.0, 1)), b)
    }
}
