package com.flagser.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("flagser_native", Context.MODE_PRIVATE)

    var points by mutableStateOf(p.getInt("points", 0)); private set
    var unlockedModes by mutableStateOf(p.getStringSet("unlockedModes", emptySet())!!.toSet()); private set
    var unlockedHelpers by mutableStateOf(p.getStringSet("unlockedHelpers", emptySet())!!.toSet()); private set
    var enabledHelpers by mutableStateOf(p.getStringSet("enabledHelpers", setOf("shield","restore","double","skip","peek"))!!.toSet()); private set
    var unlockedColors by mutableStateOf(p.getStringSet("unlockedColors", emptySet())!!.toSet()); private set
    var unlockedPhoneModes by mutableStateOf(p.getStringSet("unlockedPhoneModes", emptySet())!!.toSet()); private set
    var unlockedStudyModes by mutableStateOf(p.getStringSet("unlockedStudyModes", emptySet())!!.toSet()); private set
    var selectedFamily by mutableStateOf(p.getString("selectedFamily", "flags") ?: "flags"); private set
    var selectedDirection by mutableStateOf(p.getString("selectedDirection", "classic") ?: "classic"); private set
    var selectedDifficulty by mutableStateOf(p.getString("selectedDifficulty", "standard") ?: "standard"); private set
    var selectedPhoneMode by mutableStateOf(p.getString("selectedPhoneMode", "international") ?: "international"); private set
    var accent by mutableStateOf(p.getString("accent", "white") ?: "white"); private set
    var muted by mutableStateOf(p.getBoolean("muted", false)); private set
    var darkModeUnlocked by mutableStateOf(p.getBoolean("darkModeUnlocked", false)); private set
    var darkMode by mutableStateOf(p.getBoolean("darkMode", false)); private set
    var showGameTitle by mutableStateOf(p.getBoolean("showGameTitle", true)); private set
    var newSliderUnlocked by mutableStateOf(p.getBoolean("newSliderUnlocked", false)); private set
    var switchStyle by mutableStateOf(p.getString("switchStyle", "classic") ?: "classic"); private set
    var customDomesticCodes by mutableStateOf(loadCustom()); private set

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) = p.edit().apply(block).apply()

    private fun loadCustom(): List<PhoneCode> = try {
        val a = JSONArray(p.getString("customDomesticCodes", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            PhoneCode(o.getString("code"), o.getString("country"), true, o.optString("id"))
        }
    } catch (_: Exception) { emptyList() }

    private fun saveCustom() {
        val a = JSONArray()
        customDomesticCodes.forEach {
            a.put(JSONObject().put("id", it.id).put("code", it.code).put("country", it.country))
        }
        edit { putString("customDomesticCodes", a.toString()) }
    }

    fun addPoints(v: Int) { points += v; edit { putInt("points", points) } }
    fun setFamily(v: String) { selectedFamily = v; edit { putString("selectedFamily", v) } }
    fun setDirection(v: String) { selectedDirection = v; edit { putString("selectedDirection", v) } }
    fun setDifficulty(v: String) { selectedDifficulty = v; edit { putString("selectedDifficulty", v) } }
    fun setPhoneMode(v: String) { selectedPhoneMode = v; edit { putString("selectedPhoneMode", v) } }
    fun setAccent(v: String) { accent = v; edit { putString("accent", v) } }
    fun setMuted(v: Boolean) { muted = v; edit { putBoolean("muted", v) } }
    fun setDarkMode(v: Boolean) { darkMode = v; edit { putBoolean("darkMode", v) } }
    fun setShowGameTitle(v: Boolean) { showGameTitle = v; edit { putBoolean("showGameTitle", v) } }
    fun setSwitchStyle(v: String) { switchStyle = v; edit { putString("switchStyle", v) } }

    fun phoneGatewayOwned() = "phonecodes" in unlockedModes
    fun modeOwned(key: String) = key in unlockedModes
    fun phoneModeOwned(key: String) = key in unlockedPhoneModes
    fun studyOwned(key: String) = key in unlockedStudyModes
    fun helperOwned(key: String) = FlagserData.helpers[key]?.free == true || key in unlockedHelpers
    fun colorOwned(key: String) = key == "white" || key in unlockedColors

    fun buyMode(key: String, price: Int): Boolean {
        if (points < price || key in unlockedModes) return false
        points -= price; unlockedModes = unlockedModes + key
        edit { putInt("points", points); putStringSet("unlockedModes", unlockedModes) }
        return true
    }
    fun buyPhone(key: String, price: Int): Boolean {
        if (points < price || key in unlockedPhoneModes) return false
        points -= price; unlockedPhoneModes = unlockedPhoneModes + key
        edit { putInt("points", points); putStringSet("unlockedPhoneModes", unlockedPhoneModes) }
        return true
    }
    fun buyHelper(key: String, price: Int): Boolean {
        if (points < price || helperOwned(key)) return false
        points -= price; unlockedHelpers = unlockedHelpers + key; enabledHelpers = enabledHelpers + key
        edit { putInt("points", points); putStringSet("unlockedHelpers", unlockedHelpers); putStringSet("enabledHelpers", enabledHelpers) }
        return true
    }
    fun toggleHelper(key: String): Boolean {
        if (!helperOwned(key)) return false
        enabledHelpers = if (key in enabledHelpers) {
            if (enabledHelpers.size <= 3) return false
            enabledHelpers - key
        } else enabledHelpers + key
        edit { putStringSet("enabledHelpers", enabledHelpers) }
        return true
    }
    fun buyStudy(key: String, price: Int): Boolean {
        if (points < price || key in unlockedStudyModes) return false
        points -= price; unlockedStudyModes = unlockedStudyModes + key
        edit { putInt("points", points); putStringSet("unlockedStudyModes", unlockedStudyModes) }
        return true
    }
    fun buyColor(key: String, price: Int): Boolean {
        if (points < price || colorOwned(key)) return false
        points -= price; unlockedColors = unlockedColors + key
        edit { putInt("points", points); putStringSet("unlockedColors", unlockedColors) }
        return true
    }
    fun buyDarkMode(): Boolean {
        if (darkModeUnlocked || points < 1200) return false
        points -= 1200; darkModeUnlocked = true
        edit { putInt("points", points); putBoolean("darkModeUnlocked", true) }
        return true
    }
    fun buyNewSlider(): Boolean {
        if (newSliderUnlocked || points < 1400) return false
        points -= 1400; newSliderUnlocked = true
        edit { putInt("points", points); putBoolean("newSliderUnlocked", true) }
        return true
    }
    fun addCustomDomestic(code: String, country: String) {
        val c = code.trim().replace("...", "…")
        val n = country.trim().replace(Regex("\\s+"), " ")
        if (c.isBlank() || n.isBlank()) return
        customDomesticCodes = customDomesticCodes + PhoneCode(c, n, true, "dom_${System.currentTimeMillis()}")
        saveCustom()
    }
    fun removeCustomDomestic(id: String?) {
        customDomesticCodes = customDomesticCodes.filterNot { it.id == id }
        saveCustom()
    }
    fun resetProgress() {
        points = 0
        unlockedModes = emptySet(); unlockedHelpers = emptySet(); enabledHelpers = setOf("shield","restore","double","skip","peek")
        unlockedColors = emptySet(); unlockedPhoneModes = emptySet(); unlockedStudyModes = emptySet()
        selectedFamily = "flags"; selectedDirection = "classic"; selectedDifficulty = "standard"; selectedPhoneMode = "international"
        accent = "white"; muted = false; darkModeUnlocked = false; darkMode = false; showGameTitle = true; newSliderUnlocked = false; switchStyle = "classic"
        edit {
            putInt("points", points)
            putStringSet("unlockedModes", unlockedModes); putStringSet("unlockedHelpers", unlockedHelpers); putStringSet("enabledHelpers", enabledHelpers)
            putStringSet("unlockedColors", unlockedColors); putStringSet("unlockedPhoneModes", unlockedPhoneModes); putStringSet("unlockedStudyModes", unlockedStudyModes)
            putString("selectedFamily", selectedFamily); putString("selectedDirection", selectedDirection); putString("selectedDifficulty", selectedDifficulty)
            putString("selectedPhoneMode", selectedPhoneMode); putString("accent", accent); putBoolean("muted", muted)
            putBoolean("darkModeUnlocked", darkModeUnlocked); putBoolean("darkMode", darkMode); putBoolean("showGameTitle", showGameTitle)
            putBoolean("newSliderUnlocked", newSliderUnlocked); putString("switchStyle", switchStyle)
        }
    }
}
