package ua.ukrainedrones

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The major-city chip grid shared by the first-launch wizard and Settings' pin-city row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CityChipGrid(
    lang: AppLanguage,
    selected: City?,
    enabled: Boolean = true,
    onChange: (City?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cities = remember(lang) {
        Cities.ALL.filter { it.major }
            .sortedBy { if (lang == AppLanguage.UA) it.nameUa else it.nameEn }
    }
    val label: (City) -> String = { c -> if (lang == AppLanguage.UA) c.nameUa else c.nameEn }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cities.forEach { city ->
            val on = selected == city
            Text(
                label(city),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (on && enabled) MaterialTheme.colorScheme.onSurface
                else if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.5.dp,
                        color = when {
                            !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            on -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (on && enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable(enabled = enabled) { onChange(city) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
