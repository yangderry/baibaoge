package com.baibaoge.home.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.auth.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 首次启动引导：设置 4-6 位 PIN（两步确认 + 指纹开关） */
@Composable
fun PinSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { AuthManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var fingerprintOn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("设置 PIN 码", style = MaterialTheme.typography.headlineSmall)
        Text(
            "用于保护家庭库存数据，4-6 位数字",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.padding(8.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= AuthManager.PIN_MAX_LENGTH) pin = it.filter(Char::isDigit)
            },
            label = { Text("输入 PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = {
                if (it.length <= AuthManager.PIN_MAX_LENGTH) confirm = it.filter(Char::isDigit)
            },
            label = { Text("再次输入确认") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用指纹解锁", Modifier.weight(1f))
            Switch(checked = fingerprintOn, onCheckedChange = { fingerprintOn = it })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.padding(8.dp))
        Button(
            onClick = {
                when {
                    pin.length < AuthManager.PIN_MIN_LENGTH ->
                        error = "PIN 至少 ${AuthManager.PIN_MIN_LENGTH} 位"
                    pin != confirm -> error = "两次输入不一致"
                    else -> scope.launch {
                        auth.setupPin(pin, fingerprintOn)
                        onDone()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("完成") }
    }
}

/** 锁定页：冷启动/回前台验证 PIN，支持指纹 */
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
    var tick by remember { mutableStateOf(0) }

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

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("百宝格已锁定", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.padding(8.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= AuthManager.PIN_MAX_LENGTH) pin = it.filter(Char::isDigit)
            },
            label = { Text("输入 PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        @Suppress("UNUSED_EXPRESSION") tick // 触发每秒重组刷新倒计时文案
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.padding(8.dp))
        Button(
            onClick = {
                scope.launch {
                    val err = auth.verifyPin(pin)
                    if (err == null) onUnlocked() else {
                        error = err
                        pin = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("解锁") }
        if (canFingerprint && activity != null) {
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
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("使用指纹解锁") }
        }
    }
}
