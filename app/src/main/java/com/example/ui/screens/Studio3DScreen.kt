package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private enum class SceneModelPreset(val id: String, val labelFa: String) {
    CONDENSER_MIC("condenser_mic", "میکروفون استودیویی"),
    STUDIO_MONITOR("studio_monitor", "اسپیکر مانیتورینگ"),
    VOICE_CORE("voice_core", "گوی آوایی 3D")
}

private enum class StudioLightingPreset(val id: String, val labelFa: String) {
    WARM_STUDIO("warm_studio", "نور استودیو گرم"),
    NEON_CYBER("neon_cyber", "نور نئون آکوستیک"),
    MOONLIGHT_MATTE("moonlight_matte", "نور مهتابی مات")
}

private enum class PbrMaterialPreset(val id: String, val labelFa: String) {
    BRUSHED_GOLD("brushed_gold", "طلایی برس‌خورده"),
    TITANIUM_MATTE("titanium_matte", "تیتانیوم مات"),
    CARBON_OBSIDIAN("carbon_obsidian", "کربن تیره")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Studio3DScreen(
    pitch: Float,
    speed: Float,
    isAudioActive: Boolean,
    audioEnergy: Float,
    onBackToStudio: () -> Unit
) {
    BackHandler { onBackToStudio() }

    val context = LocalContext.current
    var selectedScene by remember { mutableStateOf(SceneModelPreset.CONDENSER_MIC) }
    var selectedLighting by remember { mutableStateOf(StudioLightingPreset.WARM_STUDIO) }
    var selectedMaterial by remember { mutableStateOf(PbrMaterialPreset.BRUSHED_GOLD) }
    var autoRotate by remember { mutableStateOf(true) }

    var isStudioReady by remember { mutableStateOf(false) }
    var studioError by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val htmlContent = remember {
        context.assets.open("studio3d.html").bufferedReader().use { it.readText() }
    }

    fun pushSceneParams(wv: WebView?) {
        wv ?: return
        if (!isStudioReady) return
        wv.evaluateJavascript(
            """
            window.Studio3D && (
              window.Studio3D.setScene('${selectedScene.id}'),
              window.Studio3D.setLighting('${selectedLighting.id}'),
              window.Studio3D.setMaterial('${selectedMaterial.id}'),
              window.Studio3D.setAutoRotate(${autoRotate}),
              window.Studio3D.setLiveTelemetry($audioEnergy, $pitch, $speed, $isAudioActive)
            );
            """.trimIndent(),
            null
        )
    }

    LaunchedEffect(isStudioReady, selectedScene, selectedLighting, selectedMaterial, autoRotate) {
        pushSceneParams(webViewRef)
    }

    LaunchedEffect(isStudioReady, audioEnergy, pitch, speed, isAudioActive) {
        if (isStudioReady) {
            webViewRef?.evaluateJavascript(
                "window.Studio3D && window.Studio3D.setLiveTelemetry($audioEnergy, $pitch, $speed, $isAudioActive);",
                null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    // Zero-occlusion vertical layout: full unobstructed 3D WebGL stage on top, docked control console below
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("threejs_pbr_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(AndroidColor.parseColor("#09111F"))
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onStudioReady() {
                                    post {
                                        studioError = null
                                        isStudioReady = true
                                    }
                                }

                                @JavascriptInterface
                                fun onStudioError(message: String) {
                                    post {
                                        if (!isStudioReady) {
                                            studioError = message
                                        }
                                    }
                                }
                            },
                            "AndroidBridge"
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    studioError = error?.description?.toString()
                                        ?: "خطا در بارگذاری صحنه سه‌بعدی"
                                }
                            }
                        }
                        webViewRef = this
                        loadDataWithBaseURL(
                            "https://cdn.jsdelivr.net/",
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { wv ->
                    if (wv.tag != reloadNonce) {
                        wv.tag = reloadNonce
                        if (reloadNonce > 0) {
                            isStudioReady = false
                            studioError = null
                            wv.loadDataWithBaseURL(
                                "https://cdn.jsdelivr.net/",
                                htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                }
            )

            // Minimal edge-pinned corner pill (zero occlusion of center model)
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Three.js PBR • تکسچر رویه‌ای",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Minimal corner camera controls (48dp touch targets, edge pinned)
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { autoRotate = !autoRotate },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("threejs_autorotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "چرخش خودکار دوربین",
                            tint = if (autoRotate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            webViewRef?.evaluateJavascript("window.Studio3D && window.Studio3D.resetCamera();", null)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("threejs_reset_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "بازنشانی زاویه دوربین"
                        )
                    }
                }
            }

            if (!isStudioReady && studioError == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("در حال آماده‌سازی رندر استودیویی PBR...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (studioError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = studioError ?: "خطا در بارگذاری Three.js",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = { reloadNonce++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تلاش مجدد")
                        }
                    }
                }
            }
        }

        // Docked Control Console (strictly below 3D viewport -> zero occlusion)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "کنترل‌های استودیو سه‌بعدی (چرخش با لمس / زوم دو انگشتی)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "لحن ${"%.2f".format(pitch)}x • سرعت ${"%.2f".format(speed)}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SceneModelPreset.entries) { item ->
                        FilterChip(
                            selected = selectedScene == item,
                            onClick = { selectedScene = item },
                            label = { Text(item.labelFa) },
                            modifier = Modifier.testTag("scene_3d_${item.id}")
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StudioLightingPreset.entries) { item ->
                        FilterChip(
                            selected = selectedLighting == item,
                            onClick = { selectedLighting = item },
                            label = { Text(item.labelFa) },
                            modifier = Modifier.testTag("light_3d_${item.id}")
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PbrMaterialPreset.entries) { item ->
                        FilterChip(
                            selected = selectedMaterial == item,
                            onClick = { selectedMaterial = item },
                            label = { Text(item.labelFa) },
                            modifier = Modifier.testTag("mat_3d_${item.id}")
                        )
                    }
                }
            }
        }
    }
}
