package com.kers.killove.jhsy.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
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
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kers.killove.jhsy.data.remote.WallhavenApi
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
    var keyValid by remember { mutableStateOf<Boolean?>(null) }
    var keyValidateMsg by remember { mutableStateOf("") }
    var keyValidating by remember { mutableStateOf(false) }
    var registrationSubmitted by remember { mutableStateOf(false) }
    var loginFilled by remember { mutableStateOf(false) }
    /** 用户确认已通过 Cloudflare 人机验证后才允许自动填表/提交 */
    var cfPassed by remember { mutableStateOf(false) }
    var formFilled by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    // 提取到 Key 后自动检测有效性，通过才允许写入
    LaunchedEffect(discoveredKey) {
        val k = discoveredKey?.trim().orEmpty()
        if (k.isEmpty()) return@LaunchedEffect
        keyValidating = true
        keyValid = null
        keyValidateMsg = "正在检测 API Key 有效性…"
        status = keyValidateMsg
        val probe = withContext(Dispatchers.IO) {
            try {
                WallhavenApi().validateApiKey(k)
            } catch (e: Exception) {
                null
            }
        }
        keyValidating = false
        if (probe == null) {
            keyValid = false
            keyValidateMsg = "检测异常"
            status = "API Key 检测失败，未写入本地"
        } else {
            keyValid = probe.ok
            keyValidateMsg = probe.detail
            status = if (probe.ok) {
                "API Key 有效（${probe.detail}），可确认保存"
            } else {
                "API Key 无效，未写入本地：${probe.detail}"
            }
        }
    }


    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = true
        )
    ) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, true)
            }
        }
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    // Dialog 在部分机型拿不到 insets，再固定垫高底部，保证按钮完整可见
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
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
                            "流程：清空 wallhaven.cc Cookie/站点数据 → 打开注册页 → 完成 CF 验证 →「已通过CF验证」→ 填表 → 提交注册 → 登录取 Key。\n" +
                                "不会强制刷新或反复提交。验证码/CF 请在网页中手动完成。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                        ) { Text("取消") }
                        Button(
                            enabled = username.length in 3..12
                                && email.contains("@")
                                && password.isNotEmpty()
                                && confirmPassword == password,
                            onClick = {
                                clearWallhavenSiteData(null)
                                started = true
                                status = "已清空 wallhaven.cc Cookie/站点数据，正在打开注册页…"
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp)
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
                                settings.databaseEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                // 每次注册流程开始：清空本站 Cookie 与本地网站数据
                                clearWallhavenSiteData(this)
                                webViewClient = object : WebViewClient() {
                                    private var accountRequested = false

                                    private fun isWallhaven(u: String) =
                                        u.contains("wallhaven.cc")

                                    private fun isLogin(u: String) =
                                        u.contains("/login") || u.contains("/auth/login")

                                    private fun isJoin(u: String) =
                                        u.contains("/join")

                                    private fun isAccount(u: String) =
                                        u.contains("/settings/account")

                                    /** 登录成功：已离开登录/注册页，尽快进账户设置（不等整页加载完） */
                                    private fun onLeftLogin(view: WebView, url: String) {
                                        val u = url.lowercase()
                                        if (!loginFilled || discoveredKey != null) return
                                        if (!isWallhaven(u) || isLogin(u) || isJoin(u)) return
                                        if (isAccount(u)) return
                                        if (accountRequested) return
                                        accountRequested = true
                                        status = "登录成功，正在打开账户设置…"
                                        view.post {
                                            if (discoveredKey == null) {
                                                view.loadUrl(WALLHAVEN_ACCOUNT)
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean {
                                        val u = request.url?.toString().orEmpty()
                                        onLeftLogin(view, u)
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String,
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        val u = url.lowercase()
                                        onLeftLogin(view, u)
                                        if (isAccount(u)) {
                                            status = "正在打开账户设置…"
                                        }
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        super.onPageFinished(view, url)
                                        val u = url.lowercase()
                                        // 账户页：立刻抽 Key，不等其它逻辑
                                        if (isAccount(u)) {
                                            status = "正在读取账户设置中的 API Key…"
                                            extractApiKey(view)
                                            return
                                        }
                                        // 登录后已离开登录页：补一次跳转（onPageStarted 可能已处理）
                                        onLeftLogin(view, u)

                                        // 仅 join/login 需要 CF 检测与填表
                                        if (!isJoin(u) && !isLogin(u)) return

                                        view.evaluateJavascript(
                                            """
                                            (function(){
                                              const t = (document.body && document.body.innerText || '').toLowerCase();
                                              const title = (document.title || '').toLowerCase();
                                              if (t.includes('cloudflare') || t.includes('checking your browser') ||
                                                  t.includes('正在进行安全验证') || t.includes('just a moment') ||
                                                  t.includes('attention required') || title.includes('just a moment') ||
                                                  title.includes('attention required') ||
                                                  !!document.querySelector('#challenge-form, #cf-challenge-running, .cf-browser-verification, iframe[src*="challenges.cloudflare"]')) {
                                                return 'cf';
                                              }
                                              return 'ok';
                                            })();
                                            """.trimIndent()
                                        ) { cfResult ->
                                            val isCf = cfResult != null && cfResult.contains("cf")
                                            if (isCf) {
                                                status = "正在进行 Cloudflare 安全验证，请在网页中完成，完成后点「已通过CF验证」。不要反复刷新。"
                                                return@evaluateJavascript
                                            }
                                            when {
                                                isJoin(u) -> {
                                                    if (registrationSubmitted) {
                                                        view.evaluateJavascript(
                                                            """
                                                            (function(){
                                                              const err = document.querySelector('.error, .alert, .alert-danger, .text-danger, .invalid-feedback, [class*="error"]');
                                                              const t = (err && err.innerText || document.body.innerText || '').trim();
                                                              const low = t.toLowerCase();
                                                              if (low.includes('already') || low.includes('taken') || low.includes('exists')) return 'exists:' + t.slice(0,100);
                                                              if (err && err.innerText) return 'err:' + err.innerText.trim().slice(0,100);
                                                              return 'stay';
                                                            })();
                                                            """.trimIndent()
                                                        ) { r ->
                                                            val msg = r?.trim('"')?.trim() ?: "stay"
                                                            status = when {
                                                                msg.startsWith("exists:") -> "注册失败：账号可能已存在。可改开登录页。"
                                                                msg.startsWith("err:") -> "注册未成功：${msg.removePrefix("err:")}。请改信息后重试「提交注册」。"
                                                                else -> "仍在注册页（未跳转登录）。请检查用户名/邮箱/密码后再次「提交注册」，或网页上手动点 Register。"
                                                            }
                                                            registrationSubmitted = false
                                                        }
                                                    } else if (cfPassed && !formFilled) {
                                                        status = "CF 已通过，正在填写注册表单…"
                                                        fillRegistration(
                                                            view, username, email, password, confirmPassword,
                                                            autoSubmit = false
                                                        )
                                                        formFilled = true
                                                    } else if (!cfPassed) {
                                                        status = "请先在网页完成 Cloudflare 验证，再点「已通过CF验证」。"
                                                    } else {
                                                        status = "注册表单已填写。确认无误后点「提交注册」。"
                                                    }
                                                }
                                                isLogin(u) -> {
                                                    accountRequested = false
                                                    if (cfPassed && !loginFilled) {
                                                        status = "登录页已就绪，正在填写账号密码…"
                                                        fillLogin(view, username, password, autoSubmit = false)
                                                        loginFilled = true
                                                        registrationSubmitted = true
                                                    } else if (!cfPassed) {
                                                        status = "登录页若出现 CF 验证，请完成后再点「已通过CF验证」。"
                                                    }
                                                }
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
                                        if (k.isNotBlank()) {
                                            discoveredKey = k
                                            keyValid = null
                                            keyValidateMsg = ""
                                            keyValidating = true
                                        }
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
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                        ) {
                            Text("关闭")
                        }
                        if (!cfPassed) {
                            Button(
                                onClick = {
                                    cfPassed = true
                                    formFilled = false
                                    status = "已确认 CF 通过，正在检查页面并填表…"
                                    webView?.evaluateJavascript(
                                        "(function(){ return location.href; })();",
                                        null
                                    )
                                    // 主动触发一次填表：用当前 URL 逻辑
                                    webView?.let { w ->
                                        val url = w.url.orEmpty().lowercase()
                                        if (url.contains("/join")) {
                                            fillRegistration(w, username, email, password, confirmPassword, autoSubmit = false)
                                            formFilled = true
                                            status = "注册表单已填写。确认后点「提交注册」。"
                                        } else if (url.contains("/login")) {
                                            if (!loginFilled) {
                                                fillLogin(w, username, password, autoSubmit = false)
                                                loginFilled = true
                                            }
                                            status = "登录表单已填写。可点页面 Login 或「提交登录」。"
                                        } else {
                                            status = "当前不在注册/登录页。若仍是 CF 页请再等验证完成后再点一次。"
                                            cfPassed = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("已通过CF验证") }
                        } else {
                            Button(
                                onClick = {
                                    val urlNow = webView?.url.orEmpty().lowercase()
                                    when {
                                        // 仍在注册页：提交表单（不重置 cfPassed，失败可重试）
                                        urlNow.contains("/join") ||
                                            (!registrationSubmitted && !loginFilled && !urlNow.contains("/login")) -> {
                                            status = "正在提交注册…"
                                            webView?.let { w ->
                                                // 若页面重绘导致脚本丢失，先重新填表再提交
                                                fillRegistration(
                                                    w, username, email, password, confirmPassword,
                                                    autoSubmit = true,
                                                    forceRefill = true
                                                )
                                            }
                                            registrationSubmitted = true
                                            // 等跳转登录；仍在 /join 时由 onPageFinished 提示错误
                                            status = "已提交注册，等待跳转到登录页（https://wallhaven.cc/login）…"
                                        }
                                        urlNow.contains("/login") ||
                                            (loginFilled && discoveredKey == null && urlNow.contains("login")) -> {
                                            webView?.let { w ->
                                                fillLogin(w, username, password, autoSubmit = true, forceRefill = true)
                                            }
                                            status = "正在提交登录…"
                                        }
                                        discoveredKey == null -> {
                                            webView?.loadUrl(WALLHAVEN_ACCOUNT)
                                            status = "正在打开账户设置并提取 API Key…"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                val urlNow = webView?.url.orEmpty().lowercase()
                                Text(
                                    when {
                                        urlNow.contains("/join") || (!loginFilled && !urlNow.contains("/login")) -> "提交注册"
                                        urlNow.contains("/login") -> "提交登录"
                                        else -> "获取 API Key"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (discoveredKey != null) {
        AlertDialog(
            onDismissRequest = {
                if (!keyValidating) {
                    discoveredKey = null
                    keyValid = null
                }
            },
            title = {
                Text(
                    when {
                        keyValidating -> "正在检测 API Key…"
                        keyValid == true -> "API Key 有效"
                        keyValid == false -> "API Key 无效"
                        else -> "确认保存 API Key？"
                    }
                )
            },
            text = {
                Column {
                    Text(
                        when {
                            keyValidating -> "正在请求 Wallhaven 校验密钥，通过后才能写入本地。"
                            keyValid == true -> "检测通过（$keyValidateMsg）。确认后写入本地配置，不会上传到服务器。"
                            keyValid == false -> "检测未通过（$keyValidateMsg）。不会写入本地，请重新获取或手动填写。"
                            else -> "以下密钥待检测："
                        }
                    )
                    SelectionContainer {
                        Text(discoveredKey.orEmpty(), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = keyValid == true && !keyValidating,
                    onClick = {
                        // ViewModel 内会再次校验，仅有效才落盘
                        onSaveKey(discoveredKey.orEmpty())
                        discoveredKey = null
                        keyValid = null
                        onDismiss()
                    }
                ) { Text(if (keyValid == true) "确认保存" else "不可保存") }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !keyValidating,
                    onClick = {
                        discoveredKey = null
                        keyValid = null
                    }
                ) { Text(if (keyValid == false) "关闭" else "不保存") }
            }
        )
    }
}



/** 清空 wallhaven.cc（含 /、/login、/join）的 Cookie 与 WebView 站点数据 */
private fun clearWallhavenSiteData(web: WebView?) {
    val cm = CookieManager.getInstance()
    cm.setAcceptCookie(true)
    val hosts = listOf(
        "https://wallhaven.cc/",
        "https://wallhaven.cc/login",
        "https://wallhaven.cc/join",
        "https://www.wallhaven.cc/",
        "https://www.wallhaven.cc/login",
        "https://www.wallhaven.cc/join"
    )
    for (url in hosts) {
        val raw = cm.getCookie(url) ?: continue
        for (part in raw.split(";")) {
            val name = part.substringBefore("=").trim()
            if (name.isEmpty()) continue
            // 过期掉各路径下的同名 Cookie
            cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=wallhaven.cc")
            cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.wallhaven.cc")
            cm.setCookie(url, "$name=; Max-Age=0; Path=/")
        }
    }
    // 再整表清掉 WebView Cookie，避免残留会话
    cm.removeAllCookies(null)
    cm.removeSessionCookies(null)
    cm.flush()
    try {
        WebStorage.getInstance().deleteAllData()
    } catch (_: Exception) {
    }
    web?.apply {
        clearCache(true)
        clearFormData()
        clearHistory()
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun fillRegistration(
    web: WebView,
    username: String,
    email: String,
    password: String,
    confirmPassword: String,
    autoSubmit: Boolean = false,
    forceRefill: Boolean = false
) {
    val usernameJson = JSONObject.quote(username)
    val emailJson = JSONObject.quote(email)
    val passwordJson = JSONObject.quote(password)
    val confirmJson = JSONObject.quote(confirmPassword)
    web.evaluateJavascript(
        """
        (function() {
          window.__WH_AUTO_SUBMIT = $autoSubmit;
          const forceRefill = $forceRefill;
          const username = $usernameJson;
          const email = $emailJson;
          const password = $passwordJson;
          const confirmPassword = $confirmJson;
          // 已在登录页则不要再提交注册
          if (/\/login/i.test(location.pathname + location.href)) {
            window.WallhavenBridge.status('当前已是登录页，请点「提交登录」。');
            return;
          }
          const inputs = Array.from(document.querySelectorAll('input'));
          if (inputs.length === 0) {
            window.WallhavenBridge.status('页面尚无表单（可能仍在 CF 验证），请完成后再点「已通过CF验证」。');
            return;
          }
          if (window.WallhavenAutofilledReg && !forceRefill && !window.__WH_AUTO_SUBMIT) return;
          const usernameInput = inputs.find(x => {
            const meta = (x.name + ' ' + x.id + ' ' + x.placeholder + ' ' + (x.getAttribute('autocomplete') || '')).toLowerCase();
            return x.type === 'text' && /user|username|nick|name/.test(meta) && !/email|mail|pass/.test(meta);
          }) || inputs.find(x => x.type === 'text' && !/email|mail|pass|captcha|code/.test((x.name + ' ' + x.id + ' ' + x.placeholder).toLowerCase()));
          const emailInput = inputs.find(x => x.type === 'email' || /email|mail/i.test(x.name + ' ' + x.id + ' ' + x.placeholder));
          const passwordInputs = inputs.filter(x => x.type === 'password');
          if (!emailInput && passwordInputs.length === 0) {
            window.WallhavenBridge.status('未识别到注册表单，若仍在 CF 页请完成验证后再点「已通过CF验证」。');
            return;
          }
          window.WallhavenAutofilledReg = true;
          const passwordInput = passwordInputs.find(x => /confirm|repeat|again|verify/i.test(x.name + ' ' + x.id + ' ' + x.placeholder) === false) || passwordInputs[0];
          const confirmInput = passwordInputs.find(x => /confirm|repeat|again|verify/i.test(x.name + ' ' + x.id + ' ' + x.placeholder)) || passwordInputs[1];
          function put(input, value) {
            if (!input) return;
            input.focus();
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            setter.call(input, value);
            input.dispatchEvent(new Event('input', {bubbles:true}));
            input.dispatchEvent(new Event('change', {bubbles:true}));
            input.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true}));
            input.blur();
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
          // 页面校验错误提示
          const err = document.querySelector('.error, .alert-danger, .text-danger, .invalid-feedback, [class*="error"]');
          if (err && (err.innerText || '').trim().length > 0 && window.__WH_AUTO_SUBMIT) {
            window.WallhavenBridge.status('注册未通过：' + (err.innerText || '').trim().slice(0, 120));
          }
          function inTopNav(el) {
            if (!el || !el.closest) return false;
            return !!el.closest('header, nav, .navbar, .topbar, #header, .header, .site-header, .main-nav');
          }
          function findRegisterSubmit() {
            // 账号字段所在表单内的按钮，避开顶部导航 Register
            const form = (passwordInput && passwordInput.form)
              || (emailInput && emailInput.form)
              || (usernameInput && usernameInput.form)
              || document.querySelector('form');
            let list = [];
            if (form) {
              list = Array.from(form.querySelectorAll('button, input[type="submit"], a.button'));
            }
            if (!list.length) {
              const ver = document.querySelector('input[placeholder*="erif" i], input[name*="captcha" i], input[id*="captcha" i], input[name*="code" i]');
              const box = ver && (ver.closest('form') || ver.closest('div'));
              if (box) list = Array.from(box.querySelectorAll('button, input[type="submit"]'));
            }
            list = list.filter(b => !inTopNav(b));
            // 优先：带对号的 Register
            let btn = list.find(b => /[\u2713\u2714\u221A]/.test(b.textContent || '') && /register|注册/i.test(b.textContent || ''));
            if (!btn) btn = list.find(b => b.type === 'submit' && /register|sign\s*up|注册/i.test((b.textContent || b.value || '')));
            if (!btn) btn = list.find(b => /register|sign\s*up|create\s*account|注册/i.test((b.textContent || b.value || '').trim()));
            if (!btn) btn = list.find(b => b.type === 'submit');
            if (!btn && form) btn = form.querySelector('button[type="submit"], input[type="submit"]');
            return btn || null;
          }
          window.WallhavenSubmit = function() {
            put(usernameInput, username);
            put(emailInput, email);
            put(passwordInput, password);
            put(confirmInput, confirmPassword);
            const form = (emailInput && emailInput.form) || (passwordInput && passwordInput.form) || document.querySelector('form');
            const submit = findRegisterSubmit();
            if (submit) {
              try { submit.scrollIntoView({ block: 'center' }); } catch (e) {}
              submit.focus();
              submit.click();
              window.WallhavenBridge.status('已点击表单内 Register，等待跳转登录页…');
              return;
            }
            if (form) {
              if (typeof form.requestSubmit === 'function') form.requestSubmit();
              else form.submit();
              window.WallhavenBridge.status('已提交注册表单，等待跳转登录页…');
              return;
            }
            window.WallhavenBridge.status('未找到表单内 Register，请手动点击带对号的 Register。');
          };
          const captchaInput = document.querySelector('input[placeholder*="erif" i], input[name*="captcha" i], input[id*="captcha" i], input[name*="code" i], input[placeholder*="Verification" i]');
          const hasVerification = !!captchaInput
            || !!document.querySelector('img[src*="captcha"], iframe[src*="captcha"], iframe[src*="turnstile"], iframe[src*="recaptcha"]')
            || body.includes('captcha') || body.includes('verification') || body.includes('turnstile');
          if (hasVerification && !window.__WH_AUTO_SUBMIT) {
            window.WallhavenBridge.status('表单已填好。请先填写验证码 Verification，再点「提交注册」（会点表单里带对号的 Register）。');
            return;
          }
          if (window.__WH_AUTO_SUBMIT) {
            if (captchaInput && !(captchaInput.value || '').trim()) {
              window.WallhavenBridge.status('请先在网页填写验证码 Verification，再点「提交注册」。');
              return;
            }
            window.WallhavenBridge.status('正在点击表单内 Register…');
            setTimeout(function() { window.WallhavenSubmit(); }, 300);
          } else {
            window.WallhavenBridge.status('注册表单已填写。填好验证码后点「提交注册」。');
          }
        })();
        """.trimIndent(),
        null
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun fillLogin(
    web: WebView,
    username: String,
    password: String,
    autoSubmit: Boolean = false,
    forceRefill: Boolean = false
) {
    val usernameJson = JSONObject.quote(username)
    val passwordJson = JSONObject.quote(password)
    web.evaluateJavascript(
        """
        (function() {
          window.__WH_AUTO_LOGIN = $autoSubmit;
          const forceRefill = $forceRefill;
          const username = $usernameJson;
          const password = $passwordJson;
          if (!/\/login/i.test(location.pathname + location.href) && !document.querySelector('input[type="password"]')) {
            window.WallhavenBridge.status('当前不是登录页。');
            return;
          }
          const forms = Array.from(document.querySelectorAll('form'));
          // 优先含密码框的 form（真正的登录表单）
          const form = forms.find(f => f.querySelector('input[type="password"]')) || forms[0] || null;
          const scope = form || document;
          const inputs = Array.from(scope.querySelectorAll('input'));
          if (!inputs.length) {
            window.WallhavenBridge.status('登录页尚无表单，若在 CF 验证请完成后再试。');
            return;
          }
          const passInput = inputs.find(x => x.type === 'password');
          // 用户名：密码框前面的 text/email，或 name 含 user
          let userInput = inputs.find(x => {
            const meta = ((x.name||'') + ' ' + (x.id||'') + ' ' + (x.placeholder||'') + ' ' + (x.getAttribute('autocomplete')||'')).toLowerCase();
            return x.type !== 'password' && x.type !== 'hidden' && /user|username|login|email|mail|name|account/.test(meta);
          });
          if (!userInput && passInput) {
            // 同 form 内 password 之前最后一个可见文本框
            const all = Array.from((passInput.form || scope).querySelectorAll('input'));
            const idx = all.indexOf(passInput);
            for (let i = idx - 1; i >= 0; i--) {
              const x = all[i];
              if (x.type === 'hidden' || x.type === 'password' || x.type === 'checkbox' || x.type === 'submit') continue;
              userInput = x;
              break;
            }
          }
          if (!userInput) {
            userInput = inputs.find(x => x.type === 'text' || x.type === 'email' || x.type === '');
          }
          function put(input, value) {
            if (!input) return;
            try { input.focus(); } catch (e) {}
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            setter.call(input, value);
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
            input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
            try { input.blur(); } catch (e) {}
          }
          if (!forceRefill && window.WallhavenAutofilledLogin && userInput && (userInput.value || '').trim() && passInput && (passInput.value || '').trim()) {
            // 已填过且两边都有值，仍重新绑定提交函数
          } else {
            put(userInput, username);
            put(passInput, password);
            // 再写一次用户名，防止被后续脚本清掉
            setTimeout(function() { put(userInput, username); put(passInput, password); }, 50);
            window.WallhavenAutofilledLogin = true;
          }
          function inTopNav(el) {
            if (!el || !el.closest) return false;
            return !!el.closest('header, nav, .navbar, .topbar, #header, .header, .site-header, .main-nav');
          }
          function findLoginSubmit() {
            let list = [];
            if (form) list = Array.from(form.querySelectorAll('button, input[type="submit"]'));
            if (!list.length && passInput && passInput.form) {
              list = Array.from(passInput.form.querySelectorAll('button, input[type="submit"]'));
            }
            if (!list.length) {
              list = Array.from(document.querySelectorAll('button, input[type="submit"]'));
            }
            list = list.filter(b => !inTopNav(b));
            // 优先表单内 → Login / Login
            let btn = list.find(b => /login|登录|sign\s*in/i.test((b.textContent || b.value || '')) && b.type === 'submit');
            if (!btn) btn = list.find(b => /login|登录|sign\s*in/i.test((b.textContent || b.value || '')));
            if (!btn) btn = list.find(b => b.type === 'submit');
            if (!btn && form) btn = form.querySelector('button[type="submit"], input[type="submit"]');
            return btn || null;
          }
          window.WallhavenLoginSubmit = function() {
            put(userInput, username);
            put(passInput, password);
            const btn = findLoginSubmit();
            if (btn) {
              try { btn.scrollIntoView({ block: 'center' }); } catch (e) {}
              btn.focus();
              btn.click();
              window.WallhavenBridge.status('已点击表单内 Login，等待进入账户…');
              return;
            }
            const f = (passInput && passInput.form) || form;
            if (f) {
              if (typeof f.requestSubmit === 'function') f.requestSubmit();
              else f.submit();
              window.WallhavenBridge.status('已提交登录表单…');
              return;
            }
            window.WallhavenBridge.status('未找到表单内 Login 按钮，请手动点击 → Login。');
          };
          const uOk = userInput && (userInput.value || '').trim();
          const pOk = passInput && (passInput.value || '').trim();
          if (!uOk) {
            window.WallhavenBridge.status('用户名未填上，请再点一次「已通过CF验证」或「提交登录」。');
          }
          if (window.__WH_AUTO_LOGIN) {
            window.WallhavenBridge.status('正在点击表单内 Login…');
            setTimeout(function() { window.WallhavenLoginSubmit(); }, 300);
          } else if (uOk && pOk) {
            window.WallhavenBridge.status('登录表单已填写（用户名+密码）。请点「提交登录」。');
          }
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
          // Wallhaven 账户页 API Key：固定 32 位字母数字，出现在「API Key」区块 input 中
          function isKey(s) {
            if (!s) return false;
            s = ('' + s).trim();
            if (s.length !== 32) return false;
            if (!/^[A-Za-z0-9]+$/.test(s)) return false;
            if (!/[A-Za-z]/.test(s) || !/[0-9]/.test(s)) return false;
            if (/@|csrf|session|token|password|email|http/i.test(s)) return false;
            return true;
          }
          function pickFromInputs(root, bonusList) {
            const list = [];
            (root || document).querySelectorAll('input').forEach(inp => {
              const v = (inp.value || inp.getAttribute('value') || '').trim();
              if (isKey(v)) list.push({ v: v, bonus: 0, el: inp });
            });
            return list;
          }

          let best = null;
          let bestRank = -1;
          function consider(v, rank) {
            if (!isKey(v)) return;
            if (rank > bestRank) { bestRank = rank; best = v.trim(); }
          }

          // 1) 精确定位：文案恰好为 "API Key" 的标题，在其后 sibling / 父级内找 input
          const nodes = Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,label,legend,div,span,p,strong,dt'));
          for (const el of nodes) {
            const t = (el.textContent || '').replace(/\s+/g, ' ').trim();
            if (t !== 'API Key' && t !== 'Api Key' && t !== 'API key') continue;
            // 标题元素自身很短
            if ((el.textContent || '').length > 24) continue;

            // 下一个兄弟子树
            let n = el.nextElementSibling;
            for (let step = 0; step < 6 && n; step++, n = n.nextElementSibling) {
              n.querySelectorAll('input').forEach(inp => {
                consider(inp.value || inp.getAttribute('value') || '', 100 - step);
              });
              if (n.tagName === 'INPUT') consider(n.value || n.getAttribute('value') || '', 100 - step);
            }
            // 父级容器（账户设置卡片）
            let p = el.parentElement;
            for (let depth = 0; depth < 5 && p; depth++, p = p.parentElement) {
              p.querySelectorAll('input').forEach(inp => {
                // 跳过 email / password
                const ty = (inp.type || '').toLowerCase();
                if (ty === 'password' || ty === 'email') return;
                const meta = ((inp.name||'')+' '+(inp.id||'')+' '+(inp.placeholder||'')+' '+(inp.autocomplete||'')).toLowerCase();
                if (/email|pass|mail|user/.test(meta)) return;
                consider(inp.value || inp.getAttribute('value') || '', 80 - depth);
              });
              // 找到 Regenerate 按钮所在块，优先其前方 input
              const regen = Array.from(p.querySelectorAll('button,a,input[type="submit"]'))
                .find(b => /regenerate/i.test(b.textContent || b.value || ''));
              if (regen) {
                let prev = regen.previousElementSibling;
                for (let i = 0; i < 5 && prev; i++, prev = prev.previousElementSibling) {
                  if (prev.tagName === 'INPUT') consider(prev.value || prev.getAttribute('value') || '', 120);
                  prev.querySelectorAll && prev.querySelectorAll('input').forEach(inp => {
                    consider(inp.value || inp.getAttribute('value') || '', 115);
                  });
                }
              }
            }
          }

          // 2) name/id 含 apikey 的 input（仍要求恰好 32 位）
          document.querySelectorAll('input').forEach(inp => {
            const ty = (inp.type || '').toLowerCase();
            if (ty === 'password' || ty === 'email' || ty === 'hidden') return;
            const meta = ((inp.name||'')+' '+(inp.id||'')+' '+(inp.className||'')).toLowerCase();
            if (/api.?key|apikey/.test(meta)) {
              consider(inp.value || inp.getAttribute('value') || '', 60);
            }
          });

          // 3) 整页 input 仅收集「恰好 32 位」；若只有一个则采用
          if (!best) {
            const keys = [];
            document.querySelectorAll('input').forEach(inp => {
              const ty = (inp.type || '').toLowerCase();
              if (ty === 'password' || ty === 'email') return;
              const v = (inp.value || inp.getAttribute('value') || '').trim();
              if (isKey(v) && keys.indexOf(v) < 0) keys.push(v);
            });
            if (keys.length === 1) { best = keys[0]; bestRank = 40; }
            else if (keys.length > 1) {
              // 多个时仍优先带 apikey 属性的；否则不瞎选最长的
              best = keys[0];
              bestRank = 30;
            }
          }

          // 4) 正文中独立的 32 位词（不拼接）
          if (!best) {
            const text = document.body ? (document.body.innerText || '') : '';
            const re = /\b([A-Za-z0-9]{32})\b/g;
            let m;
            const found = [];
            while ((m = re.exec(text)) !== null) {
              if (isKey(m[1]) && found.indexOf(m[1]) < 0) found.push(m[1]);
            }
            // 排除整段被拼进邮箱行的情况已由 isKey 处理
            if (found.length === 1) best = found[0];
            else if (found.length > 1) {
              // 取出现在 "API Key" 文案之后的第一个
              const idx = text.toLowerCase().indexOf('api key');
              if (idx >= 0) {
                const after = text.slice(idx);
                for (const k of found) {
                  if (after.indexOf(k) >= 0) { best = k; break; }
                }
              }
              if (!best) best = found[0];
            }
          }

          if (best && isKey(best)) {
            window.WallhavenBridge.keyFound(best);
            window.WallhavenBridge.status('已提取 API Key（32 位）：' + best.slice(0, 6) + '…');
          } else {
            window.WallhavenBridge.status('未准确找到 32 位 API Key。请确认账户设置页「API Key」输入框有值，或手动复制。');
          }
        })();
        """.trimIndent(),
        null
    )
}
