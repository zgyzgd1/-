package com.example.timetable.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.notify.CourseReminderScheduler

@Composable
fun HeroSection(
    courseCount: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onEnableNotifications: () -> Unit,
    reminderMinutes: Int,
    reminderOptions: List<Int>,
    onReminderMinutesChange: (Int) -> Unit,
    backgroundMode: AppBackgroundMode,
    hasCustomBackground: Boolean,
    onSelectBackgroundImage: () -> Unit,
    onUseBundledBackground: () -> Unit,
    onUseGradientBackground: () -> Unit,
    onAdjustCustomBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    weekCardAlpha: Float,
    onWeekCardAlphaChange: (Float) -> Unit,
    weekCardHue: Float,
    onWeekCardHueChange: (Float) -> Unit,
) {
    var showReminderSheet by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.78f),
                        ),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "课表助手",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "当前共 $courseCount 门课程，支持 .ics 导入 / 导出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroActionChip(
                    icon = Icons.Default.Download,
                    label = "导入",
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                )
                HeroActionChip(
                    icon = Icons.Default.Upload,
                    label = "导出",
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                )
                HeroActionChip(
                    icon = Icons.Default.NotificationsActive,
                    label = "提醒 ${reminderMinutes}m",
                    onClick = { showReminderSheet = true },
                    modifier = Modifier.weight(1f),
                )
                HeroActionChip(
                    icon = Icons.Default.ColorLens,
                    label = "背景",
                    onClick = { showAppearanceDialog = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showReminderSheet) {
        ReminderPickerDialog(
            reminderMinutes = reminderMinutes,
            reminderOptions = reminderOptions,
            onDismiss = { showReminderSheet = false },
            onSelect = {
                onReminderMinutesChange(it)
                showReminderSheet = false
            },
            onEnableNotifications = {
                onEnableNotifications()
                showReminderSheet = false
            },
        )
    }

    if (showAppearanceDialog) {
        AppearanceDialog(
            onDismiss = { showAppearanceDialog = false },
            backgroundMode = backgroundMode,
            hasCustomBackground = hasCustomBackground,
            onSelectBackgroundImage = onSelectBackgroundImage,
            onUseBundledBackground = onUseBundledBackground,
            onUseGradientBackground = onUseGradientBackground,
            onAdjustCustomBackground = onAdjustCustomBackground,
            onClearCustomBackground = onClearCustomBackground,
            weekCardAlpha = weekCardAlpha,
            onWeekCardAlphaChange = onWeekCardAlphaChange,
            weekCardHue = weekCardHue,
            onWeekCardHueChange = onWeekCardHueChange,
        )
    }
}

@Composable
private fun HeroActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppearanceDialog(
    onDismiss: () -> Unit,
    backgroundMode: AppBackgroundMode,
    hasCustomBackground: Boolean,
    onSelectBackgroundImage: () -> Unit,
    onUseBundledBackground: () -> Unit,
    onUseGradientBackground: () -> Unit,
    onAdjustCustomBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    weekCardAlpha: Float,
    onWeekCardAlphaChange: (Float) -> Unit,
    weekCardHue: Float,
    onWeekCardHueChange: (Float) -> Unit,
) {
    val previewColor = remember(weekCardHue, weekCardAlpha) {
        colorWithHueShift(Color(0xFFE98AA9), weekCardHue).copy(alpha = weekCardAlpha)
    }
    val backgroundSummary = when (backgroundMode) {
        AppBackgroundMode.BUNDLED_IMAGE -> "当前使用默认背景图"
        AppBackgroundMode.CUSTOM_IMAGE -> "当前使用自定义背景图"
        AppBackgroundMode.GRADIENT -> "当前只显示渐变背景"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("背景与色块", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("背景图片", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = backgroundSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "自定义图片会保存在本地，重启应用后仍会保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                    )
                    Button(
                        onClick = {
                            onDismiss()
                            onSelectBackgroundImage()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("选择自定义图片")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onUseBundledBackground,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("默认图片")
                        }
                        OutlinedButton(
                            onClick = onUseGradientBackground,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("仅渐变")
                        }
                    }
                    if (hasCustomBackground) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onAdjustCustomBackground()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("调整范围")
                            }
                            TextButton(
                                onClick = onClearCustomBackground,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("清除自定义图片")
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("周视图色相", style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = weekCardHue,
                        onValueChange = onWeekCardHueChange,
                        valueRange = 0f..360f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = previewColor,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(width = 56.dp, height = 32.dp),
                        ) {}
                        Text(
                            text = "当前色相 ${weekCardHue.toInt()}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("周视图透明度", style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = weekCardAlpha,
                        onValueChange = onWeekCardAlphaChange,
                        valueRange = 0.35f..1.0f,
                    )
                    Text(
                        text = "当前透明度 ${(weekCardAlpha * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun ReminderPickerDialog(
    reminderMinutes: Int,
    reminderOptions: List<Int>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onEnableNotifications: () -> Unit,
) {
    var customReminderText by remember(reminderMinutes) {
        mutableStateOf(reminderMinutes.takeIf { it !in reminderOptions }?.toString().orEmpty())
    }
    var customReminderError by remember { mutableStateOf<String?>(null) }

    fun submitCustomReminder() {
        val parsedMinutes = customReminderText.toIntOrNull()
        if (parsedMinutes == null || !CourseReminderScheduler.isReminderMinutesValid(parsedMinutes)) {
            customReminderError = "请输入 1-180 分钟"
            return
        }
        customReminderError = null
        onSelect(parsedMinutes)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课前提醒时间", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "选择课程开始前多久接收提醒通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    reminderOptions.forEach { option ->
                        val selected = option == reminderMinutes
                        if (selected) {
                            Button(
                                onClick = { onSelect(option) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("${option}m") }
                        } else {
                            OutlinedButton(
                                onClick = { onSelect(option) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("${option}m") }
                        }
                    }
                }
                Text(
                    text = "也可以输入 1-180 分钟的自定义提醒时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = customReminderText,
                    onValueChange = { value ->
                        customReminderText = value.filter { it.isDigit() }.take(3)
                        customReminderError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("自定义分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = customReminderError != null,
                    supportingText = {
                        Text(customReminderError ?: "输入后点“保存自定义提醒”生效")
                    },
                )
                OutlinedButton(
                    onClick = ::submitCustomReminder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存自定义提醒")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEnableNotifications) { Text("开启通知权限") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "按时间排序",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ViewModeSwitcher(
    currentDestination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)) {
        NavigationBarItem(
            selected = currentDestination == AppDestination.DAY,
            onClick = { onDestinationChange(AppDestination.DAY) },
            icon = {},
            label = { Text("日视图") },
        )
        NavigationBarItem(
            selected = currentDestination == AppDestination.WEEK,
            onClick = { onDestinationChange(AppDestination.WEEK) },
            icon = {},
            label = { Text("周视图") },
        )
        NavigationBarItem(
            selected = currentDestination == AppDestination.SETTINGS,
            onClick = { onDestinationChange(AppDestination.SETTINGS) },
            icon = {},
            label = { Text("设置") },
        )
    }
}
