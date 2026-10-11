package com.kers.killove.jhsy.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

private const val WALLHAVEN_JOIN = "https://wallhaven.cc/join"
private const val WALLHAVEN_LOGIN = "https://wallhaven.cc/login"
private const val WALLHAVEN_ACCOUNT = "https://wallhaven.cc/settings/account"

@Composable
fun WallhavenApiSetupDialog(
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("请填写用户名、邮箱、密码与确认密码") }
    var discoveredKey by remember { mutableStateOf<String?>(null) }
    var registrationSubmitted by remember { mutableStateOf(false) }
    var loginFilled by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("获取 Wallhaven API Key", style = MaterialTheme.typography.titleLarge)
                Text(status, style = MaterialTheme.typography.bodySmall)

                if (!started) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { if (it.length <= 12) username = it },
                            label = { Text("用户名（3-12字符）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("邮箱") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("确认密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "流程：打开注册页填表 → 提交注册 → 自动登录 → 读取 API Key。\n" +
                                "仅用于当前流程，不会保存账号密码。若出现验证码请在网页中手动完成。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("取消")
                        }
                        Button(
                            enabled = username.length in 3..12
                                && email.contains("@")
                                && password.isNotEmpty()
                                && confirmPassword == password,
                            onClick = {
                                started = true
                                status = "正在打开注册页…"
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("开始注册") }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        factory = {
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean = false

                                    override fun onPageFinished(view: WebView, url: String) {
                                        super.onPageFinished(view, url)
                                        val u = url.lowercase()
                                        when {
                                            u.contains("/join") -> {
                                                status = "正在填写注册表单并提交…"
                                                fillRegistration(view, username, email, password, confirmPassword)
                                            }
                                            u.contains("/login") || u.contains("/auth/login") -> {
                                                if (!loginFilled) {
                                                    status = "注册完成，正在自动登录…"
                                                    fillLogin(view, username, password)
                                                    loginFilled = true
                                                    registrationSubmitted = true
                                                }
                                            }
                                            u.contains("/settings/account") -> {
                                                status = "正在读取账户设置中的 API Key…"
                                                extractApiKey(view)
                                            }
                                            // 登录成功后常跳首页，自动进账户设置
                                            (u == "https://wallhaven.cc/" || u == "https://wallhaven.cc" ||
                                                u.endsWith("wallhaven.cc/") || u.contains("wallhaven.cc/?") ||
                                                u.contains("/user/")) && loginFilled && discoveredKey == null -> {
                                                status = "登录成功，打开账户设置…"
                                                view.postDelayed({ view.loadUrl(WALLHAVEN_ACCOUNT) }, 600)
                                            }
                                        }
                                    }
                                }
                                val bridge = object {
                                    @JavascriptInterface
                                    fun status(message: String) {
                                        status = message
                                    }

                                    @JavascriptInterface
                                    fun keyFound(key: String) {
                                        val k = key.trim()
                                        if (k.isNotBlank()) discoveredKey = k
                                    }
                                }
                                addJavascriptInterface(bridge, "WallhavenBridge")
                                webView = this
                                // 直接进入注册页
                                loadUrl(WALLHAVEN_JOIN)
                            }
                        },
                        update = { webView = it }
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("关闭")
                        }
                        Button(
                            onClick = {
                                when {
                                    !registrationSubmitted && !loginFilled -> {
                                        webView?.evaluateJavascript(
                                            "window.WallhavenSubmit && window.WallhavenSubmit();",
                                            null
                                        )
                                        registrationSubmitted = true
                                        status = "已提交注册；完成后网站会跳转登录，将自动填写并登录。"
                                    }
                                    discoveredKey == null -> {
                                        webView?.loadUrl(WALLHAVEN_ACCOUNT)
                                        status = "正在打开账户设置并提取 API Key…"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when {
                                    !registrationSubmitted && !loginFilled -> "提交注册"
                                    else -> "获取 API Key"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (discoveredKey != null) {
        AlertDialog(
            onDismissRequest = { discoveredKey = null },
            title = { Text("确认保存 API Key？") },
            text = {
                Column {
                    Text("以下密钥将写入应用本地配置，不会上传到服务器。请确认：")
                    SelectionContainer {
                        Text(discoveredKey.orEmpty(), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveKey(discoveredKey.orEmpty())
                    discoveredKey = null
                    onDismiss()
                }) { Text("确认保存") }
            },
            dismissButton = {
                OutlinedButton(onClick = { discoveredKey = null }) { Text("不保存") }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun fillRegistration(
    web: WebView,
    username: String,
    email: String,
    password: String,
    confirmPassword: String
) {
    val usernameJson = JSONObject.quote(username)
    val emailJson = JSONObject.quote(email)
    val passwordJson = JSONObject.quote(password)
    val confirmJson = JSONObject.quote(confirmPassword)
    web.evaluateJavascript(
        """
        (function() {
          if (window.WallhavenAutofilledReg) return;
          window.WallhavenAutofilledReg = true;
          const username = $usernameJson;
          const email = $emailJson;
          const password = $passwordJson;
          const confirmPassword = $confirmJson;
          const inputs = Array.from(document.querySelectorAll('input'));
          const usernameInput = inputs.find(x => {
            const meta = (x.name + ' ' + x.id + ' ' + x.placeholder + ' ' + (x.getAttribute('autocomplete') || '')).toLowerCase();
            return x.type === 'text' && /user|username|nick|name/.test(meta) && !/email|mail|pass/.test(meta);
          }) || inputs.find(x => x.type === 'text' && !/email|mail|pass|captcha|code/.test((x.name + ' ' + x.id + ' ' + x.placeholder).toLowerCase()));
          const emailInput = inputs.find(x => x.type === 'email' || /email|mail/i.test(x.name + ' ' + x.id + ' ' + x.placeholder));
          const passwordInputs = inputs.filter(x => x.type === 'password');
          const passwordInput = passwordInputs.find(x => /confirm|repeat|again|verify/i.test(x.name + ' ' + x.id + ' ' + x.placeholder) === false) || passwordInputs[0];
          const confirmInput = passwordInputs.find(x => /confirm|repeat|again|verify/i.test(x.name + ' ' + x.id + ' ' + x.placeholder)) || passwordInputs[1];
          function put(input, value) {
            if (!input) return;
            const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
            setter.call(input, value);
            input.dispatchEvent(new Event('input', {bubbles:true}));
            input.dispatchEvent(new Event('change', {bubbles:true}));
          }
          put(usernameInput, username);
          put(emailInput, email);
          put(passwordInput, password);
          put(confirmInput, confirmPassword);
          const body = (document.body.innerText || '').toLowerCase();
          if (body.includes('already exists') || body.includes('already registered') || body.includes('email is already') || body.includes('username is already')) {
            window.WallhavenBridge.status('账号已存在：请改用登录，或在账户设置中直接获取 API Key。');
            return;
          }
          function findRegisterSubmit() {
            const byType = inputs.find(x => x.type === 'submit');
            if (byType) return byType;
            const btns = Array.from(document.querySelectorAll('button, input[type="submit"], a.button, a.btn'));
            return btns.find(b => /register|sign\s*up|create|join|注册/i.test((b.textContent || b.value || '') + ' ' + (b.className || '')))
              || document.querySelector('button[type="submit"]')
              || btns.find(b => /btn|button|submit/i.test(b.className || ''));
          }
          window.WallhavenSubmit = function() {
            const submit = findRegisterSubmit();
            if (submit) { submit.click(); window.WallhavenBridge.status('已点击注册提交…'); }
            else window.WallhavenBridge.status('未找到注册按钮，请在页面手动点击 Register。');
          };
          const hasVerification = !!document.querySelector('img[src*="captcha"], iframe[src*="captcha"], iframe[src*="turnstile"], iframe[src*="recaptcha"], input[name*="captcha" i], input[id*="captcha" i]')
            || body.includes('captcha') || body.includes('verification') || body.includes('turnstile');
          if (hasVerification) {
            window.WallhavenBridge.status('表单已填好，请完成验证码后点「提交注册」。');
            return;
          }
          window.WallhavenBridge.status('注册表单已填写，正在自动提交…');
          setTimeout(function() { window.WallhavenSubmit(); }, 500);
        })();
        """.trimIndent(),
        null
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun fillLogin(web: WebView, username: String, password: String) {
    val usernameJson = JSONObject.quote(username)
    val passwordJson = JSONObject.quote(password)
    web.evaluateJavascript(
        """
        (function() {
          if (window.WallhavenAutofilledLogin) return;
          window.WallhavenAutofilledLogin = true;
          const username = $usernameJson;
          const password = $passwordJson;
          const inputs = Array.from(document.querySelectorAll('input'));
          const userInput = inputs.find(x => {
            const meta = (x.name + ' ' + x.id + ' ' + x.placeholder + ' ' + (x.getAttribute('autocomplete') || '')).toLowerCase();
            return x.type !== 'password' && (/user|username|login|email|mail|name/.test(meta) || x.type === 'text' || x.type === 'email');
          }) || inputs.find(x => x.type === 'text' || x.type === 'email');
          const passInput = inputs.find(x => x.type === 'password');
          function put(input, value) {
            if (!input) return;
            const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
            setter.call(input, value);
            input.dispatchEvent(new Event('input', {bubbles:true}));
            input.dispatchEvent(new Event('change', {bubbles:true}));
          }
          put(userInput, username);
          put(passInput, password);
          window.WallhavenBridge.status('登录表单已填写（用户名+密码），正在点击登录…');
          setTimeout(function() {
            const submit = inputs.find(x => x.type === 'submit') || document.querySelector('button[type="submit"]') || document.querySelector('button.btn');
            if (submit) submit.click();
            else window.WallhavenBridge.status('未找到登录按钮，请手动点击 Login。');
          }, 400);
        })();
        """.trimIndent(),
        null
    )
}

/**
 * Wallhaven API Key 多为 32 位字母数字（如 sKCOEJvHYJRcQ1cs9vLIBwqaiDExv8TW）。
 * 旧逻辑用 {24,128} 会误匹配更长的 CSRF/会话串。
 */
private fun extractApiKey(web: WebView) {
    web.evaluateJavascript(
        """
        (function() {
          function score(s) {
            if (!s) return -1;
            s = s.trim();
            // 排除明显非 key
            if (/csrf|session|token|cookie|php|http|www\./i.test(s)) return -1;
            if (!/^[A-Za-z0-9]+${'$'}/.test(s)) return -1;
            if (s.length === 32 && /[A-Za-z]/.test(s) && /[0-9]/.test(s)) return 100;
            if (s.length >= 28 && s.length <= 40 && /[A-Za-z]/.test(s) && /[0-9]/.test(s)) return 50;
            return -1;
          }
          let best = '';
          let bestScore = -1;
          // 1) 优先：标签/文案含 API Key 的附近 input
          const labels = Array.from(document.querySelectorAll('label, th, td, dt, strong, span, p, div'));
          for (const el of labels) {
            const t = (el.textContent || '').toLowerCase();
            if (!t.includes('api') || !t.includes('key')) continue;
            let input = el.querySelector && el.querySelector('input');
            if (!input && el.nextElementSibling) {
              input = el.nextElementSibling.querySelector
                ? el.nextElementSibling.querySelector('input')
                : (el.nextElementSibling.tagName === 'INPUT' ? el.nextElementSibling : null);
            }
            if (!input && el.parentElement) input = el.parentElement.querySelector('input[type="text"], input:not([type])');
            if (input && input.value) {
              const sc = score(input.value);
              if (sc > bestScore) { bestScore = sc; best = input.value.trim(); }
            }
          }
          // 2) name/id 含 apikey 的 input
          const inputs = Array.from(document.querySelectorAll('input'));
          for (const inp of inputs) {
            const meta = ((inp.name || '') + ' ' + (inp.id || '') + ' ' + (inp.placeholder || '')).toLowerCase();
            if (/api.?key|apikey/.test(meta) && inp.value) {
              const sc = score(inp.value) + 20;
              if (sc > bestScore) { bestScore = sc; best = inp.value.trim(); }
            }
          }
          // 3) 所有 input value 与正文中的 32 位候选
          for (const inp of inputs) {
            if (!inp.value) continue;
            const sc = score(inp.value);
            if (sc > bestScore) { bestScore = sc; best = inp.value.trim(); }
          }
          const text = document.body.innerText || '';
          const re = /\\b([A-Za-z0-9]{28,40})\\b/g;
          let m;
          while ((m = re.exec(text)) !== null) {
            const sc = score(m[1]);
            if (sc > bestScore) { bestScore = sc; best = m[1]; }
          }
          if (best && bestScore >= 50) {
            window.WallhavenBridge.keyFound(best);
          } else if (best) {
            window.WallhavenBridge.keyFound(best);
            window.WallhavenBridge.status('已候选密钥（请核对长度约 32 位）：' + best);
          } else {
            window.WallhavenBridge.status('未找到 API Key。请确认已登录并打开账户设置页，或手动复制密钥。');
          }
        })();
        """.trimIndent(),
        null
    )
}
