package com.example.timetable.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 英雄区域组件
 * 显示应用简介、课程统计和主要操作按钮
 *
 * @param courseCount 课程总数
 * @param onImport 导入按钮点击回调
 * @param onExport 导出按钮点击回调
 * @param onShare 分享按钮点击回调
 */
@Composable
fun HeroSection(
    courseCount: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onEnableNotifications: () -> Unit,
    reminderMinutes: Int,
    reminderOptions: List<Int>,
    onReminderMinutesChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "简约、清晰、可分享",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "当前共 $courseCount 门课程。支持导入和导出 .ics 格式。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Text("导入日历", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Text("导出日历", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            TextButton(onClick = onEnableNotifications) {
                Text("开启手机通知")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "提醒提前时间：$reminderMinutes 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    reminderOptions.forEach { option ->
                        if (option == reminderMinutes) {
                            Button(onClick = { onReminderMinutesChange(option) }) {
                                Text("${option}m")
                            }
                        } else {
                            OutlinedButton(onClick = { onReminderMinutesChange(option) }) {
                                Text("${option}m")
                            }
                        }
                    }
                }
            }
        }
    }
}
/**
 * 分区标题组件
 * 显示当前查看的星期标题
 *
 * @param title 标题文本
 */
@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text("按时间顺序展示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
