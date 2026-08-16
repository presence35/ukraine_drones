package ua.ukrainedrones

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ConnectionStatus(
    neptunDown: Boolean,
    backupActive: Boolean,
    backupUp: Boolean,
    backupSeen: Boolean,
    backupOfflineElapsedSec: Long?,
    offlineElapsedSec: Long?,
    forceOffline: Boolean,
    onForceOfflineChange: (Boolean) -> Unit,
    s: Strings.StringSet,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        neptunDown -> Color(0xFFE57373) // red — NEPTUN offline (real or simulated)
        backupActive -> Color(0xFFF9A825) // amber — NEPTUN alive but on the backup source
        else -> Color(0xFF4CAF50)
    }
    val label = when {
        neptunDown -> offlineElapsedSec?.let { String.format(s.offlineUiFormat, String.format(s.offlineDurMinFormat, it / 60)) }
            ?: s.connOffline
        backupActive -> s.connBackup
        else -> s.connOnline
    }
    var showInfo by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable { showInfo = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
    if (showInfo) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(s.backButton) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(s.connStatusTitle)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceStatusRow(
                        color = if (neptunDown) Color(0xFFE57373) else Color(0xFF4CAF50),
                        name = s.connNeptunLabel,
                        active = !backupActive || !backupSeen,
                        activeLabel = s.connActiveLabel
                    )
                    SourceStatusRow(
                        color = when {
                            backupUp -> Color(0xFF4CAF50)
                            backupSeen -> Color(0xFFF9A825)
                            else -> Color(0xFFE57373)
                        },
                        name = s.connBackupLabel,
                        active = backupActive && backupSeen,
                        activeLabel = s.connActiveLabel
                    )
                    Text(
                        s.connBackupNoMapDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            s.connForceOfflineTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = forceOffline,
                            onCheckedChange = onForceOfflineChange
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.connUpLine, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE57373))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.connDownLine, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://neptun.in.ua/")
                                )
                            )
                        }
                    ) {
                        Image(
                            painter = painterResource(R.drawable.neptun),
                            contentDescription = s.attributionText,
                            modifier = Modifier.height(40.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "neptun.in.ua",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        )
    }
}

@Composable
internal fun SourceStatusRow(color: Color, name: String, active: Boolean, activeLabel: String) {
    val accent = Color(0xFFF9A825)
    val nameColor = if (active) accent else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (active) 1f else 0.65f)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = nameColor)
        if (active) {
            Spacer(Modifier.width(8.dp))
            Text(
                activeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}