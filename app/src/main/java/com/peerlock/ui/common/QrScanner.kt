package com.peerlock.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun QrScanLauncher(
    onResult: (String?) -> Unit,
    trigger: Int,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lastTrigger by remember { mutableIntStateOf(0) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        onResult(result.contents)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            val options = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("扫描二维码")
                .setBeepEnabled(false)
                .setOrientationLocked(true)
            scanLauncher.launch(options)
        }
    }

    LaunchedEffect(trigger) {
        if (trigger > 0 && trigger != lastTrigger) {
            lastTrigger = trigger
            if (hasPermission) {
                val options = ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("扫描二维码")
                    .setBeepEnabled(false)
                    .setOrientationLocked(true)
                scanLauncher.launch(options)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}
