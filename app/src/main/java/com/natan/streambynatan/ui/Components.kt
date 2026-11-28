package com.natan.streambynatan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.natan.streambynatan.ui.theme.Neon

/** Kartu kaca dengan tepi neon tipis. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, glow: Brush = Neon.brand, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Neon.GlassStrong, Neon.Glass)))
            .border(BorderStroke(1.dp, glow), RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Neon.Cyan,
        modifier = modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
fun ScreenTitle(title: String, subtitle: String = "") {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = Neon.Text, modifier = Modifier.semantics { heading() })
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Neon.Muted)
    }
}

/** Tombol utama bergradasi, besar dan mudah ditekan. */
@Composable
fun NeonButton(text: String, modifier: Modifier = Modifier, brush: Brush = Neon.brandH, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 60.dp).background(if (enabled) brush else Brush.horizontalGradient(listOf(Neon.Line, Neon.Line))), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let { Icon(it, null, tint = Neon.Void); Spacer(Modifier.width(10.dp)) }
                Text(text, style = MaterialTheme.typography.labelLarge, color = Neon.Void)
            }
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = 54.dp),
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Neon.Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Neon.Glass, contentColor = Neon.Text),
    ) {
        icon?.let { Icon(it, null, tint = Neon.Cyan, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ubin aksi besar (ikon + label) untuk panel live. */
@Composable
fun ActionTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) Brush.linearGradient(listOf(Neon.Violet.copy(alpha = .55f), Neon.Pink.copy(alpha = .45f))) else Brush.linearGradient(listOf(Neon.Glass, Neon.Glass)))
            .border(1.dp, if (active) Neon.Pink else Neon.Line, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .heightIn(min = 88.dp)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (active) Color.White else Neon.Cyan, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Neon.Text, maxLines = 2)
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Neon.Cyan) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Neon.Glass).border(1.dp, Neon.Line, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Neon.Muted)
    }
}

@Composable
fun LiveDot(on: Boolean) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(if (on) Neon.Pink else Neon.Muted))
}

@Composable
fun SwitchRow(label: String, checked: Boolean, desc: String = "", onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .semantics(mergeDescendants = true) { stateDescription = if (checked) "menyala" else "mati" }
            .heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Neon.Text)
            if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodyMedium, color = Neon.Muted)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(
            checkedThumbColor = Neon.Void, checkedTrackColor = Neon.Cyan, uncheckedTrackColor = Neon.Glass, uncheckedBorderColor = Neon.Line))
    }
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, secret: Boolean = false, singleLine: Boolean = true,
          number: Boolean = false, minLines: Int = 1, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val trailing: (@Composable () -> Unit)? = if (secret) {
        { androidx.compose.material3.TextButton(onClick = { show = !show }) { Text(if (show) "Sembunyikan" else "Lihat") } }
    } else null
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when { number -> KeyboardType.Number; secret -> KeyboardType.Password; else -> KeyboardType.Text }),
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Neon.Cyan, unfocusedBorderColor = Neon.Line, focusedLabelColor = Neon.Cyan),
    )
}

@Composable
fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Field(label, text, number = true) { t -> text = t.filter(Char::isDigit).take(7); text.toIntOrNull()?.let(onChange) }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String = { String.format("%.2f", it) }, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("$label: ${format(value)}", style = MaterialTheme.typography.bodyLarge, color = Neon.Text)
        Slider(value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.semantics { contentDescription = label; stateDescription = format(value) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Neon.Text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (id, name) ->
                FilterChip(
                    selected = id == selected, onClick = { onSelect(id) }, label = { Text(name) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = if (id == selected) "dipilih" else "" },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon.Violet, selectedLabelColor = Color.White),
                )
            }
        }
    }
}

/** Editor daftar: satu item per baris. */
@Composable
fun ListEditor(label: String, items: List<String>, hint: String = "Satu per baris", onChange: (List<String>) -> Unit) {
    var text by remember(items) { mutableStateOf(items.joinToString("\n")) }
    Field("$label ($hint)", text, singleLine = false, minLines = 4) { t -> text = t; onChange(t.lines().map { it.trim() }.filter { it.isNotEmpty() }) }
}

/** Editor pasangan "kunci = nilai", satu per baris. */
@Composable
fun MapEditor(label: String, map: Map<String, String>, onChange: (Map<String, String>) -> Unit) {
    var text by remember(map) { mutableStateOf(map.entries.joinToString("\n") { "${it.key} = ${it.value}" }) }
    Field("$label (format: singkatan = arti)", text, singleLine = false, minLines = 6) { t ->
        text = t
        onChange(t.lines().mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
            .filter { it.first.isNotEmpty() }.toMap(LinkedHashMap()))
    }
}

/** Pilihan warna sederhana (preset). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRow(label: String, value: Int, onChange: (Int) -> Unit) {
    val presets = listOf(
        0xFF0B1022 to "biru malam", 0xFF2A1250 to "ungu tua", 0xFF000000 to "hitam", 0xFFFFFFFF to "putih",
        0xFF22E5FF to "sian", 0xFF8B5CFF to "ungu", 0xFFFF3DA8 to "merah muda", 0xFFFFC86B to "emas",
        0xFF7CFFB2 to "hijau mint", 0xFF3B0710 to "merah marun", 0xFF0A2A1C to "hijau tua", 0xFF111111 to "abu gelap",
    )
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Neon.Text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (c, name) ->
                val sel = c.toInt() == value
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(c.toInt()))
                        .border(if (sel) 3.dp else 1.dp, if (sel) Neon.Cyan else Neon.Line, CircleShape)
                        .clickable(role = Role.RadioButton, onClickLabel = name) { onChange(c.toInt()) }
                        .semantics { contentDescription = name + if (sel) ", dipilih" else "" },
                )
            }
        }
    }
}

@Composable
fun NavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Neon.Glass)
            .border(1.dp, Neon.Line, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "Buka $title", onClick = onClick)
            .heightIn(min = 68.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Neon.brand), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Neon.Void)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Neon.Text)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Neon.Muted, maxLines = 2)
        }
    }
}

/** Tombol pilihan kecil (filter, kualitas, posisi). Pembaca layar membaca "dipilih". */
@Composable
fun FilterChipButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Neon.Violet else Neon.Glass)
            .border(1.dp, if (selected) Neon.Pink else Neon.Line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { stateDescription = if (selected) "dipilih" else "" }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(label, style = MaterialTheme.typography.bodyMedium, color = Neon.Text) }
}

/** Ikon bulat di pojok atas (akun, kotak saran) dengan angka pemberitahuan opsional. */
@Composable
fun TopIconButton(icon: ImageVector, description: String, badge: Int = 0, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Neon.Glass)
            .border(1.dp, Neon.Line, CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = if (badge > 0) "$description, $badge baru" else description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Neon.Cyan, modifier = Modifier.size(26.dp))
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Neon.Pink),
            contentAlignment = Alignment.Center,
        ) { Text(if (badge > 9) "9+" else "$badge", style = MaterialTheme.typography.labelMedium, color = Color.White) }
    }
}
