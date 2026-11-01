package com.vylia.privacy.domain.security

import com.vylia.privacy.data.export.CsvExporter
import com.vylia.privacy.data.trackers.ExodusTrackerParser
import com.vylia.privacy.domain.backup.BackupFile
import com.vylia.privacy.domain.backup.BackupSanitizer
import com.vylia.privacy.domain.history.AppPoint
import com.vylia.privacy.domain.history.EventType
import com.vylia.privacy.domain.history.History
import com.vylia.privacy.domain.history.HistoryEvent
import com.vylia.privacy.domain.history.ScoreSnapshot
import com.vylia.privacy.domain.rewards.RemovedApp
import com.vylia.privacy.domain.rewards.RewardsState
import com.vylia.privacy.domain.scoring.ConditionParser
import com.vylia.privacy.domain.scoring.RulesValidator
import com.vylia.privacy.domain.scoring.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Données venues de l'extérieur : sauvegarde, règles importées, base Exodus, export CSV. */
class ExternalDataSecurityTest {

    @Test
    fun `labels lose invisible and direction-changing characters and are bounded`() {
        assertEquals("Banquetxt.exe", Untrusted.text("Banque\u202Etxt.exe", 100))
        assertEquals("Line 1 Line 2", Untrusted.text("Line 1\nLine 2", 100))
        assertEquals("a b", Untrusted.text("a\n\t\u0000 b\u200B", 100))
        assertEquals(10, Untrusted.text("x".repeat(500), 10).length)
        assertEquals("?", Untrusted.text("\u202E\u0007", 10))
    }

    @Test
    fun `backup elements with bad identifiers or oversized lists are set aside`() {
        val good = HistoryEvent(1, EventType.INSTALLED, "com.good.app", "Good\u202E", score = 50)
        val snapshot = ScoreSnapshot(1, 50.0, 1.0, mapOf("com.ok" to AppPoint("Ok", 50, setOf("android.permission.CAMERA"))))
        val file = BackupFile(
            exportedAt = 1, app = "x",
            history = History(
                snapshots = listOf(
                    snapshot,
                    snapshot.copy(at = 2, apps = mapOf("../../etc" to AppPoint("x", 1))),
                    snapshot.copy(at = 3, apps = mapOf("com.ok" to AppPoint("x", 1, setOf("<script>")))),
                ),
                events = listOf(good, good.copy(packageName = "com.bad;rm -rf"), good.copy(permission = "a b")),
            ),
            rewards = RewardsState(
                removedApps = List(BackupSanitizer.MAX_REMOVED_APPS + 5) { RemovedApp("com.app$it", "A", 10, 1, 1) },
                completedScans = Int.MAX_VALUE,
                swaps = setOf("com.fine", "not a package"),
            ),
        )
        val (clean, invalid) = BackupSanitizer.clean(file)
        assertEquals(listOf(1L), clean.history.snapshots.map { it.at })
        assertEquals(listOf("Good"), clean.history.events.map { it.label })
        assertEquals(BackupSanitizer.MAX_REMOVED_APPS, clean.rewards.removedApps.size)
        assertEquals(BackupSanitizer.MAX_COUNTER, clean.rewards.completedScans)
        assertEquals(setOf("com.fine"), clean.rewards.swaps)
        assertEquals(2 + 2 + 5 + 1, invalid)
    }

    @Test
    fun `bundled rules pass the import checks`() {
        RulesValidator.validate(TestFixtures.rules, TestFixtures.rules.excludedPackages)
    }

    @Test
    fun `imported rules cannot hide an app nor use out of range values`() {
        val rules = TestFixtures.rules
        fun refused(r: com.vylia.privacy.domain.scoring.Rules) =
            assertThrows(IllegalArgumentException::class.java) { RulesValidator.validate(r, rules.excludedPackages) }
        refused(rules.copy(excludedPackages = rules.excludedPackages + "com.spy.app"))
        refused(rules.copy(permissionWeights = rules.permissionWeights + ("CAMERA" to -50)))
        refused(rules.copy(scoring = rules.scoring.copy(declaredOnlyFactor = Double.NaN)))
        refused(rules.copy(scoring = rules.scoring.copy(permissionCap = 10_000)))
        refused(rules.copy(categories = rules.categories - "DEFAULT"))
    }

    @Test
    fun `deeply nested or huge conditions are refused instead of exhausting the stack`() {
        val nested = "(".repeat(5_000) + "a" + ")".repeat(5_000)
        assertThrows(IllegalArgumentException::class.java) { ConditionParser(nested, setOf("a")).parse() }
        val deep = "(".repeat(40) + "a" + ")".repeat(40)
        assertThrows(IllegalArgumentException::class.java) { ConditionParser(deep, setOf("a")).parse() }
        val ok = "(".repeat(10) + "a" + ")".repeat(10)
        ConditionParser(ok, setOf("a")).parse()
    }

    @Test
    fun `downloaded tracker signatures must be plain package names`() {
        val json = """{"trackers": {
            "1": {"id": 1, "name": "Good", "code_signature": "com.good.|com.other."},
            "2": {"id": 2, "name": "Evil", "code_signature": "(a+)+$"},
            "3": {"id": 3, "name": "Evil2", "code_signature": "com.x.(.*)*"},
            "4": {"id": 4, "name": "Spoof\u202E", "code_signature": "com.spoof.", "website": "javascript:alert(1)"}
        }}"""
        val parsed = ExodusTrackerParser().parse(json)
        assertEquals(listOf(1, 4), parsed.map { it.tracker.id })
        assertEquals("Spoof", parsed[1].tracker.name)
        assertEquals(null, parsed[1].tracker.website)
        // La base intégrée passe entièrement les contrôles.
        val bundled = ExodusTrackerParser().parse(java.io.File("src/main/assets/exodus_trackers.json").readText())
        assertTrue(bundled.size > 400)
    }

    @Test
    fun `csv fields that would become formulas are neutralised`() {
        assertEquals("'=HYPERLINK(\"http://evil\")", CsvExporter.neutralize("=HYPERLINK(\"http://evil\")"))
        assertEquals("'+1", CsvExporter.neutralize("+1"))
        assertEquals("'@SUM(A1)", CsvExporter.neutralize("@SUM(A1)"))
        assertEquals("Signal", CsvExporter.neutralize("Signal"))
    }
}
