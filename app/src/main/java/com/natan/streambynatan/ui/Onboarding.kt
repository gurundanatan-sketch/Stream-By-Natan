package com.natan.streambynatan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.ui.theme.Neon

/** Tutorial singkat saat aplikasi pertama kali dibuka (bisa dilewati, bisa dibuka lagi di Tentang aplikasi). */
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    val pages = Guides.tutorial
    val g = pages[page]
    ScreenColumn {
        Spacer(Modifier.height(16.dp))
        Text("STREAM BY NATAN · TUTORIAL ${page + 1} DARI ${pages.size}", style = MaterialTheme.typography.labelMedium, color = Neon.Cyan)
        ScreenTitle(g.title)
        g.steps.forEach { (title, body) ->
            GlassCard {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) GhostButton("Sebelumnya", Modifier.weight(1f), Icons.Filled.ArrowBack) { page-- }
            if (page < pages.size - 1) NeonButton("Berikutnya", Modifier.weight(1f), icon = Icons.Filled.ArrowForward) { page++ }
            else NeonButton("Mulai", Modifier.weight(1f), icon = Icons.Filled.Check) { onDone() }
        }
        GhostButton("Lewati tutorial") { onDone() }
        Spacer(Modifier.height(24.dp))
    }
}

/** Pilihan mode pembaca layar / tunanetra (sekali, bisa diubah lagi nanti). */
@Composable
fun A11yChoiceScreen(onDone: () -> Unit) {
    ScreenColumn {
        Spacer(Modifier.height(24.dp))
        Text("STREAM BY NATAN", style = MaterialTheme.typography.labelMedium, color = Neon.Cyan)
        ScreenTitle("Mode pembaca layar", "Pilih sesuai kebutuhanmu. Bisa diubah kapan saja di menu Akun, Tampilan.")
        GlassCard {
            Text("Mode pembaca layar (tunanetra)", style = MaterialTheme.typography.titleMedium)
            Text("Semua bantuan suara aktif: catatan live dibisikkan, peringatan dan status diucapkan. Hanya kamu yang mendengar.", color = Neon.Muted)
            NeonButton("Nyalakan mode pembaca layar", icon = Icons.Filled.RecordVoiceOver) {
                Graph.settings.update { it.copy(a11y = it.a11y.copy(voiceAids = true, chosen = true), features = it.features.copy(notesWhisper = true)) }
                onDone()
            }
        }
        GlassCard {
            Text("Tampilan biasa", style = MaterialTheme.typography.titleMedium)
            Text("Bantuan suara dimatikan. Catatan dan pemberitahuan tampil sebagai tulisan di layar. Pembacaan komentar tetap bisa dinyalakan terpisah.", color = Neon.Muted)
            GhostButton("Pakai tampilan biasa", icon = Icons.Filled.Visibility) {
                Graph.settings.update { it.copy(a11y = it.a11y.copy(voiceAids = false, chosen = true)) }
                onDone()
            }
        }
    }
}
