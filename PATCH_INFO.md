# wallpaper_app Wallhaven API Key 获取助手修复补丁

## 本版修复

上一版补丁的打包脚本只收集 `git diff --name-only`，漏掉了新增的未跟踪文件 `WallhavenApiSetupScreen.kt`，导致 CI 编译时报：

- `Unresolved reference 'WallhavenApiSetupDialog'`
- `Unresolved reference 'it'`

本版使用“已修改文件 + 未跟踪文件”的完整清单，已包含该新增源码文件。

## 功能

- 设置页新增“自动注册并获取 Wallhaven API Key”按钮。
- 输入邮箱和密码后打开 `https://wallhaven.cc/join`。
- 自动填写并在无验证码时提交。
- 遇到验证码/额外验证时暂停，由用户完成网页操作。
- 注册成功后进入 `https://wallhaven.cc/settings/account`。
- 读取 API Key 后先展示，用户明确点击“确认保存”才写入本地 API Key 配置。
- 已存在账号、格式错误、找不到按钮或找不到 API Key 时提示具体原因，不无限重试。
- 邮箱和密码不写入 DataStore、备份或 Git。

## 验证

- `git diff --check` 通过。
- 已验证压缩包包含 `app/src/main/java/com/kers/killove/jhsy/ui/screens/WallhavenApiSetupScreen.kt`。
- 未推送远程仓库。
