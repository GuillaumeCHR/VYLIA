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
import com.vylia.privacy.domain.security.StalkerwareDatabase
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
    fun `backup threshold crossings out of range or with bad names are set aside`() {
        fun crossing(score: Int, pkg: String?) = com.vylia.privacy.domain.alert.ThresholdCrossing(
            at = 1, score = score, threshold = 70,
            cause = com.vylia.privacy.domain.alert.DropCause(com.vylia.privacy.domain.alert.DropCause.Type.INSTALLED, pkg, "Spy\u202Eapp", 20),
        )
        val file = BackupFile(exportedAt = 1, app = "test", thresholdCrossings = listOf(crossing(60, "com.ok"), crossing(150, "com.ok"), crossing(60, "bad name;")))
        val clean = BackupSanitizer.clean(file)
        assertEquals(listOf("com.ok"), clean.file.thresholdCrossings.map { it.cause.packageName })
        assertEquals("Spyapp", clean.file.thresholdCrossings.single().cause.label)
        assertEquals(2, clean.invalid)
    }

    @Test
    fun `stalkerware database entries with bad identifiers are set aside`() {
        val loaded = StalkerwareDatabase.parse(
            "{\"commit\":\"not a commit\",\"apps\":[{\"name\":\"Spy\\u202Eware\",\"packages\":[\"com.spy.app\",\"bad name;\"]," +
                "\"certificates\":[\"31A6ECECD97CF39BC4126B8745CD94A7C30BF81C\",\"zz\"]}]}"
        )
        assertEquals("", loaded.commit)
        val scan = com.vylia.privacy.domain.scoring.AppScan("com.spy.app", 35, "CATEGORY_UNDEFINED", emptySet(), emptySet(), emptyList())
        assertEquals("Spyware", loaded.iocs.match(scan))
        assertEquals(null, loaded.iocs.match(scan.copy(packageName = "bad name;")))
    }

    @Test
    fun `bundled rules pass the import checks`() {
        val raw = java.io.File("src/main/assets/vylia_rules_v3.json").readText()
        RulesValidator.checkDocument(raw)
        RulesValidator.validate(TestFixtures.rules, TestFixtures.rules)
        // Le fichier exporté est celui-ci : il se réimporte tel quel.
        com.vylia.privacy.data.rules.RuleMatrixRepository.checked(raw, TestFixtures.rules)
    }

    @Test
    fun `imported rules cannot hide an app nor use out of range values`() {
        val rules = TestFixtures.rules
        fun refused(r: com.vylia.privacy.domain.scoring.Rules) =
            assertThrows(IllegalArgumentException::class.java) { RulesValidator.validate(r, rules) }
        refused(rules.copy(excludedPackages = rules.excludedPackages + "com.spy.app"))
        refused(rules.copy(permissionWeights = rules.permissionWeights + ("CAMERA" to -50)))
        refused(rules.copy(scoring = rules.scoring.copy(declaredOnlyFactor = Double.NaN)))
        refused(rules.copy(scoring = rules.scoring.copy(permissionCap = 10_000)))
        refused(rules.copy(categories = rules.categories - "DEFAULT"))
    }

    @Test
    fun `imported rules cannot crash the engine`() {
        val rules = TestFixtures.rules
        fun refused(r: com.vylia.privacy.domain.scoring.Rules) =
            assertThrows(IllegalArgumentException::class.java) { RulesValidator.validate(r, rules) }
        // Sans note à 0, un score bas n'aurait pas de note : le moteur planterait à chaque scan.
        refused(rules.copy(scoring = rules.scoring.copy(grades = rules.scoring.grades.filter { it.min > 0 })))
        refused(rules.copy(scoring = rules.scoring.copy(contextMultipliers = rules.scoring.contextMultipliers - "forbidden")))
        refused(rules.copy(scoring = rules.scoring.copy(grades = rules.scoring.grades + rules.scoring.grades.first())))
        refused(rules.copy(scoring = rules.scoring.copy(grades = rules.scoring.grades.map { it.copy(grade = "=1") })))
        refused(rules.copy(packageOverrides = rules.packageOverrides + ("com.example.app" to "NO_SUCH_CATEGORY")))
    }

    @Test
    fun `imported rules cannot weaken the security checks`() {
        val rules = TestFixtures.rules
        val security = rules.security
        fun refused(r: com.vylia.privacy.domain.scoring.SecurityRules) =
            assertThrows(IllegalArgumentException::class.java) { RulesValidator.validate(rules.copy(security = r), rules) }
        val (id, pattern) = security.patterns.entries.first { security.severityEffects[it.value.severity]?.scoreCap != null }
        refused(security.copy(patterns = security.patterns - id))
        refused(security.copy(patterns = security.patterns + (id to pattern.copy(condition = "false"))))
        refused(security.copy(patterns = security.patterns + (id to pattern.copy(exemptPackages = setOf("com.spy.app")))))
        refused(security.copy(patterns = security.patterns + (id to pattern.copy(severity = "medium"))))
        refused(security.copy(severityEffects = security.severityEffects.mapValues { it.value.copy(scoreCap = null) }))
        // Durcir reste permis : un plafond plus bas pour tous.
        RulesValidator.validate(
            rules.copy(security = security.copy(severityEffects = security.severityEffects.mapValues { it.value.copy(scoreCap = 0) })),
            rules,
        )
    }

    @Test
    fun `imported rules are refused with invalid names`() {
        val rules = TestFixtures.rules
        fun refused(r: com.vylia.privacy.domain.scoring.Rules) =
            assertThrows(IllegalArgumentException::class.java) { RulesValidator.validate(r, rules) }
        refused(rules.copy(permissionWeights = rules.permissionWeights + ("CAMERA; DROP" to 5)))
        refused(rules.copy(packageOverrides = rules.packageOverrides + ("not a package" to "DEFAULT")))
        refused(rules.copy(categories = rules.categories + ("../evil" to rules.categories.getValue("DEFAULT"))))
        refused(rules.copy(badges = rules.badges.copy(definitions = listOf(rules.badges.definitions.first().copy(icon = "../../x")))))
    }

    @Test
    fun `imported rules file is refused with hidden characters or deep nesting`() {
        val raw = java.io.File("src/main/assets/vylia_rules_v3.json").readText()
        fun refused(content: String) =
            assertThrows(IllegalArgumentException::class.java) { RulesValidator.checkDocument(content) }
        // Inversion bidirectionnelle (U+202E) dans un libellé, écrite en clair ou en échappement JSON.
        refused(raw.replaceFirst("\"Stalkerware connu\"", "\"Stalkerware \u202Econnu\""))
        refused(raw.replaceFirst("\"Stalkerware connu\"", "\"Stalkerware \\u202Econnu\""))
        refused(raw.replaceFirst("\"Stalkerware connu\"", "\"Stalkerware\\u0000connu\""))
        // Une clé inconnue, ignorée par le décodeur, n'est pas pour autant ignorée par le contrôle.
        refused(raw.replaceFirst("{", "{\"x\u200B\": 1,"))
        refused(raw.replaceFirst("{", "{\"deep\": " + "[".repeat(5_000) + "]".repeat(5_000) + ","))
        assertEquals(1, RulesValidator.depthOf("{\"a\": \"[[[[\"}"))
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
