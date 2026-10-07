package com.flagser.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Country(val code: String, val name: String)
data class PhoneCode(val code: String, val country: String, val custom: Boolean = false, val id: String? = null)
data class HardSet(val current: Country, val options: List<Country>)
data class UnlockItem(val key: String, val name: String, val price: Int, val desc: String, val icon: String)
data class HelperItem(val key: String, val emoji: String, val name: String, val copy: String, val price: Int = 0, val free: Boolean = false)
data class StudyItem(val key: String, val family: String, val type: String, val name: String, val price: Int, val desc: String, val icon: String)

object FlagserData {
    lateinit var countries: List<Country>
    lateinit var internationalCodes: List<PhoneCode>
    lateinit var domesticCodes: List<PhoneCode>
    lateinit var capitals: Map<String, String>
    lateinit var hardSets: List<HardSet>
    lateinit var byName: Map<String, Country>

    fun load(context: Context) {
        if (::countries.isInitialized) return
        countries = JSONArray(context.assets.open("countries.json").bufferedReader().use { it.readText() }).let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { Country(it.getString("code"), it.getString("name")) } }
        }
        internationalCodes = JSONArray(context.assets.open("international_codes.json").bufferedReader().use { it.readText() }).let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { PhoneCode(it.getString("code"), it.getString("country")) } }
        }
        domesticCodes = JSONArray(context.assets.open("domestic_codes.json").bufferedReader().use { it.readText() }).let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { PhoneCode(it.getString("code"), it.getString("country")) } }
        }
        capitals = JSONObject(context.assets.open("capitals.json").bufferedReader().use { it.readText() }).let { o ->
            o.keys().asSequence().associateWith { o.getString(it) }
        }
        byName = countries.associateBy { it.name }
        hardSets = JSONArray(context.assets.open("hard_sets.json").bufferedReader().use { it.readText() }).let { a ->
            buildList {
                repeat(a.length()) { i ->
                    val row = a.getJSONArray(i)
                    val current = byName[row.getString(0)] ?: return@repeat
                    val optionNames = row.getJSONArray(1)
                    val options = List(optionNames.length()) { j -> byName[optionNames.getString(j)] }.filterNotNull()
                    if (options.size == 3) add(HardSet(current, options))
                }
            }
        }
    }

    val easyFlagNames = listOf(
        "France","Germany","Italy","Spain","United Kingdom","United States","Canada","Japan","Brazil","India",
        "China","Australia","Mexico","Belgium","Netherlands","Switzerland","Sweden","Norway","Denmark","Finland"
    )
    val familiarFlagNames = listOf(
        "Portugal","Greece","Ireland","Poland","Ukraine","Austria","Türkiye","South Korea","Argentina","South Africa",
        "Morocco","Egypt","Nigeria","Kenya","Thailand","Vietnam","Indonesia","Philippines","Malaysia","Singapore",
        "New Zealand","Iceland","Romania","Bulgaria","Croatia","Serbia","Czechia","Slovakia","Hungary","Israel"
    )
    val midFlagNames = listOf(
        "Chile","Colombia","Peru","Ecuador","Venezuela","Uruguay","Paraguay","Bolivia",
        "Saudi Arabia","United Arab Emirates","Qatar","Jordan","Lebanon","Pakistan","Bangladesh","Sri Lanka","Nepal",
        "Algeria","Tunisia","Ghana","Cameroon","Ethiopia","Tanzania","Uganda","Zimbabwe",
        "Lithuania","Latvia","Estonia","Slovenia","Bosnia and Herzegovina","North Macedonia","Albania","Georgia","Armenia"
    )
    val commonInternational = listOf(
        "+33","+49","+39","+34","+44","+32","+31","+41","+43","+45","+46","+47","+48",
        "+1","+81","+82","+86","+91","+61","+64","+55","+52","+54","+27","+90","+30","+351"
    )
    val secondaryInternational = listOf(
        "+62","+63","+60","+65","+66","+84","+380","+40","+36","+420","+421","+385","+386",
        "+972","+971","+974","+212","+213","+216","+234","+254","+353","+354","+358"
    )

    val helpers = linkedMapOf(
        "shield" to HelperItem("shield","🛡️","shield","cancel one mistake completely", free = true),
        "restore" to HelperItem("restore","❤️","heart","gain +1 life", free = true),
        "double" to HelperItem("double","2️⃣","double down","drop to 1 life. Finish the whole next round without a mistake and earn +5,000 pts.", free = true),
        "skip" to HelperItem("skip","⏭️","skip","store one skip · no points earned", free = true),
        "peek" to HelperItem("peek","👁️","peek","store one 50 / 50", free = true),
        "anchor" to HelperItem("anchor","⚓","anchor","one mistake keeps current-stage points",900),
        "gamble" to HelperItem("gamble","🎲","gamble","roll a random reward immediately",1200),
        "insurance" to HelperItem("insurance","🏦","insurance","bank 20% of your current pot instantly",1600),
        "rush" to HelperItem("rush","🔥","rush","reach the next checkpoint after 5 questions instead of 10 · +500 pts when you make it",1400),
        "longshot" to HelperItem("longshot","🎯","longshot","next checkpoint +5 questions · +100 if reached",1800),
        "perfect" to HelperItem("perfect","✨","perfect","5 brutal flags · +750 · one miss kills you",2200)
    )

    val modeUnlocks = linkedMapOf(
        "reverse" to UnlockItem("reverse","reverse",5000,"country name → choose the correct flag","↔"),
        "capital" to UnlockItem("capital","capital",6500,"flag → choose its capital city","◉"),
        "capitalmixed" to UnlockItem("capitalmixed","capital mixed",9000,"flag → country or capital questions in one run","◐"),
        "medium" to UnlockItem("medium","medium",8500,"ultradifficult flag pool · 3 lives","◆"),
        "hardcore" to UnlockItem("hardcore","hardcore",14000,"ultradifficult flag pool · 1 life","!"),
        "phonecodes" to UnlockItem("phonecodes","phone codes",12000,"opens a second game family and its own Unlocks tab","+")
    )

    val phoneUnlocks = linkedMapOf(
        "international" to UnlockItem("international","+ international",2500,"+33, +62, +81 → choose the country","+"),
        "domestic" to UnlockItem("domestic","domestic",5000,"0813, 07, 010… → choose the country","⌕"),
        "mixed" to UnlockItem("mixed","mixed",7500,"international and domestic codes in one run","±")
    )

    val studyUnlocks = linkedMapOf(
        "flagGuide" to StudyItem("flagGuide","flags","guide","flag study guide",2500,"browse flags with their country names","▤"),
        "flagCards" to StudyItem("flagCards","flags","flash","flag flashcards",4000,"flip through flags and recall the country","◇"),
        "phoneGuide" to StudyItem("phoneGuide","phone","guide","phone code study guide",2500,"browse international and domestic codes","▤"),
        "phoneCards" to StudyItem("phoneCards","phone","flash","phone code flashcards",4000,"learn code → country with flip cards","◇")
    )

    data class Accent(val key: String, val name: String, val hex: Long, val price: Int)
    val accents = linkedMapOf(
        "white" to Accent("white","white",0xFFF2EEE6,0),
        "yellow" to Accent("yellow","yellow",0xFFF0DC46,600),
        "cyan" to Accent("cyan","cyan",0xFF63DEEE,900),
        "green" to Accent("green","green",0xFF75DC86,900),
        "klein" to Accent("klein","klein blue",0xFF0008FF,1200)
    )

    val perfectNames = setOf(
        "Singapore","Indonesia","Monaco","Poland","Romania","Chad","Ireland","Côte d’Ivoire","Australia","New Zealand",
        "Iceland","Faroe Islands","Qatar","Bahrain","Palau","Nauru","Marshall Islands","Micronesia","Tuvalu","Fiji",
        "Kiribati","Vanuatu","Solomon Islands","Aruba","Curaçao"
    )
}

fun flagEmoji(code: String): String {
    val upper = code.uppercase()
    if (upper.length != 2) return "🏳️"
    return upper.map { c -> String(Character.toChars(0x1F1E6 + (c.code - 'A'.code))) }.joinToString("")
}
