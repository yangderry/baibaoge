package com.baibaoge.home

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.ui.auth.PinLockScreen
import com.baibaoge.home.ui.auth.PinSetupScreen
import com.baibaoge.home.ui.navigation.AppNavHost
import com.baibaoge.home.ui.theme.BaibaogeTheme

/** BiometricPrompt 需要 FragmentActivity */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 切后台自动锁定
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                AuthManager.getInstance(applicationContext).lock()
            }
        })
        setContent {
            BaibaogeTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val auth = remember { AuthManager.getInstance(context) }
    val pinSet by auth.pinSet.collectAsState()
    val locked by auth.locked.collectAsState()

    LaunchedEffect(Unit) { auth.init() }

    when {
        !pinSet -> PinSetupScreen(onDone = { })
        locked -> PinLockScreen(onUnlocked = { })
        else -> AppNavHost()
    }
}
