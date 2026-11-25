package app.tiinyremote

import android.annotation.SuppressLint
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/**
 * A 3D model (.glb) shown inside the app: turn it with a finger, pinch to zoom. Phones rarely have a 3D viewer,
 * so the app brings its own (Google's model-viewer, in assets/viewer). Nothing is loaded from the internet:
 * the page, the viewer and the model are all served from the phone.
 * The first time a model is shown, a picture of it is kept (…-render.png) for the Gallery.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MeshViewer(title: String, glb: File, render: File, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(8.dp))
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Text(
                    "Turn it with a finger, pinch to zoom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                AndroidView(
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            addJavascriptInterface(object {
                                @JavascriptInterface fun wantPicture(): Boolean = !render.exists()
                                @JavascriptInterface fun picture(dataUrl: String) {
                                    runCatching {
                                        val bytes = Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT)
                                        if (bytes.size in 1000..20_000_000) render.writeBytes(bytes)
                                    }
                                }
                            }, "Tiiny")
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val u = request.url
                                    if (u.host != HOST) return WebResourceResponse("text/plain", "utf-8", 404, "Not here", emptyMap(), "".byteInputStream())
                                    return when (u.path) {
                                        "/viewer.html" -> WebResourceResponse("text/html", "utf-8", PAGE.byteInputStream())
                                        "/model-viewer.min.js" -> WebResourceResponse("text/javascript", "utf-8", c.assets.open("viewer/model-viewer.min.js"))
                                        "/model.glb" -> WebResourceResponse("model/gltf-binary", null, glb.inputStream())
                                        else -> WebResourceResponse("text/plain", "utf-8", 404, "Not here", emptyMap(), "".byteInputStream())
                                    }
                                }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                            }
                            // Loaded once the view has its size (as in Code's preview).
                            var loaded = false
                            addOnLayoutChangeListener { v, _, top, _, bottom, _, _, _, _ ->
                                if (!loaded && bottom - top > 0) {
                                    loaded = true
                                    (v as WebView).loadUrl("https://$HOST/viewer.html")
                                }
                            }
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                )
            }
        }
    }
}

private const val HOST = "viewer.tiiny.invalid"

private val PAGE = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<script type="module" src="model-viewer.min.js"></script>
<style>
html,body{margin:0;height:100%;background:transparent;color:#bbb;font-family:sans-serif}
model-viewer{width:100%;height:100%;--poster-color:transparent}
#msg{position:absolute;top:45%;width:100%;text-align:center}
</style></head><body>
<div id="msg">Loading the 3D model…</div>
<model-viewer id="mv" src="model.glb" camera-controls touch-action="none" interaction-prompt="none"
  shadow-intensity="1" environment-image="neutral" exposure="1"></model-viewer>
<script>
const mv = document.getElementById('mv'), msg = document.getElementById('msg');
mv.addEventListener('progress', e => { if (msg) msg.textContent = 'Loading the 3D model… ' + Math.round(e.detail.totalProgress * 100) + ' %'; });
mv.addEventListener('load', () => {
  msg.remove();
  // A picture for the Gallery, once: taken before anyone turns it.
  setTimeout(async () => {
    try {
      if (window.Tiiny && Tiiny.wantPicture()) {
        const b = await mv.toBlob({ mimeType: 'image/png', idealAspect: true });
        const r = new FileReader(); r.onload = () => Tiiny.picture(r.result); r.readAsDataURL(b);
      }
    } catch (e) {}
    mv.setAttribute('auto-rotate', '');
  }, 700);
});
mv.addEventListener('error', () => { msg.textContent = 'This 3D model could not be shown. Share it to open it elsewhere.'; });
</script></body></html>
""".trimIndent()
