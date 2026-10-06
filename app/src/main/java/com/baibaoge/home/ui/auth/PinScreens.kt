package com.baibaoge.home.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.auth.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 首次启动引导：图形键盘设置 4-6 位 PIN（两步确认 + 指纹开关） */
@Composable
fun PinSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { AuthManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) } // 0=输入，1=确认
    var fingerprintOn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (step == 0) "设置 PIN 码" else "再次输入确认",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "用于保护家庭库存数据，4-6 位数字",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.padding(8.dp))
        PinDots(filled = if (step == 0) pin.length else confirm.length)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.padding(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用指纹解锁")
            Spacer(Modifier.padding(4.dp))
            Switch(checked = fingerprintOn, onCheckedChange = { fingerprintOn = it })
        }
        Spacer(Modifier.padding(8.dp))
        PinKeyboard(
            onDigit = { d ->
                if (step == 0) {
                    if (pin.length < AuthManager.PIN_MAX_LENGTH) pin += d
                } else {
                    if (confirm.length < AuthManager.PIN_MAX_LENGTH) confirm += d
                }
                error = null
            },
            onDelete = {
                if (step == 0) pin = pin.dropLast(1) else confirm = confirm.dropLast(1)
                error = null
            }
        )
        Spacer(Modifier.padding(8.dp))
        Button(
            onClick = {
                if (step == 0) {
                    when {
                        pin.length < AuthManager.PIN_MIN_LENGTH ->
                            error = "PIN 至少 ${AuthManager.PIN_MIN_LENGTH} 位"
                        else -> step = 1
                    }
                } else {
                    when {
                        confirm.length < AuthManager.PIN_MIN_LENGTH ->
                            error = "PIN 至少 ${AuthManager.PIN_MIN_LENGTH} 位"
                        pin != confirm -> {
                            error = "两次输入不一致"
                            confirm = ""
                        }
                        else -> scope.launch {
                            auth.setupPin(pin, fingerprintOn)
                            onDone()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (step == 0) "下一步" else "完成") }
    }
}

/** 锁定页：图形键盘输入 PIN，输满已设定位数自动验证，支持指纹 */
@Composable
fun PinLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val auth = remember { AuthManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var canFingerprint by remember { mutableStateOf(false) }
    // 锁定倒计时刷新
    var tick by remember { mutableIntStateOf(0) }

    val pinLength = remember { auth.pinLength().takeIf { it > 0 } ?: AuthManager.PIN_MAX_LENGTH }

    LaunchedEffect(Unit) {
        canFingerprint = auth.fingerprintEnabled() &&
                activity != null && BiometricHelper.canAuthenticate(activity)
        while (true) {
            delay(1000)
            tick++
        }
    }

    // 进入页面自动弹指纹
    LaunchedEffect(canFingerprint) {
        if (canFingerprint && activity != null) {
            BiometricHelper.showPrompt(
                activity,
                onSuccess = {
                    auth.onFingerprintSuccess()
                    onUnlocked()
                },
                onError = { }
            )
        }
    }

    fun tryVerify(input: String) {
        scope.launch {
            val err = auth.verifyPin(input)
            if (err == null) onUnlocked() else {
                error = err
                pin = ""
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("百宝格已锁定", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.padding(12.dp))
        PinDots(filled = pin.length, total = pinLength)
        @Suppress("UNUSED_EXPRESSION") tick // 触发每秒重组刷新倒计时文案
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.padding(12.dp))
        PinKeyboard(
            onDigit = { d ->
                if (pin.length < pinLength) {
                    pin += d
                    error = null
                    // 输满已设定位数自动验证
                    if (pin.length == pinLength) tryVerify(pin)
                }
            },
            onDelete = {
                pin = pin.dropLast(1)
                error = null
            }
        )
        if (canFingerprint && activity != null) {
            Spacer(Modifier.padding(8.dp))
            TextButton(
                onClick = {
                    BiometricHelper.showPrompt(
                        activity,
                        onSuccess = {
                            auth.onFingerprintSuccess()
                            onUnlocked()
                        },
                        onError = { }
                    )
                }
            ) { Text("使用指纹解锁") }
        }
    }
}

/** 圆点指示器：实心=已输入位数 */
@Composable
private fun PinDots(filled: Int, total: Int = AuthManager.PIN_MAX_LENGTH) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (i < filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

/** 3x4 数字键盘：1-9、空、0、删除 */
@Composable
private fun PinKeyboard(onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(' ', '0', '<')
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    when (key) {
                        ' ' -> Spacer(Modifier.size(64.dp))
                        '<' -> KeyButton("⌫") { onDelete() }
                        else -> KeyButton(key.toString()) { onDigit(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}
