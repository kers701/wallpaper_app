package com.kers.killove.jhsy.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private const val WALLHAVEN_JOIN = "https://wallhaven.cc/join"
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
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("获取 Wallhaven API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!started) {
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
                        "需填写用户名（3-12字符）、邮箱、密码、确认密码。仅用于当前注册流程，不会保存。注册页若出现验证码或其他验证，助手会暂停等待你处理。",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(360.dp),
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
                                        if (url.contains("/join")) {
                                            fillRegistration(view, username, email, password, confirmPassword)
                                        } else if (url.contains("/settings/account")) {
                                            status = "正在读取账户设置中的 API Key…"
                                            extractApiKey(view)
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
                                        if (key.isNotBlank()) discoveredKey = key.trim()
                                    }
                                }
                                addJavascriptInterface(bridge, "WallhavenBridge")
                                webView = this
                                loadUrl(WALLHAVEN_JOIN)
                            }
                        },
                        update = { webView = it }
                    )
                }
            }
        },
        confirmButton = {
            if (!started) {
                Button(
                    enabled = username.length in 3..12
                        && email.contains("@")
                        && password.isNotEmpty()
                        && confirmPassword == password,
                    onClick = {
                        started = true
                        status = "正在打开注册页并填写表单…"
                    }
                ) { Text("开始注册") }
            } else {
                Button(
                    onClick = {
                        if (!registrationSubmitted) {
                            webView?.evaluateJavascript("window.WallhavenSubmit && window.WallhavenSubmit();", null)
                            registrationSubmitted = true
                            status = "已提交注册请求；请确认页面显示账号创建成功。若失败/账号已存在，请按页面提示处理。"
                        } else {
                            webView?.loadUrl(WALLHAVEN_ACCOUNT)
                            status = "正在打开账户设置…"
                        }
                    }
                ) { Text(if (registrationSubmitted) "账号已创建，获取 API Key" else "提交注册（验证码完成后）") }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (discoveredKey != null) {
        AlertDialog(
            onDismissRequest = { discoveredKey = null },
            title = { Text("确认保存 API Key？") },
            text = {
                Column {
                    Text("以下密钥将写入应用本地配置，不会上传到服务器。请确认这是你刚刚获取的密钥：")
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
          if (window.WallhavenAutofilled) return;
          window.WallhavenAutofilled = true;
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
          const body = document.body.innerText.toLowerCase();
          if (body.includes('already exists') || body.includes('already registered') || body.includes('email is already') || body.includes('username is already')) {
            window.WallhavenBridge.status('账号已存在：请改用登录流程，或在账户设置中直接获取 API Key。');
            return;
          }
          if (body.includes('invalid email') || body.includes('invalid password') || body.includes('invalid username')) {
            window.WallhavenBridge.status('注册失败：用户名/邮箱/密码格式不符合网站要求。');
            return;
          }
          const hasVerification = !!document.querySelector('img[src*="captcha"], input[name*="captcha" i], input[id*="captcha" i]') || body.includes('captcha') || body.includes('verification');
          if (hasVerification) {
            window.WallhavenBridge.status('用户名、邮箱、密码已填写，请在网页中完成验证码/额外验证，再点击 Register；完成后点击“提交注册（验证码完成后）”。');
            window.WallhavenSubmit = function() {
              const submit = inputs.find(x => x.type === 'submit') || document.querySelector('button[type="submit"]');
              if (submit) submit.click();
              else window.WallhavenBridge.status('未找到注册提交按钮，页面结构可能已变化，请手动提交。');
            };
            return;
          }
          const submit = inputs.find(x => x.type === 'submit') || document.querySelector('button[type="submit"]');
          if (submit) submit.click();
          else window.WallhavenBridge.status('未找到注册提交按钮，页面结构可能已变化，请手动提交。');
        })();
        """.trimIndent(),
        null
    )
}


private fun extractApiKey(web: WebView) {
    web.evaluateJavascript(
        """
        (function() {
          const text = document.body.innerText || '';
          const inputs = Array.from(document.querySelectorAll('input')).map(x => x.value || '').join('\\n');
          const all = text + '\\n' + inputs;
          const candidates = all.match(/[A-Za-z0-9_-]{24,128}/g) || [];
          const key = candidates.find(x => /[A-Za-z]/.test(x) && /[0-9]/.test(x)) || candidates[0];
          if (key) window.WallhavenBridge.keyFound(key);
          else window.WallhavenBridge.status('未找到 API Key，可能尚未登录、页面结构已变化或需要额外验证。请检查账户设置页面。');
        })();
        """.trimIndent(),
        null
    )
}
