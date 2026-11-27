package app.tiinyremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityMathTest {
    @Test fun labelsAndOrder() {
        assertEquals(listOf("A", "B", "C"), QualityMath.labels(3))
        val models = listOf("m1", "m2", "m3", "m4")
        val o1 = QualityMath.order(models, "summary", 42L)
        // A shuffle of the same models, the same for the same seed and task.
        assertEquals(models.toSet(), o1.toSet())
        assertEquals(o1, QualityMath.order(models, "summary", 42L))
    }

    @Test fun parsesJsonAfterThinking() {
        val reply = "<think>A is better because…{not json}</think>Here you go:\n{\"A\": {\"score\": 8, \"why\": \"right answer\"}, \"B\": {\"score\": 12}, \"C\": 3}"
        val v = QualityMath.parseVerdict(reply, listOf("A", "B", "C"))
        assertEquals(8.0, v.getValue("A").first, 0.0)
        assertEquals("right answer", v.getValue("A").second)
        assertEquals(10.0, v.getValue("B").first, 0.0) // clamped
        assertEquals(3.0, v.getValue("C").first, 0.0)
    }

    @Test fun parsesPlainLines() {
        val v = QualityMath.parseVerdict("Answer A: 7\nB - 4\n**C**: 9.5", listOf("A", "B", "C"))
        assertEquals(7.0, v.getValue("A").first, 0.0)
        assertEquals(4.0, v.getValue("B").first, 0.0)
        assertEquals(9.5, v.getValue("C").first, 0.0)
    }

    @Test fun ignoresUnknownLabels() {
        val v = QualityMath.parseVerdict("{\"A\": 5, \"Z\": 9}", listOf("A", "B"))
        assertEquals(setOf("A"), v.keys)
    }

    private fun raw(): QualityRaw {
        val tasks = QualityMath.TASKS.map { it.id }
        val answers = tasks.flatMap { t -> listOf(QualityAnswer(t, "Big", "answer", 10.0), QualityAnswer(t, "Small", if (t == "code") "" else "answer", 4.0)) }
        // Shown as Small (A), Big (B) everywhere; Big gets 8, Small 6.
        val order = tasks.associateWith { listOf("Small", "Big") }
        val verdicts = tasks.associateWith { "{\"A\": {\"score\": 6}, \"B\": {\"score\": 8}}" }
        return QualityRaw("Big", answers, order, verdicts)
    }

    @Test fun analyzeMapsLabelsBackToModels() {
        val scores = QualityMath.analyze(raw())
        assertEquals("Big", scores[0].model)
        assertEquals(8.0, scores[0].average, 1e-9)
        // Small: 6 everywhere but code, where it gave no answer: 1.
        val small = scores.first { it.model == "Small" }
        assertEquals(1.0, small.perTask.getValue("code"), 0.0)
        assertEquals((6.0 * (QualityMath.TASKS.size - 1) + 1.0) / QualityMath.TASKS.size, small.average, 1e-9)
        assertEquals(4.0, small.secs, 1e-9)
    }

    @Test fun summaryWarnsWhenTheGraderGradedItself() {
        assertTrue(QualityMath.summary(raw()).contains("graded its own answers too"))
    }

    @Test fun rawAndRecordsSurviveTheTrip() {
        val r = raw()
        assertEquals(r, QualityMath.raw(QualityMath.rawJson(r)))
        val rec = QualityMath.records(r, 7L, "nas").first()
        assertEquals(rec, QualityRecord.from(rec.json()))
    }

    @Test fun everyTaskHasARubricAndAUniqueId() {
        val ids = QualityMath.TASKS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(QualityMath.TASKS.all { it.rubric.length > 40 && it.prompt.length > 40 })
    }
}
