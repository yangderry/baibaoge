package com.baibaoge.home.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 首页占位：临期提醒与统计在阶段 6 实现 */
@Composable
fun HomeScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("百宝格", style = MaterialTheme.typography.headlineSmall)
        Text(
            "临期提醒与统计将在后续阶段开放",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
