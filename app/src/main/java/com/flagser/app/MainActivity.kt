package com.flagser.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.ceil

enum class Screen { MENU, MODES, UNLOCKS, STUDY, STUDY_READER, SETTINGS, GAME, END }

data class Palette(
    val bg: Color,
    val paper: Color,
    val muted: Color,
    val line: Color,
    val surface: Color,
    val surface2: Color,
    val signal: Color,
    val inverse: Color
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FlagserData.load(this)
        val prefs = AppPrefs(this)
        val engine = GameEngine(prefs)
        setContent { FlagserApp(prefs, engine) }
    }
}

@Composable
fun FlagserApp(prefs: AppPrefs, engine: GameEngine) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.MENU) }
    var studyModule by remember { mutableStateOf<String?>(null) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 34) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    fun sound(kind: String = "click") {
        if (prefs.muted) return
        val t = when (kind) {
            "correct" -> ToneGenerator.TONE_PROP_ACK
            "wrong" -> ToneGenerator.TONE_PROP_NACK
            "unlock" -> ToneGenerator.TONE_PROP_BEEP2
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        tone.startTone(t, 65)
    }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val dark = prefs.darkModeUnlocked && prefs.darkMode
    val accentRaw = FlagserData.accents[prefs.accent]?.hex ?: 0xFFF2EEE6
    val signal = if (prefs.accent == "white" && !dark) Color(0xFF060708) else Color(accentRaw)
    val p = if (dark) Palette(
        bg = Color(0xFF060708), paper = Color(0xFFF2EEE6), muted = Color(0xFF77726A),
        line = Color(0x332F2E2B), surface = Color(0xFF090A0C), surface2 = Color(0xFF181A1D), signal = signal, inverse = Color(0xFF070809)
    ) else Palette(
        bg = Color(0xFFF2EEE6), paper = Color(0xFF060708), muted = Color(0xFF706A62),
        line = Color(0x2D060708), surface = Color(0xFFEEE9DF), surface2 = Color(0xFFDDD7CB), signal = signal, inverse = Color(0xFFF2EEE6)
    )

    LaunchedEffect(engine.result) {
        if (engine.result != null) screen = Screen.END
    }

    BackHandler(enabled = screen != Screen.MENU && screen != Screen.GAME) {
        when (screen) {
            Screen.STUDY_READER -> screen = Screen.STUDY
            Screen.END -> {
                engine.clearResult()
                screen = Screen.MENU
            }
            else -> screen = Screen.MENU
        }
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            when (screen) {
                Screen.MENU -> MenuScreen(p, prefs, sound = ::sound, onNavigate = { target ->
                    if (target == Screen.GAME) {
                        if (engine.start()) screen = Screen.GAME else toast("unlock that mode first")
                    } else screen = target
                })
                Screen.MODES -> ModesScreen(p, prefs, ::sound, toast = ::toast, onBack = { screen = Screen.MENU }, onPlay = {
                    if (engine.start()) screen = Screen.GAME else toast("unlock that mode first")
                })
                Screen.UNLOCKS -> UnlocksScreen(p, prefs, ::sound, ::toast, onBack = { screen = Screen.MENU })
                Screen.STUDY -> StudyHubScreen(p, prefs, ::sound, ::toast, onBack = { screen = Screen.MENU }, onOpen = {
                    studyModule = it; screen = Screen.STUDY_READER
                }, onUnlocks = { screen = Screen.UNLOCKS })
                Screen.STUDY_READER -> StudyReaderScreen(p, prefs, studyModule ?: "flagGuide", ::sound, onBack = { screen = Screen.STUDY })
                Screen.SETTINGS -> SettingsScreen(p, prefs, ::sound, ::toast, onBack = { screen = Screen.MENU })
                Screen.GAME -> GameScreen(p, prefs, engine, ::sound, onQuit = { engine.abandon(); screen = Screen.MENU })
                Screen.END -> EndScreen(p, prefs, engine.result, ::sound, onAgain = {
                    engine.clearResult(); if (engine.start()) screen = Screen.GAME else screen = Screen.MENU
                }, onMenu = { engine.clearResult(); screen = Screen.MENU })
            }
        }
    }
}

@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), content = content)
}

@Composable
fun TopBar(p: Palette, left: String? = null, right: String, onLeft: (() -> Unit)? = null, muted: Boolean? = null, onSound: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        if (left == null) Box(Modifier.size(7.dp).clip(CircleShape).background(p.signal)) else SmallTextButton(left, p, onLeft ?: {})
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(right.uppercase(), color = p.muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
            if (muted != null && onSound != null) SmallTextButton(if (muted) "sound ×" else "sound ◔", p, onSound)
        }
    }
}

@Composable
fun SmallTextButton(text: String, p: Palette, onClick: () -> Unit) {
    Text(text, color = p.muted, fontSize = 9.sp, letterSpacing = .8.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 8.dp, horizontal = 2.dp))
}

@Composable
fun BigTitle(text: String, p: Palette, size: Int = 72) {
    Text(text, color = p.paper, fontSize = size.sp, lineHeight = (size * .76).sp, letterSpacing = (-4).sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun PillButton(text: String, p: Palette, modifier: Modifier = Modifier, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (filled) p.paper else p.surface
    val fg = if (filled) p.inverse else if (enabled) p.paper else p.muted
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(999.dp)).background(bg).border(1.dp, if (filled) p.paper else p.line, RoundedCornerShape(999.dp)).clickable(enabled = enabled) { onClick() }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun MenuRow(label: String, aside: String? = null, p: Palette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(999.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(999.dp)).clickable { onClick() }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = p.paper, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!aside.isNullOrBlank()) Text(aside.uppercase(), color = p.muted, fontSize = 8.sp, letterSpacing = .8.sp)
            Text("→", color = p.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun MenuScreen(p: Palette, prefs: AppPrefs, sound: (String) -> Unit, onNavigate: (Screen) -> Unit) {
    Page {
        TopBar(p, right = "pts ${prefs.points.toString().padStart(4,'0')}", muted = prefs.muted, onSound = { prefs.updateMuted(!prefs.muted); sound("click") })
        Spacer(Modifier.weight(1f))
        BigTitle("flagser.", p, 86)
        Spacer(Modifier.height(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val mode = if (prefs.selectedFamily == "phone") "phone · ${prefs.selectedPhoneMode}" else "classic"
            MenuRow("play", mode, p) { sound("click"); onNavigate(Screen.GAME) }
            MenuRow("modes", null, p) { sound("click"); onNavigate(Screen.MODES) }
            MenuRow("unlocks", null, p) { sound("click"); onNavigate(Screen.UNLOCKS) }
            MenuRow("study", null, p) { sound("click"); onNavigate(Screen.STUDY) }
            MenuRow("settings", null, p) { sound("click"); onNavigate(Screen.SETTINGS) }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun SectionHeader(left: String, right: String, p: Palette) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left.uppercase(), color = p.muted, fontSize = 8.sp, letterSpacing = 1.1.sp)
        Text(right.uppercase(), color = p.muted, fontSize = 8.sp, letterSpacing = 1.1.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
fun OptionCard(name: String, desc: String, p: Palette, selected: Boolean, unlocked: Boolean, price: Int = 0, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 108.dp).clip(RoundedCornerShape(18.dp)).background(p.surface).border(1.dp, if (selected) p.signal else p.line, RoundedCornerShape(18.dp)).clickable { onClick() }.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = if (unlocked) p.paper else p.muted, fontSize = 17.sp)
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (selected) p.signal else p.surface2).border(1.dp, p.line, CircleShape))
        }
        Spacer(Modifier.height(8.dp))
        Text(desc, color = p.muted, fontSize = 9.sp, lineHeight = 13.sp)
        if (!unlocked && price > 0) {
            Spacer(Modifier.weight(1f))
            Text("${price.toString()} PTS", color = p.muted, fontSize = 8.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun ModesScreen(p: Palette, prefs: AppPrefs, sound: (String) -> Unit, toast: (String) -> Unit, onBack: () -> Unit, onPlay: () -> Unit) {
    Page {
        TopBar(p, "← menu", "pts ${prefs.points.toString().padStart(4,'0')}", onBack, prefs.muted) { prefs.updateMuted(!prefs.muted) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 22.dp, bottom = 28.dp)) {
            BigTitle("modes.", p)
            Text("Flags is the full ISO pool. Phone Codes appears here after the gateway is unlocked.", color = p.muted, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 18.dp))
            Spacer(Modifier.height(38.dp)); SectionHeader("game", "choose one", p); Spacer(Modifier.height(10.dp))
            OptionCard("flags", "full ISO pool · ${FlagserData.countries.size} flags", p, prefs.selectedFamily == "flags", true) { sound("click"); prefs.setFamily("flags") }
            if (prefs.phoneGatewayOwned()) {
                Spacer(Modifier.height(8.dp)); OptionCard("phone codes", "international · domestic · mixed", p, prefs.selectedFamily == "phone", true) { sound("click"); prefs.setFamily("phone") }
            }
            if (prefs.selectedFamily == "flags") {
                Spacer(Modifier.height(34.dp)); SectionHeader("direction", "choose one", p); Spacer(Modifier.height(10.dp))
                val dirs = listOf(
                    Triple("classic", "classic", "flag → country"), Triple("reverse","reverse","country → flag"),
                    Triple("capital","capital","flag → capital city"), Triple("capitalmixed","capital mixed","alternate country and capital answers")
                )
                dirs.forEach { (key, name, desc) ->
                    val unlocked = key == "classic" || prefs.modeOwned(key)
                    val price = FlagserData.modeUnlocks[key]?.price ?: 0
                    OptionCard(name, desc, p, prefs.selectedDirection == key, unlocked, price) {
                        if (unlocked) { sound("click"); prefs.setDirection(key) } else toast("unlock $name first")
                    }; Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(24.dp)); SectionHeader("difficulty", "choose one", p); Spacer(Modifier.height(10.dp))
                listOf(Triple("standard","standard","full pool · ${FlagserData.countries.size} flags · 3 lives"), Triple("medium","medium","ultradifficult flag pool · 3 lives"), Triple("hardcore","hardcore","ultradifficult flag pool · 1 life")).forEach { (key,name,desc) ->
                    val unlocked = key == "standard" || prefs.modeOwned(key)
                    OptionCard(name, desc, p, prefs.selectedDifficulty == key, unlocked, FlagserData.modeUnlocks[key]?.price ?: 0) {
                        if (unlocked) { sound("click"); prefs.setDifficulty(key) } else toast("unlock $name first")
                    }; Spacer(Modifier.height(8.dp))
                }
            } else {
                Spacer(Modifier.height(34.dp)); SectionHeader("phone codes", "choose one", p); Spacer(Modifier.height(10.dp))
                FlagserData.phoneUnlocks.values.forEach { x ->
                    val unlocked = prefs.phoneModeOwned(x.key)
                    OptionCard(x.name, x.desc, p, prefs.selectedPhoneMode == x.key, unlocked, x.price) {
                        if (unlocked) { sound("click"); prefs.setPhoneMode(x.key) } else toast("unlock ${x.name} first")
                    }; Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (prefs.selectedFamily == "phone") "phone codes · ${prefs.selectedPhoneMode} · 3 lives" else "${prefs.selectedDirection} · ${prefs.selectedDifficulty}", color = p.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                PillButton("play", p, Modifier.width(150.dp), filled = true) { sound("click"); onPlay() }
            }
        }
    }
}

@Composable
fun UnlocksScreen(p: Palette, prefs: AppPrefs, sound: (String) -> Unit, toast: (String) -> Unit, onBack: () -> Unit) {
    var tab by remember { mutableStateOf("modes") }
    val tabs = buildList { addAll(listOf("modes","helpers","layout","sliders","study")); if (prefs.phoneGatewayOwned()) add("phone") }
    Page {
        TopBar(p, "← menu", "pts ${prefs.points.toString().padStart(4,'0')}", onBack, prefs.muted) { prefs.updateMuted(!prefs.muted) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 22.dp, bottom = 28.dp)) {
            BigTitle("unlocks.", p)
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { t ->
                            PillButton(t, p, Modifier.weight(1f), filled = tab == t) { sound("click"); tab = t }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(34.dp))
            when (tab) {
                "modes" -> {
                    SectionHeader("modes", "permanent", p); FlagserData.modeUnlocks.values.forEach { item ->
                        StoreRow(item.icon, item.name, item.desc, p) {
                            val owned = prefs.modeOwned(item.key)
                            PillButton(if (owned) "owned" else "unlock · ${item.price}", p, Modifier.widthIn(min = 118.dp), filled = owned) {
                                if (owned) toast("already owned") else if (prefs.buyMode(item.key, item.price)) { sound("unlock"); toast("${item.name} unlocked") } else toast("not enough points")
                            }
                        }
                    }
                }
                "helpers" -> {
                    SectionHeader("helpers", "licenses", p); FlagserData.helpers.values.forEach { h ->
                        StoreRow(h.emoji, h.name, h.copy, p) {
                            val owned = prefs.helperOwned(h.key); val on = h.key in prefs.enabledHelpers
                            PillButton(if (!owned) "unlock · ${h.price}" else if (on) "on" else "off", p, Modifier.widthIn(min = 110.dp), filled = owned && on) {
                                if (!owned) { if (prefs.buyHelper(h.key,h.price)) { sound("unlock"); toast("${h.name} unlocked") } else toast("not enough points") }
                                else if (!prefs.toggleHelper(h.key)) toast("keep at least 3 Helpers on") else sound("click")
                            }
                        }
                    }
                }
                "layout" -> {
                    SectionHeader("dark mode", "theme", p)
                    StoreRow("◐", "dark mode", "white on black", p) {
                        if (!prefs.darkModeUnlocked) {
                            PillButton("unlock · 1200", p, Modifier.width(126.dp)) {
                                if (prefs.buyDarkMode()) { sound("unlock"); toast("dark mode unlocked") } else toast("not enough points")
                            }
                        } else if (prefs.switchStyle == "neo" && prefs.newSliderUnlocked) {
                            PremiumRockerSwitch(
                                checked = prefs.darkMode,
                                p = p,
                                onToggle = {
                                    prefs.updateDarkMode(!prefs.darkMode)
                                    sound("click")
                                }
                            )
                        } else {
                            ClassicSwitch(
                                checked = prefs.darkMode,
                                p = p,
                                onToggle = {
                                    prefs.updateDarkMode(!prefs.darkMode)
                                    sound("click")
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp)); SectionHeader("accent colours", "signal", p)
                    FlagserData.accents.values.forEach { a ->
                        StoreRow("●", if (a.key == "white" && !(prefs.darkModeUnlocked && prefs.darkMode)) "black" else a.name, if (prefs.accent == a.key) "currently selected" else "", p, iconColor = if (a.key == "white" && !(prefs.darkModeUnlocked && prefs.darkMode)) Color(0xFF060708) else Color(a.hex)) {
                            val owned = prefs.colorOwned(a.key); val selected = prefs.accent == a.key
                            PillButton(if (!owned) "unlock · ${a.price}" else if (selected) "selected" else "select", p, Modifier.widthIn(min = 110.dp), filled = selected) {
                                if (!owned) { if (prefs.buyColor(a.key,a.price)) { sound("unlock"); toast("${a.name} unlocked") } else toast("not enough points") }
                                else { prefs.updateAccent(a.key); sound("click") }
                            }
                        }
                    }
                }
                "sliders" -> {
                    SectionHeader("sliders", "dark mode control", p)
                    SliderStyleCard(
                        title = "classic",
                        copy = "default clean switch",
                        selected = prefs.switchStyle == "classic",
                        p = p,
                        preview = { ClassicSwitch(checked = true, p = p, onToggle = {}) },
                        action = {
                            prefs.updateSwitchStyle("classic")
                            sound("click")
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    SliderStyleCard(
                        title = "new slider",
                        copy = "mini premium 3D rocker · warm yellow LED",
                        selected = prefs.switchStyle == "neo",
                        locked = !prefs.newSliderUnlocked,
                        price = 1400,
                        p = p,
                        preview = { PremiumRockerSwitch(checked = true, p = p, enabled = prefs.newSliderUnlocked, onToggle = {}) },
                        action = {
                            if (!prefs.newSliderUnlocked) {
                                if (prefs.buyNewSlider()) {
                                    prefs.updateSwitchStyle("neo")
                                    sound("unlock")
                                    toast("new slider unlocked")
                                } else toast("not enough points")
                            } else {
                                prefs.updateSwitchStyle("neo")
                                sound("click")
                            }
                        }
                    )
                }
                "study" -> {
                    SectionHeader("flags", "study material", p)
                    listOf("flagGuide","flagCards").forEach { key -> StudyUnlockRow(key,p,prefs,sound,toast) }
                    Spacer(Modifier.height(28.dp)); SectionHeader("phone codes", "study material", p)
                    listOf("phoneGuide","phoneCards").forEach { key -> StudyUnlockRow(key,p,prefs,sound,toast,requirePhone = true) }
                }
                "phone" -> {
                    SectionHeader("phone codes", "submodes", p)
                    FlagserData.phoneUnlocks.values.forEach { item ->
                        StoreRow(item.icon,item.name,item.desc,p) {
                            val owned = prefs.phoneModeOwned(item.key)
                            PillButton(if (owned) "owned" else "unlock · ${item.price}",p,Modifier.widthIn(min = 118.dp),filled = owned) {
                                if (owned) toast("already owned") else if (prefs.buyPhone(item.key,item.price)) { sound("unlock"); toast("${item.name} unlocked") } else toast("not enough points")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudyUnlockRow(key: String, p: Palette, prefs: AppPrefs, sound: (String) -> Unit, toast: (String) -> Unit, requirePhone: Boolean = false) {
    val s = FlagserData.studyUnlocks.getValue(key)
    StoreRow(s.icon,s.name,s.desc,p) {
        val owned = prefs.studyOwned(key)
        val blocked = requirePhone && !prefs.phoneGatewayOwned()
        PillButton(if (blocked) "phone codes first" else if (owned) "owned" else "unlock · ${s.price}",p,Modifier.widthIn(min = 120.dp),filled = owned,enabled = !blocked) {
            if (owned) toast("already owned") else if (prefs.buyStudy(key,s.price)) { sound("unlock"); toast("${s.name} unlocked") } else toast("not enough points")
        }
    }
}

@Composable
fun ClassicSwitch(
    checked: Boolean,
    p: Palette,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    val track = if (checked) p.paper else p.surface2
    val knob = if (checked) p.inverse else p.paper
    Box(
        Modifier
            .width(62.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track)
            .border(1.dp, p.line, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled) { onToggle() }
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(26.dp)
                .clip(CircleShape)
                .background(knob)
                .border(1.dp, p.line, CircleShape)
        )
    }
}

@Composable
fun PremiumRockerSwitch(
    checked: Boolean,
    p: Palette,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    val warmLed = Color(0xFFFFD56A)
    val body = if (p.bg.luminance() < .5f) Color(0xFF151617) else Color(0xFFD8D2C8)
    val rocker = if (p.bg.luminance() < .5f) Color(0xFF232527) else Color(0xFFE9E4DA)
    Box(
        Modifier
            .width(76.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(body)
            .border(1.dp, p.line, RoundedCornerShape(13.dp))
            .clickable(enabled = enabled) { onToggle() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(rocker)
                .border(1.dp, if (checked) warmLed.copy(alpha = .34f) else p.line, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (checked) Color.Black.copy(alpha = .12f) else Color.Transparent)
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (!checked) Color.Black.copy(alpha = .12f) else Color.Transparent)
            )
        }
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 11.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(if (checked) warmLed else p.muted.copy(alpha = .28f))
                .border(1.dp, if (checked) warmLed.copy(alpha = .65f) else p.line, CircleShape)
        )
        Text(
            if (checked) "I" else "O",
            color = if (checked) warmLed.copy(alpha = .82f) else p.muted,
            fontSize = 8.sp,
            modifier = Modifier.align(if (checked) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 11.dp)
        )
    }
}

@Composable
fun SliderStyleCard(
    title: String,
    copy: String,
    selected: Boolean,
    p: Palette,
    preview: @Composable () -> Unit,
    action: () -> Unit,
    locked: Boolean = false,
    price: Int = 0
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.surface)
            .border(1.dp, if (selected) p.paper.copy(alpha = .55f) else p.line, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.paper, fontSize = 16.sp)
                Text(copy, color = p.muted, fontSize = 8.sp, lineHeight = 12.sp)
            }
            preview()
        }
        Spacer(Modifier.height(14.dp))
        PillButton(
            if (locked) "unlock · $price" else if (selected) "selected" else "select",
            p,
            Modifier.fillMaxWidth(),
            filled = selected && !locked,
            onClick = action
        )
    }
}

@Composable
fun StoreRow(icon: String, name: String, desc: String, p: Palette, iconColor: Color? = null, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).border(BorderStroke(0.dp, Color.Transparent)).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (iconColor != null) iconColor else p.surface).border(1.dp,p.line,CircleShape),contentAlignment = Alignment.Center) {
            if (iconColor == null) Text(icon,color=p.paper,fontSize=14.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(name,color=p.paper,fontSize=14.sp)
            if (desc.isNotBlank()) Text(desc,color=p.muted,fontSize=8.sp,lineHeight=12.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
        }
        control()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
fun StudyHubScreen(p: Palette, prefs: AppPrefs, sound: (String) -> Unit, toast: (String) -> Unit, onBack: () -> Unit, onOpen: (String) -> Unit, onUnlocks: () -> Unit) {
    Page {
        TopBar(p,"← menu","pts ${prefs.points.toString().padStart(4,'0')}",onBack,prefs.muted){ prefs.updateMuted(!prefs.muted) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=22.dp,bottom=28.dp)) {
            BigTitle("study.",p)
            Spacer(Modifier.height(36.dp)); SectionHeader("flags","learn before you play",p); Spacer(Modifier.height(10.dp))
            listOf("flagGuide","flagCards").forEach { key -> StudyModuleCard(key,p,prefs,sound,toast,onOpen,onUnlocks); Spacer(Modifier.height(9.dp)) }
            Spacer(Modifier.height(28.dp)); SectionHeader("phone codes","learn before you play",p); Spacer(Modifier.height(10.dp))
            listOf("phoneGuide","phoneCards").forEach { key -> StudyModuleCard(key,p,prefs,sound,toast,onOpen,onUnlocks); Spacer(Modifier.height(9.dp)) }
        }
    }
}

@Composable
fun StudyModuleCard(key:String,p:Palette,prefs:AppPrefs,sound:(String)->Unit,toast:(String)->Unit,onOpen:(String)->Unit,onUnlocks:()->Unit){
    val s=FlagserData.studyUnlocks.getValue(key); val phoneLocked=s.family=="phone"&&!prefs.phoneGatewayOwned(); val owned=prefs.studyOwned(key)
    Column(Modifier.fillMaxWidth().heightIn(min=128.dp).clip(RoundedCornerShape(18.dp)).background(p.surface).border(1.dp,p.line,RoundedCornerShape(18.dp)).clickable{
        sound("click"); when { phoneLocked->toast("unlock phone codes first"); !owned->onUnlocks(); else->onOpen(key) }
    }.padding(16.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){ Text(s.icon,color=p.paper,fontSize=18.sp); Text(if(phoneLocked)"PHONE CODES FIRST" else if(owned)"OPEN" else "${s.price} PTS",color=p.muted,fontSize=8.sp,letterSpacing=.8.sp) }
        Spacer(Modifier.height(20.dp)); Text(s.name,color=if(owned&&!phoneLocked)p.paper else p.muted,fontSize=17.sp); Spacer(Modifier.height(6.dp)); Text(s.desc,color=p.muted,fontSize=9.sp,lineHeight=13.sp)
    }
}

@Composable
fun StudyReaderScreen(p: Palette, prefs: AppPrefs, moduleKey: String, sound: (String) -> Unit, onBack: () -> Unit) {
    val module = FlagserData.studyUnlocks.getValue(moduleKey)
    var filter by remember(moduleKey) { mutableStateOf("all") }
    var page by remember(moduleKey) { mutableIntStateOf(0) }
    var flashIndex by remember(moduleKey) { mutableIntStateOf(0) }
    var flipped by remember(moduleKey) { mutableStateOf(false) }
    fun flagDeck():List<Country>{
        val seen=linkedSetOf<String>(); val out=mutableListOf<Country>()
        (FlagserData.easyFlagNames+FlagserData.familiarFlagNames+FlagserData.midFlagNames).forEach{ n->FlagserData.byName[n]?.let{if(seen.add(it.code))out+=it} }
        FlagserData.countries.filter{it.code !in seen}.sortedBy{it.name}.forEach{out+=it};
        return when(filter){"opening"->out.take(10);"familiar"->out.drop(10).take(10);"world"->out.drop(20).take(20);"obscure"->out.drop(40);else->out}
    }
    fun phoneDeck():List<PhoneCode>{ val all=FlagserData.internationalCodes+FlagserData.domesticCodes+prefs.customDomesticCodes; return when(filter){"international"->FlagserData.internationalCodes;"domestic"->FlagserData.domesticCodes+prefs.customDomesticCodes;else->all} }
    val isFlags=module.family=="flags"; val guide=module.type=="guide"; val fDeck=if(isFlags)flagDeck() else emptyList(); val pDeck=if(!isFlags)phoneDeck() else emptyList(); val count=if(isFlags)fDeck.size else pDeck.size
    val filters=if(isFlags)listOf("all" to "all flags","opening" to "opening","familiar" to "familiar","world" to "world","obscure" to "obscure") else listOf("all" to "all","international" to "international","domestic" to "domestic")
    Page {
        TopBar(p,"← study","pts ${prefs.points.toString().padStart(4,'0')}",onBack,prefs.muted){prefs.updateMuted(!prefs.muted)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=22.dp,bottom=28.dp)){
            BigTitle("${module.name}.",p,58)
            Text(if(isFlags) if(guide)"browse the flag and country together at your own pace" else "look at the flag, recall the country, then flip" else if(guide)"browse the code and country together at your own pace" else "look at the code, recall the country, then flip",color=p.muted,fontSize=10.sp,lineHeight=15.sp,modifier=Modifier.padding(top=18.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top=24.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){ filters.forEach{(k,label)->PillButton(label,p,Modifier.widthIn(min=86.dp),filled=filter==k){sound("click");filter=k;page=0;flashIndex=0;flipped=false} } }
            Spacer(Modifier.height(24.dp))
            if(guide){
                val per=12; val pages=maxOf(1,ceil(count/per.toDouble()).toInt()); page=page.coerceIn(0,pages-1); val start=page*per; val end=minOf(count,start+per)
                for(i in start until end step 2){ Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){ StudyGuideCard(p,isFlags,if(isFlags)fDeck[i] else pDeck[i],Modifier.weight(1f)); if(i+1<end)StudyGuideCard(p,isFlags,if(isFlags)fDeck[i+1] else pDeck[i+1],Modifier.weight(1f)) else Spacer(Modifier.weight(1f)) }; Spacer(Modifier.height(10.dp)) }
                Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){ SmallTextButton("← previous",p){if(page>0){sound("click");page--}}; Text("page ${page+1} / $pages · $count ${if(isFlags)"flags" else "codes"}",color=p.muted,fontSize=8.sp); SmallTextButton("next →",p){if(page<pages-1){sound("click");page++}} }
            }else{
                if(count==0)Text("nothing here",color=p.paper,fontSize=34.sp) else {
                    flashIndex=((flashIndex%count)+count)%count
                    val front=if(isFlags)flagEmoji(fDeck[flashIndex].code) else pDeck[flashIndex].code; val back=if(isFlags)fDeck[flashIndex].name else pDeck[flashIndex].country
                    Card(Modifier.fillMaxWidth().height(280.dp).clickable{sound("click");flipped=!flipped},shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,p.line),colors=CardDefaults.cardColors(containerColor=p.surface)){
                        Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center){ Text(if(flipped)back else front,color=p.paper,fontSize=if(flipped)44.sp else if(isFlags)100.sp else 72.sp,textAlign=TextAlign.Center,lineHeight=52.sp); Text(if(flipped)"ANSWER" else "WHICH COUNTRY?",color=p.muted,fontSize=8.sp,modifier=Modifier.align(Alignment.TopStart)); Text("tap to ${if(flipped)"flip back" else "reveal"}",color=p.muted,fontSize=8.sp,modifier=Modifier.align(Alignment.BottomCenter)) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){ PillButton("← previous",p,Modifier.weight(1f)){sound("click");flashIndex--;flipped=false}; PillButton("shuffle",p,Modifier.weight(1f)){sound("click");flashIndex=(0 until count).random();flipped=false}; PillButton("next →",p,Modifier.weight(1f)){sound("click");flashIndex++;flipped=false} }
                    Text("${flashIndex+1} / $count",color=p.muted,fontSize=8.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=10.dp))
                }
            }
        }
    }
}

@Composable
fun StudyGuideCard(p: Palette, isFlags: Boolean, item: Any, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(min=130.dp).clip(RoundedCornerShape(16.dp)).background(p.surface).border(1.dp,p.line,RoundedCornerShape(16.dp)).padding(12.dp),horizontalAlignment=Alignment.Start){
        if(isFlags){ val c=item as Country; Box(Modifier.fillMaxWidth().height(76.dp),contentAlignment=Alignment.Center){Text(flagEmoji(c.code),fontSize=56.sp)}; Text(c.name,color=p.paper,fontSize=11.sp,lineHeight=14.sp) }
        else { val c=item as PhoneCode; Spacer(Modifier.height(20.dp)); Text(c.code,color=p.paper,fontSize=26.sp); Spacer(Modifier.height(8.dp)); Text(c.country,color=p.muted,fontSize=10.sp,lineHeight=13.sp) }
    }
}

@Composable
fun SettingsScreen(p: Palette, prefs: AppPrefs, sound:(String)->Unit, toast:(String)->Unit, onBack:()->Unit){
    var code by remember{mutableStateOf("")}; var country by remember{mutableStateOf("")}; var confirmReset by remember{mutableStateOf(false)}
    Page{
        TopBar(p,"← menu","pts ${prefs.points.toString().padStart(4,'0')}",onBack,prefs.muted){prefs.updateMuted(!prefs.muted)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=22.dp,bottom=28.dp)){
            BigTitle("settings.",p)
            Spacer(Modifier.height(30.dp))
            SettingRow("sound","native click / correct / wrong tones",p){PillButton(if(prefs.muted)"off" else "on",p,Modifier.width(100.dp),filled=!prefs.muted){prefs.updateMuted(!prefs.muted);sound("click")}}
            SettingRow("game title","show “which country?”, “which capital?”, etc.",p){PillButton(if(prefs.showGameTitle)"on" else "off",p,Modifier.width(100.dp),filled=prefs.showGameTitle){prefs.updateShowGameTitle(!prefs.showGameTitle);sound("click")}}
            SettingRow("prototype credit","for testing expensive unlocks",p){PillButton("+ 25,000 pts",p,Modifier.width(132.dp)){prefs.addPoints(25000);sound("unlock");toast("+25,000 prototype pts")}}
            SettingRow("reset progress","points, unlocks, modes and colour",p){PillButton("reset",p,Modifier.width(100.dp)){confirmReset=true}}
            Spacer(Modifier.height(32.dp));SectionHeader("domestic phone codes","custom",p);Spacer(Modifier.height(12.dp))
            OutlinedTextField(code,{code=it},label={Text("code")},placeholder={Text("0813…")},singleLine=true,modifier=Modifier.fillMaxWidth(),colors=fieldColors(p))
            Spacer(Modifier.height(8.dp));OutlinedTextField(country,{country=it},label={Text("country")},placeholder={Text("Indonesia")},singleLine=true,modifier=Modifier.fillMaxWidth(),colors=fieldColors(p))
            Spacer(Modifier.height(8.dp));PillButton("add",p,Modifier.fillMaxWidth(),filled=true){ if(code.isBlank()||country.isBlank())toast("enter a code and country") else {prefs.addCustomDomestic(code,country);code="";country="";sound("click")} }
            Spacer(Modifier.height(12.dp))
            if(prefs.customDomesticCodes.isEmpty())Text("no custom domestic codes yet",color=p.muted,fontSize=9.sp) else prefs.customDomesticCodes.forEach{c-> Row(Modifier.fillMaxWidth().heightIn(min=54.dp),verticalAlignment=Alignment.CenterVertically){Text(c.code,color=p.paper,fontSize=15.sp,modifier=Modifier.width(86.dp));Text(c.country,color=p.paper,fontSize=12.sp,modifier=Modifier.weight(1f));PillButton("×",p,Modifier.width(48.dp)){prefs.removeCustomDomestic(c.id)} };Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))}
        }
    }
    if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Text("reset progress?")},text={Text("Custom domestic codes will be kept.")},confirmButton={TextButton(onClick={prefs.resetProgress();confirmReset=false;sound("click");toast("progress reset")}){Text("reset")}},dismissButton={TextButton(onClick={confirmReset=false}){Text("cancel")}})
}

@Composable
fun fieldColors(p:Palette)=OutlinedTextFieldDefaults.colors(focusedTextColor=p.paper,unfocusedTextColor=p.paper,focusedBorderColor=p.signal,unfocusedBorderColor=p.line,focusedLabelColor=p.muted,unfocusedLabelColor=p.muted,cursorColor=p.signal,focusedPlaceholderColor=p.muted,unfocusedPlaceholderColor=p.muted)

@Composable
fun SettingRow(name:String,note:String,p:Palette,control: @Composable () -> Unit){ Row(Modifier.fillMaxWidth().heightIn(min=70.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(name,color=p.paper,fontSize=14.sp);Text(note,color=p.muted,fontSize=8.sp,lineHeight=12.sp)};control()};Box(Modifier.fillMaxWidth().height(1.dp).background(p.line)) }

@Composable
fun GameScreen(p:Palette,prefs:AppPrefs,engine:GameEngine,sound:(String)->Unit,onQuit:()->Unit){
    val r=engine.run ?: return
    var quitConfirm by remember{mutableStateOf(false)}
    val q=r.question
    val helperChoices=remember(r.helperVisible,r.cleared){if(r.helperVisible)engine.helperChoices() else emptyList()}
    BackHandler {
        if (quitConfirm) quitConfirm = false
        else if (!r.checkpointVisible && !r.helperVisible) quitConfirm = true
    }
    Page{
        TopBar(p,"← flagser / quit","saved ${prefs.points.toString().padStart(4,'0')}",{quitConfirm=true},prefs.muted){prefs.updateMuted(!prefs.muted)}
        Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("pot ${r.pot.toString().padStart(3,'0')}",color=p.muted,fontSize=9.sp);Text("${(r.cleared+1).coerceAtMost(r.total).toString().padStart(3,'0')} / ${r.total}",color=p.muted,fontSize=9.sp);Text("${engine.rate()} pts",color=p.muted,fontSize=9.sp)}
        Spacer(Modifier.height(8.dp))
        if(prefs.showGameTitle && q!=null)Text(q.title,color=p.paper,fontSize=52.sp,lineHeight=46.sp,letterSpacing=(-3).sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.Center){repeat(r.maxLives){i->Box(Modifier.padding(horizontal=4.dp).size(18.dp).clip(CircleShape).background(if(i<r.lives)p.signal else p.surface2).border(1.dp,p.line,CircleShape))}}
        if(r.shield+r.anchor+r.skip+r.peek+r.perfectRemaining+(if(r.doubleDown)1 else 0)>0){ Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){if(r.shield>0)BuildChip("🛡️ ${r.shield}",p);if(r.anchor>0)BuildChip("⚓ ${r.anchor}",p);if(r.doubleDown)BuildChip("2️⃣ 5k",p);if(r.skip>0)BuildChip("⏭️ ${r.skip}",p,true){engine.useSkip()};if(r.peek>0)BuildChip("👁️ ${r.peek}",p,true){engine.usePeek()};if(r.perfectRemaining>0)BuildChip("✨ ${r.perfectRemaining}",p)} }
        Spacer(Modifier.weight(1f))
        if(q!=null){
            Text(q.hero,color=p.paper,fontSize=when(q.kind){QuestionKind.COUNTRY,QuestionKind.CAPITAL->106.sp;QuestionKind.FLAG->50.sp;QuestionKind.PHONE->72.sp},lineHeight=82.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp))
            Spacer(Modifier.height(28.dp))
            q.options.forEach{opt-> val disabled=opt.key in r.disabledKeys; AnswerPill(opt.label,p,q.kind==QuestionKind.FLAG,disabled){ if(!disabled){ val correct=opt.key==q.correctKey;sound(if(correct)"correct" else "wrong");engine.answer(opt.key)} };Spacer(Modifier.height(10.dp)) }
        }
        Text(r.status,color=p.muted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(top=4.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(if(r.family=="phone")"phone codes · ${r.phoneMode}" else "${if(r.direction=="capitalmixed")"capital mixed" else r.direction} · ${r.difficulty}",color=p.muted,fontSize=8.sp);Text("checkpoint at ${r.checkpointTarget}",color=p.muted,fontSize=8.sp)}
    }
    if(r.checkpointVisible)CheckpointDialog(p,r.pot,r.cleared,onCash={sound("click");engine.cashOut()},onKeep={sound("click");engine.keepGoing()})
    if(r.helperVisible)HelperDialog(p,helperChoices){key->sound("click");engine.chooseHelper(key)}
    if(quitConfirm) QuitRunDialog(
        p = p,
        onKeep = { quitConfirm = false },
        onQuit = { quitConfirm = false; onQuit() }
    )
}

@Composable
fun QuitRunDialog(p: Palette, onKeep: () -> Unit, onQuit: () -> Unit) {
    Dialog(onDismissRequest = onKeep) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(p.surface)
                .border(1.dp, p.line, RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Text(
                "quit?",
                color = p.paper,
                fontSize = 42.sp,
                lineHeight = 38.sp,
                letterSpacing = (-2).sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "everything unbanked will be lost.\nsaved points stay permanent.",
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(24.dp))
            PillButton("←  keep playing", p, Modifier.fillMaxWidth(), filled = true, onClick = onKeep)
            Spacer(Modifier.height(9.dp))
            PillButton("quit run  →", p, Modifier.fillMaxWidth(), onClick = onQuit)
        }
    }
}

@Composable
fun BuildChip(text:String,p:Palette,action:Boolean=false,onClick:()->Unit={}){ Box(Modifier.height(32.dp).clip(RoundedCornerShape(999.dp)).background(p.surface).border(1.dp,p.line,RoundedCornerShape(999.dp)).clickable(enabled=action){onClick()}.padding(horizontal=10.dp),contentAlignment=Alignment.Center){Text(text,color=p.paper,fontSize=10.sp)} }

@Composable
fun AnswerPill(label:String,p:Palette,flag:Boolean,disabled:Boolean,onClick:()->Unit){ Box(Modifier.fillMaxWidth().heightIn(min=58.dp).clip(RoundedCornerShape(999.dp)).background(p.surface).border(1.dp,p.line,RoundedCornerShape(999.dp)).clickable(enabled=!disabled){onClick()}.padding(horizontal=18.dp),contentAlignment=Alignment.Center){ Text(label,color=if(disabled)p.muted.copy(alpha=.35f) else p.paper,fontSize=if(flag)42.sp else 15.sp,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis) } }

@Composable
fun CheckpointDialog(p:Palette,pot:Int,cleared:Int,onCash:()->Unit,onKeep:()->Unit){ AlertDialog(onDismissRequest={},containerColor=p.surface,title={Text(cleared.toString(),color=p.paper,fontSize=74.sp,letterSpacing=(-4).sp)},text={Text("$pot pts are unbanked.\ntake them now, or keep the whole run at risk.",color=p.muted)},confirmButton={PillButton("take $pot pts",p,filled=true,onClick=onCash)},dismissButton={PillButton("keep going",p,onClick=onKeep)}) }

@Composable
fun HelperDialog(p:Palette,choices:List<HelperItem>,onChoose:(String)->Unit){ AlertDialog(onDismissRequest={},containerColor=p.surface,title={Text("choose one",color=p.paper,fontSize=40.sp,letterSpacing=(-2).sp)},text={Column{Text("a random Helper joins this run",color=p.muted,fontSize=10.sp);Spacer(Modifier.height(14.dp));choices.forEach{h->Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp,p.line,RoundedCornerShape(16.dp)).clickable{onChoose(h.key)}.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(h.emoji,fontSize=28.sp);Text(h.name,color=p.paper,fontSize=15.sp);Text(h.copy,color=p.muted,fontSize=9.sp,textAlign=TextAlign.Center)};Spacer(Modifier.height(8.dp))}}},confirmButton={}) }

@Composable
fun EndScreen(p:Palette,prefs:AppPrefs,result:ResultState?,sound:(String)->Unit,onAgain:()->Unit,onMenu:()->Unit){ val r=result ?: ResultState("result","0","");Page{TopBar(p,right="pts ${prefs.points.toString().padStart(4,'0')}",muted=prefs.muted,onSound={prefs.updateMuted(!prefs.muted)});Spacer(Modifier.weight(1f));Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){Text(r.kicker.uppercase(),color=p.muted,fontSize=9.sp,letterSpacing=1.2.sp);Text(r.score,color=p.paper,fontSize=96.sp,letterSpacing=(-6).sp,fontWeight=FontWeight.SemiBold);Text(r.copy,color=p.muted,fontSize=11.sp,lineHeight=17.sp,textAlign=TextAlign.Center);Spacer(Modifier.height(26.dp));PillButton("play again",p,Modifier.width(230.dp),filled=true){sound("click");onAgain()};Spacer(Modifier.height(9.dp));PillButton("menu",p,Modifier.width(230.dp)){sound("click");onMenu()}};Spacer(Modifier.weight(1f))} }
