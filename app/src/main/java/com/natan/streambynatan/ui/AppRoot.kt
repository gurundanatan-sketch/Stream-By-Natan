package com.natan.streambynatan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.ui.theme.AuroraBackground
import com.natan.streambynatan.ui.theme.Neon

@Composable
fun AppRoot(activity: MainActivity) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var accountPage by rememberSaveable { mutableStateOf<String?>(null) }
    val appSettings by Graph.settings.state.collectAsState()
    var permsDone by rememberSaveable { mutableStateOf(activity.basicPermissionsGranted()) }
    var loggedIn by rememberSaveable { mutableStateOf(Graph.firebase.uid != null) }
    val snack = remember { SnackbarHostState() }
    val msg by activity.message
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); activity.message.value = null } }

    // Tamu dari link undangan: langsung ke layar tamu (gratis, tanpa akun)
    activity.guestInvite.value?.let { inv ->
        AuroraBackground { Box(Modifier.statusBarsPadding()) { GuestJoinScreen(activity, inv) { activity.guestInvite.value = null } } }
        return
    }

    // 0) tutorial singkat & pilihan mode pembaca layar (hanya saat pertama kali)
    if (!appSettings.onboardingDone) {
        AuroraBackground { Box(Modifier.statusBarsPadding()) { TutorialScreen { Graph.settings.update { it.copy(onboardingDone = true) } } } }
        return
    }
    if (!appSettings.a11y.chosen) {
        AuroraBackground { Box(Modifier.statusBarsPadding()) { A11yChoiceScreen { } } }
        return
    }

    // 1) izin dulu, 2) login email/Google, 3) baru tampilan utama
    if (!permsDone) { AuroraBackground { androidx.compose.foundation.layout.Box(Modifier.statusBarsPadding()) { PermissionScreen(activity) { permsDone = true } } }; return }
    if (!loggedIn) { AuroraBackground { androidx.compose.foundation.layout.Box(Modifier.statusBarsPadding()) { LoginScreen(activity) { loggedIn = true; Graph.social.onSignedIn() } } }; return }

    BackHandler(enabled = settingsPage != null || accountPage != null) {
        if (accountPage != null) accountPage = if (accountPage == "menu") null else "menu" else settingsPage = null
    }

    // Menu akun (ikon kanan atas halaman utama)
    accountPage?.let { page ->
        AuroraBackground { Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AccountScreen(activity, page, go = { accountPage = it }, onClose = { accountPage = null }, onSignedOut = { accountPage = null; loggedIn = false })
        } }
        return
    }

    // Undangan moderator dari link
    val profileLink by activity.profileLink
    LaunchedEffect(profileLink, loggedIn) { if (profileLink != null && loggedIn) accountPage = "following" }
    activity.modInvite.value?.let { (hostUid, code) ->
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { activity.modInvite.value = null },
            title = { Text("Undangan moderator") },
            text = { Text("Kamu diundang menjadi moderator live. Moderator bisa mengelola tamu, membisukan penonton, dan mengatur antrean pertanyaan. Terima?") },
            confirmButton = { TextButton(onClick = {
                activity.modInvite.value = null
                scope.launch { activity.message.value = Graph.social.acceptModeratorInvite(hostUid, code) ?: "Kamu sekarang moderator. Buka Akun, Panel moderator saat host live." }
            }) { Text("Terima") } },
            dismissButton = { TextButton(onClick = { activity.modInvite.value = null }) { Text("Tolak") } },
        )
    }

    Scaffold(
        containerColor = Neon.Void,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            val liveNow by com.natan.streambynatan.live.LiveBus.stats.collectAsState()
            if (!(liveNow.isLive && tab == 0)) NavigationBar(containerColor = Neon.Deep) {
                val str by com.natan.streambynatan.features.I18n.strings.collectAsState()
                listOf(
                    Triple(str["nav_live"] ?: "Live", Icons.Filled.Sensors, 0),
                    Triple(str["nav_rec"] ?: "Rekaman", Icons.Filled.VideoLibrary, 1),
                    Triple(str["nav_progress"] ?: "Progres", Icons.Filled.Insights, 2),
                    Triple(str["nav_settings"] ?: "Pengaturan", Icons.Filled.Settings, 3),
                ).forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i; if (i != 3) settingsPage = null },
                        icon = { Icon(icon, null) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Neon.Void, indicatorColor = Neon.Cyan,
                            selectedTextColor = Neon.Cyan, unselectedIconColor = Neon.Muted, unselectedTextColor = Neon.Muted),
                    )
                }
            }
        },
    ) { pad ->
        AuroraBackground(Modifier.padding(pad)) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (tab) {
                    0 -> LiveScreen(activity, openAccount = { accountPage = it }, openSettings = { p -> tab = 3; settingsPage = p })
                    1 -> LibraryScreen()
                    2 -> ProgressScreen()
                    else -> if (settingsPage == null) SettingsHome { settingsPage = it } else SettingsPage(activity, settingsPage!!) { settingsPage = null }
                }
            }
        }
    }

    // Deteksi key TikTok di clipboard: tanya Ya/Tidak
    activity.clipboardKey.value?.let { key ->
        AlertDialog(
            onDismissRequest = { activity.clipboardKey.value = null },
            title = { Text("Key TikTok terdeteksi") },
            text = { Text("Ada teks di clipboard yang mirip stream key TikTok. Pakai sebagai key TikTok untuk live ini?") },
            confirmButton = { TextButton(onClick = {
                Graph.secure.put(SecureStore.TIKTOK_KEY, key); activity.clipboardKey.value = null
                activity.message.value = "Key TikTok disimpan."
            }) { Text("Ya") } },
            dismissButton = { TextButton(onClick = { activity.clipboardKey.value = null }) { Text("Tidak") } },
        )
    }

    activity.updateAvailable.value?.let { rel ->
        AlertDialog(
            onDismissRequest = { activity.updateAvailable.value = null },
            title = { Text("Ada versi baru") },
            text = { Text("${rel.name} sudah tersedia. Mau dipasang?") },
            confirmButton = { TextButton(onClick = { activity.updateAvailable.value = null; activity.installUpdate(rel) }) { Text("Ya") } },
            dismissButton = { TextButton(onClick = { activity.updateAvailable.value = null }) { Text("Nanti") } },
        )
    }
}

@Composable
fun ScreenColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        content = content,
    )
}
