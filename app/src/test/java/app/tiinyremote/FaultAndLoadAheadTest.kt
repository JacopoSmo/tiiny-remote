package app.tiinyremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FaultAndLoadAheadTest {
    @Test fun faultPairsCountEachWayRound() {
        val f = listOf(
            NpuFault(1, listOf("gpt-oss-20b", "Qwen3.5-9B-Turbo")),
            NpuFault(2, listOf("Qwen3-4B", "gpt-oss-20b")),
            NpuFault(3, listOf("gpt-oss-20b", "Qwen3-4B")),
            NpuFault(4, listOf("Alone")),
        )
        val p = FaultMath.pairs(f)
        assertEquals(2 to 3L, p[FaultMath.key("gpt-oss-20b", "Qwen3-4B")])
        assertEquals(1 to 1L, p[FaultMath.key("Qwen3.5-9B-Turbo", "gpt-oss-20b")])
        assertEquals(2, p.size)
    }

    @Test fun warnFromTwoFaultsUnlessSilenced() {
        val f = listOf(NpuFault(1, listOf("A", "B")), NpuFault(2, listOf("A", "B")), NpuFault(3, listOf("A", "C")))
        assertEquals(Triple("B", 2, 2L), FaultMath.warn(f, "A", listOf("B", "C"), emptySet()))
        assertNull(FaultMath.warn(f, "A", listOf("C"), emptySet()))            // one fault only
        assertNull(FaultMath.warn(f, "A", listOf("B"), setOf(FaultMath.key("B", "A"))))
        assertNull(FaultMath.warn(f, "A", listOf("A"), emptySet()))            // itself
    }

    private val zone = ZoneId.of("Europe/Zurich")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun planNeedsTwoDays() {
        // Mondays 2, 9 and 16 Nov 2026 around 08:xx with the 27B; a single Tuesday with another one.
        val uses = listOf(
            ModelUse(at(2026, 11, 2, 8, 10), "Q/27B", "Qwen3.8-27B"),
            ModelUse(at(2026, 11, 9, 8, 40), "Q/27B", "Qwen3.8-27B"),
            ModelUse(at(2026, 11, 16, 8, 5), "Q/27B", "Qwen3.8-27B"),
            ModelUse(at(2026, 11, 16, 8, 50), "Q/27B", "Qwen3.8-27B"), // the same day again: one day
            ModelUse(at(2026, 11, 17, 20, 0), "Q/Img", "Qwen-Image"),
        )
        val now = at(2026, 11, 20, 12)
        val plan = LoadAheadMath.plan(uses, zone, now)
        assertEquals(listOf(UseSlot(1, 8, "Q/27B", "Qwen3.8-27B", 3)), plan)
        // Older than four weeks: forgotten.
        assertTrue(LoadAheadMath.plan(uses, zone, at(2027, 1, 30, 12)).isEmpty())
    }

    @Test fun dueTenMinutesBefore() {
        val plan = listOf(UseSlot(1, 8, "Q/27B", "Qwen3.8-27B", 3))
        assertEquals(plan, LoadAheadMath.due(plan, ZonedDateTime.of(2026, 11, 23, 7, 52, 0, 0, zone)))  // Monday 07:52
        assertEquals(plan, LoadAheadMath.due(plan, ZonedDateTime.of(2026, 11, 23, 8, 30, 0, 0, zone)))
        assertTrue(LoadAheadMath.due(plan, ZonedDateTime.of(2026, 11, 23, 7, 40, 0, 0, zone)).isEmpty())
        assertTrue(LoadAheadMath.due(plan, ZonedDateTime.of(2026, 11, 24, 8, 30, 0, 0, zone)).isEmpty()) // Tuesday
    }

    @Test fun describeGroupsDays() {
        val plan = (1..5).map { UseSlot(it, 8, "m", "Qwen3.8-27B", 2) } + UseSlot(6, 10, "m", "Qwen3.8-27B", 2)
        assertEquals(listOf("Qwen3.8-27B: Mon–Fri 08:00 · Sat 10:00"), LoadAheadMath.describe(plan))
    }
}
