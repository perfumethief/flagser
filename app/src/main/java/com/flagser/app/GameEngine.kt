package com.flagser.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class QuestionKind { COUNTRY, FLAG, CAPITAL, PHONE }
data class AnswerOption(val key: String, val label: String, val countryCode: String? = null)
data class GameQuestion(
    val kind: QuestionKind,
    val title: String,
    val hero: String,
    val options: List<AnswerOption>,
    val correctKey: String
)
data class ResultState(val kicker: String, val score: String, val copy: String)

data class RunState(
    val family: String,
    val direction: String,
    val difficulty: String,
    val phoneMode: String,
    val flagDeck: List<Country> = emptyList(),
    val hardDeck: List<HardSet> = emptyList(),
    val phoneDeck: List<PhoneCode> = emptyList(),
    val total: Int,
    val cleared: Int = 0,
    val pot: Int = 0,
    val checkpointBasePot: Int = 0,
    val checkpointTarget: Int,
    val lives: Int,
    val maxLives: Int,
    val shield: Int = 0,
    val anchor: Int = 0,
    val skip: Int = 0,
    val peek: Int = 0,
    val rush: Boolean = false,
    val longshot: Boolean = false,
    val doubleDown: Boolean = false,
    val doubleTarget: Int? = null,
    val perfectRemaining: Int = 0,
    val perfectQueue: List<HardSet> = emptyList(),
    val question: GameQuestion? = null,
    val disabledKeys: Set<String> = emptySet(),
    val status: String = "choose one answer",
    val checkpointVisible: Boolean = false,
    val helperVisible: Boolean = false
)

class GameEngine(private val prefs: AppPrefs) {
    var run by mutableStateOf<RunState?>(null); private set
    var result by mutableStateOf<ResultState?>(null); private set

    private fun allDomesticCodes() = FlagserData.domesticCodes + prefs.customDomesticCodes
    private fun <T> List<T>.shuffledSafe() = if (size <= 1) this else shuffled(Random.Default)

    private fun progressiveFlagDeck(): List<Country> {
        val used = mutableSetOf<String>()
        val out = mutableListOf<Country>()
        fun take(pool: List<Country>, count: Int) {
            pool.filter { it.code !in used }.shuffledSafe().take(count).forEach { used += it.code; out += it }
        }
        fun pick(names: List<String>) = names.mapNotNull { FlagserData.byName[it] }
        take(pick(FlagserData.easyFlagNames), 10)
        take(pick(FlagserData.easyFlagNames + FlagserData.familiarFlagNames), 10)
        take(pick(FlagserData.familiarFlagNames + FlagserData.midFlagNames), 20)
        take(FlagserData.countries.filter { it.code !in used }, FlagserData.countries.size)
        return out
    }

    private fun progressiveInternationalDeck(): List<PhoneCode> {
        val byCode = FlagserData.internationalCodes.associateBy { it.code }
        val used = mutableSetOf<String>()
        val out = mutableListOf<PhoneCode>()
        fun take(codes: List<String>, count: Int) {
            codes.mapNotNull { byCode[it] }.filter { it.code !in used }.shuffledSafe().take(count).forEach {
                used += it.code; out += it
            }
        }
        take(FlagserData.commonInternational, 10)
        take(FlagserData.commonInternational + FlagserData.secondaryInternational, 15)
        out += FlagserData.internationalCodes.filter { it.code !in used }.shuffledSafe()
        return out
    }

    private fun progressiveDomesticDeck(): List<PhoneCode> {
        val preferredCountries = setOf("Indonesia","France","United Kingdom","Japan","South Korea","Belgium","Australia","Ukraine","Malaysia","Philippines")
        val all = allDomesticCodes()
        return all.filter { it.country in preferredCountries }.shuffledSafe() + all.filter { it.country !in preferredCountries }.shuffledSafe()
    }

    private fun phoneDeck(mode: String): List<PhoneCode> {
        if (mode == "international") return progressiveInternationalDeck()
        if (mode == "domestic") return progressiveDomesticDeck()
        val intl = progressiveInternationalDeck()
        val domestic = progressiveDomesticDeck()
        val first = intl.take(6) + domestic.take(4)
        val usedIntl = first.filter { it.code.startsWith("+") }.map { it.code }.toSet()
        return first.shuffledSafe() + (intl.filter { it.code !in usedIntl } + domestic.filter { it !in first }).shuffledSafe()
    }

    private fun hardDeck(): List<HardSet> {
        if (FlagserData.hardSets.isEmpty()) return emptyList()
        val out = mutableListOf<HardSet>()
        while (out.size < 80) out += FlagserData.hardSets.shuffledSafe()
        return out.take(80)
    }

    fun start(): Boolean {
        result = null
        val isPhone = prefs.selectedFamily == "phone" && prefs.phoneGatewayOwned() && prefs.phoneModeOwned(prefs.selectedPhoneMode)
        if (prefs.selectedFamily == "phone" && !isPhone) return false
        val difficulty = if (isPhone) "standard" else prefs.selectedDifficulty
        val direction = prefs.selectedDirection
        val capitalGame = !isPhone && direction in setOf("capital", "capitalmixed")
        val lives = if (difficulty == "hardcore") 1 else 3

        var flags = if (!isPhone && difficulty == "standard") progressiveFlagDeck() else emptyList()
        var hard = if (!isPhone && difficulty != "standard") hardDeck() else emptyList()
        val phones = if (isPhone) phoneDeck(prefs.selectedPhoneMode) else emptyList()
        if (capitalGame) {
            flags = flags.filter { FlagserData.capitals.containsKey(it.code) }
            hard = hard.filter { FlagserData.capitals.containsKey(it.current.code) }
        }
        val total = when {
            isPhone -> phones.size
            difficulty == "standard" -> flags.size
            else -> hard.size
        }
        if (total == 0) return false
        run = RunState(
            family = if (isPhone) "phone" else "flags",
            direction = direction,
            difficulty = difficulty,
            phoneMode = prefs.selectedPhoneMode,
            flagDeck = flags,
            hardDeck = hard,
            phoneDeck = phones,
            total = total,
            checkpointTarget = min(10, total),
            lives = lives,
            maxLives = lives
        )
        renderNextQuestion()
        return true
    }

    fun clearResult() { result = null }
    fun abandon() { run = null }

    fun rate(): Int {
        val r = run ?: return 0
        return if (r.family == "phone") 100 else 10 + 5 * floor(r.cleared / 10.0).toInt()
    }

    private fun normalFlagBase(r: RunState): HardSet {
        val current = r.flagDeck[r.cleared.coerceAtMost(r.flagDeck.lastIndex)]
        val others = FlagserData.countries.filter { it.code != current.code }.shuffledSafe().take(2)
        return HardSet(current, (listOf(current) + others).shuffledSafe())
    }

    private fun hardFlagBase(r: RunState): HardSet = r.hardDeck[r.cleared % r.hardDeck.size].let { it.copy(options = it.options.shuffledSafe()) }

    private fun perfectBase(r: RunState): HardSet {
        val q = r.perfectQueue.firstOrNull()
        if (q != null) return q.copy(options = q.options.shuffledSafe())
        val pool = FlagserData.hardSets.filter { it.current.name in FlagserData.perfectNames }.shuffledSafe().take(5)
        val first = pool.firstOrNull() ?: hardFlagBase(r)
        run = r.copy(perfectQueue = pool)
        return first.copy(options = first.options.shuffledSafe())
    }

    private fun countryQuestion(base: HardSet) = GameQuestion(
        QuestionKind.COUNTRY, "which country?", flagEmoji(base.current.code),
        base.options.shuffledSafe().map { AnswerOption(it.code, it.name, it.code) }, base.current.code
    )

    private fun flagQuestion(base: HardSet) = GameQuestion(
        QuestionKind.FLAG, "which flag?", base.current.name,
        base.options.shuffledSafe().map { AnswerOption(it.code, flagEmoji(it.code), it.code) }, base.current.code
    )

    private fun capitalQuestion(base: HardSet, r: RunState): GameQuestion {
        val correct = FlagserData.capitals[base.current.code] ?: ""
        val familiarCodes = (FlagserData.easyFlagNames + FlagserData.familiarFlagNames).mapNotNull { FlagserData.byName[it]?.code }.toSet()
        val near = base.options.filter { it.code != base.current.code }.mapNotNull { FlagserData.capitals[it.code] }
        val pool = if (r.difficulty == "standard" && r.cleared < 20)
            FlagserData.capitals.filterKeys { it in familiarCodes }.values.toList()
        else FlagserData.capitals.values.toList()
        val wrong = (near.shuffledSafe() + pool.shuffledSafe() + FlagserData.capitals.values.toList().shuffledSafe()).filter { it != correct }.distinct().take(2)
        return GameQuestion(QuestionKind.CAPITAL, "which capital?", flagEmoji(base.current.code), (listOf(correct) + wrong).shuffledSafe().map { AnswerOption(it, it) }, correct)
    }

    private fun phoneQuestion(r: RunState): GameQuestion {
        val current = r.phoneDeck[r.cleared % r.phoneDeck.size]
        val countries = r.phoneDeck.map { it.country }.distinct()
        val wrong = countries.filter { it != current.country }.shuffledSafe().take(2)
        return GameQuestion(QuestionKind.PHONE, "which country?", current.code, (listOf(current.country) + wrong).shuffledSafe().map { AnswerOption(it, it) }, current.country)
    }

    private fun buildQuestion(r: RunState): GameQuestion {
        if (r.family == "phone") return phoneQuestion(r)
        val base = when {
            r.perfectRemaining > 0 -> perfectBase(r)
            r.difficulty == "standard" -> normalFlagBase(r)
            else -> hardFlagBase(r)
        }
        return when {
            r.perfectRemaining > 0 -> countryQuestion(base)
            r.direction == "reverse" -> flagQuestion(base)
            r.direction == "capital" || (r.direction == "capitalmixed" && r.cleared % 2 == 1) -> capitalQuestion(base, r)
            else -> countryQuestion(base)
        }
    }

    private fun stageStatus(r: RunState): String = when {
        r.perfectRemaining > 0 -> "✨ perfect · ${r.perfectRemaining} left"
        r.family == "phone" -> when (r.cleared + 1) { in 1..10 -> "opening · common codes"; in 11..20 -> "opening · familiar"; else -> "choose one answer" }
        r.difficulty == "standard" -> when (r.cleared + 1) { in 1..10 -> "opening · easy"; in 11..20 -> "opening · familiar"; in 21..40 -> "warming up"; else -> "choose one answer" }
        else -> "choose one answer"
    }

    private fun renderNextQuestion() {
        val r = run ?: return
        if (r.cleared >= r.total) { completeRun(); return }
        val q = buildQuestion(r)
        run = (run ?: r).copy(question = q, disabledKeys = emptySet(), status = stageStatus(run ?: r), checkpointVisible = false, helperVisible = false)
    }

    private fun finish(kicker: String, score: String, copy: String) {
        result = ResultState(kicker, score, copy)
        run = null
    }

    fun answer(key: String) {
        val r = run ?: return
        val q = r.question ?: return
        if (key in r.disabledKeys || r.checkpointVisible || r.helperVisible) return
        if (key == q.correctKey) {
            var nr = r.copy(pot = r.pot + rate(), cleared = r.cleared + 1, status = "")
            val hitCheckpoint = nr.cleared >= nr.checkpointTarget
            var message = ""
            if (hitCheckpoint && nr.rush) {
                nr = nr.copy(pot = nr.pot + 500, rush = false)
                message = "🔥 rush complete · +500"
            }
            if (nr.perfectRemaining > 0) {
                val remain = nr.perfectRemaining - 1
                val queue = nr.perfectQueue.drop(1)
                nr = nr.copy(perfectRemaining = remain, perfectQueue = queue)
                if (remain == 0) {
                    nr = nr.copy(pot = nr.pot + 750)
                    message = "✨ perfect · +750 bonus"
                }
            }
            if (nr.doubleDown && nr.doubleTarget != null && nr.cleared >= nr.doubleTarget) {
                nr = nr.copy(pot = nr.pot + 5000, doubleDown = false, doubleTarget = null)
                message = if (message.isBlank()) "2️⃣ double down complete · +5,000" else "$message · 2️⃣ +5,000"
            }
            run = nr.copy(status = message)
            if (nr.cleared >= nr.total) { completeRun(); return }
            if (hitCheckpoint) { reachCheckpoint(); return }
            renderNextQuestion()
            return
        }

        if (r.perfectRemaining > 0) { finish("perfect failed", "0", "the ${r.pot} unbanked pts were lost."); return }
        if (r.doubleDown) { finish("double down failed", "0", "the ${r.pot} unbanked pts were lost."); return }
        if (r.shield > 0) {
            run = r.copy(shield = r.shield - 1, disabledKeys = r.disabledKeys + key, status = "shielded. mistake cancelled")
            return
        }
        val lives = r.lives - 1
        val anchored = r.anchor > 0
        val pot = if (anchored) r.pot else r.checkpointBasePot
        val anchor = if (anchored) r.anchor - 1 else r.anchor
        if (lives <= 0) { finish("out of lives", "0", "the $pot unbanked pts were lost."); return }
        run = r.copy(lives = lives, pot = pot, anchor = anchor, disabledKeys = r.disabledKeys + key,
            status = if (anchored) "wrong. life lost · anchor kept your stage points" else "wrong. life lost · stage earnings wiped")
    }

    private fun nextRegularCheckpoint(r: RunState): Int = min(r.total, ceil((r.cleared + 1) / 10.0).toInt() * 10)

    private fun reachCheckpoint() {
        val r = run ?: return
        val nr = if (r.longshot) r.copy(pot = r.pot + 100, longshot = false) else r
        run = nr.copy(checkpointBasePot = nr.pot, checkpointVisible = true, helperVisible = false)
    }

    fun cashOut() {
        val r = run ?: return
        prefs.addPoints(r.pot)
        finish("cashed out", "+${r.pot}", "${r.pot} pts are safe.")
    }

    fun keepGoing() {
        val r = run ?: return
        run = r.copy(checkpointVisible = false, helperVisible = true)
    }

    fun helperChoices(): List<HelperItem> {
        val r = run ?: return emptyList()
        var keys = prefs.enabledHelpers.filter { prefs.helperOwned(it) }
            .filterNot { r.family == "phone" && it == "perfect" }
            .filterNot { it == "longshot" && nextRegularCheckpoint(r) + 5 > r.total }
            .filterNot { it == "double" && r.cleared < 30 }
        if (keys.size < 3) keys = (keys + listOf("shield","restore","peek","skip").filter { prefs.helperOwned(it) }).distinct()
        return keys.shuffledSafe().take(3).mapNotNull { FlagserData.helpers[it] }
    }

    fun chooseHelper(key: String) {
        var r = run ?: return
        val defaultCheckpoint = nextRegularCheckpoint(r)
        r = r.copy(checkpointTarget = defaultCheckpoint, rush = false, longshot = false, checkpointVisible = false, helperVisible = false)
        when (key) {
            "shield" -> r = r.copy(shield = r.shield + 1)
            "restore" -> r = r.copy(maxLives = r.maxLives + 1, lives = r.lives + 1)
            "double" -> r = r.copy(doubleDown = true, doubleTarget = r.checkpointTarget, lives = 1)
            "skip" -> r = r.copy(skip = r.skip + 1)
            "peek" -> r = r.copy(peek = r.peek + 1)
            "anchor" -> r = r.copy(anchor = r.anchor + 1)
            "gamble" -> {
                when (listOf("life","shield","skip","peek","points").random()) {
                    "life" -> r = r.copy(maxLives = r.maxLives + 1, lives = r.lives + 1, status = "❤️ +1 life")
                    "shield" -> r = r.copy(shield = r.shield + 1, status = "🛡️ +1 shield")
                    "skip" -> r = r.copy(skip = r.skip + 1, status = "⏭️ +1 skip")
                    "peek" -> r = r.copy(peek = r.peek + 1, status = "👁️ +1 peek")
                    else -> r = r.copy(pot = r.pot + 50, status = "+50 unbanked pts")
                }
            }
            "insurance" -> {
                val banked = if (r.pot > 0) max(1, floor(r.pot * .20).toInt()) else 0
                if (banked > 0) prefs.addPoints(banked)
                r = r.copy(pot = max(0, r.pot - banked), checkpointBasePot = max(0, r.pot - banked), status = if (banked > 0) "🏦 $banked pts sent to saved points" else "🏦 nothing to bank yet")
            }
            "perfect" -> {
                val pool = FlagserData.hardSets.filter { it.current.name in FlagserData.perfectNames }.shuffledSafe().take(5)
                r = r.copy(perfectRemaining = 5, perfectQueue = pool)
            }
            "rush" -> r = r.copy(rush = true, checkpointTarget = min(r.total, r.cleared + 5))
            "longshot" -> r = r.copy(longshot = true, checkpointTarget = min(r.total, defaultCheckpoint + 5))
        }
        run = r
        renderNextQuestion()
    }

    fun useSkip() {
        val r = run ?: return
        if (r.skip <= 0 || r.perfectRemaining > 0 || r.doubleDown) return
        val nr = r.copy(skip = r.skip - 1, cleared = r.cleared + 1, status = "skipped · no points")
        run = nr
        if (nr.cleared >= nr.total) { completeRun(); return }
        if (nr.cleared >= nr.checkpointTarget) { reachCheckpoint(); return }
        renderNextQuestion()
    }

    fun usePeek() {
        val r = run ?: return
        val q = r.question ?: return
        if (r.peek <= 0 || r.perfectRemaining > 0) return
        val wrong = q.options.filter { it.key != q.correctKey && it.key !in r.disabledKeys }
        if (wrong.size < 2) return
        run = r.copy(peek = r.peek - 1, disabledKeys = r.disabledKeys + wrong.random().key, status = "50 / 50")
    }

    private fun completeRun() {
        val r = run ?: return
        prefs.addPoints(r.pot)
        finish("run complete", "+${r.pot}", "all ${r.total} questions cleared. the whole pot was banked.")
    }
}
