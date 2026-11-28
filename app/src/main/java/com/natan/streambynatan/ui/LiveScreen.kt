package com.natan.streambynatan.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.natan.streambynatan.chat.BroadcastSettings
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.features.PreLiveCheck
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveMode
import com.natan.streambynatan.live.LiveService
import com.natan.streambynatan.ui.theme.Neon
import kotlinx.coroutines.launch

@Composable
fun PollDialog(onStart: (String, List<String>) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var opts by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Polling lewat komentar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Pertanyaan", q) { q = it }
                ListEditor("Pilihan (A, B, C…)", opts) { opts = it }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(q, opts); onClose() }) { Text("Mulai") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } },
    )
}
