# wallpaper_app 加速模式 API Key 注册代理修复补丁

## 修复内容

Wallhaven API 注册助手使用 Android WebView，而原有加速代理只接管 OkHttp 请求，因此注册页、Cloudflare 验证、登录和账户设置页仍然直连。

本补丁新增：

- 引入 AndroidX WebKit `ProxyController`。
- 加速模式开启且已同意隐私声明时，注册 WebView 打开页面前随机选择一个内置加速节点。
- 等待 WebView 代理覆盖完成后再加载 Wallhaven 注册页。
- 注册、Cloudflare 验证、登录、账户设置和 API Key 提取页面统一走该节点。
- 关闭注册助手时清除 WebView 的进程级代理覆盖，避免影响应用其他页面。
- 加速模式关闭、无可用节点或设备不支持 WebView 代理覆盖时自动回退直连，并正常显示注册页面。

## 变更文件

- `app/build.gradle.kts`
- `app/src/main/java/com/kers/killove/jhsy/data/remote/ProxyHttp.kt`
- `app/src/main/java/com/kers/killove/jhsy/ui/screens/WallhavenApiSetupScreen.kt`
- 同时包含当前工作区此前关键词过滤与 Wallhaven 排序改动，便于按完整工程路径应用。

## 验证

- `git diff --check` 通过。
- AndroidX WebKit `1.12.1` 的 `ProxyConfig`、`ProxyController` 和 `WebViewFeature.PROXY_OVERRIDE` API 已核对。
- 当前沙箱缺少 Android SDK，无法完成 Gradle 编译验证。
- 未推送远程仓库。
