# wallpaper_app Wallhaven API Key 获取助手补丁

## 功能流程

设置 → API 密钥 → 自动注册并获取 Wallhaven API Key：

1. 输入邮箱和密码。
2. 打开 `https://wallhaven.cc/join`。
3. 自动填写邮箱和密码。
4. 无验证码或额外验证时自动提交注册。
5. 出现验证码/额外验证时暂停，用户在网页中完成后点击提交。
6. 账号创建成功后点击进入 `https://wallhaven.cc/settings/account`。
7. 自动读取 API Key。
8. 先显示完整 Key，用户点击“确认保存”后才写入现有本地 API Key 配置。

## 异常处理

- 已存在账号：提示改用登录流程或直接进入账户设置。
- 邮箱/密码格式错误：显示具体格式错误。
- 找不到注册按钮：提示页面结构可能已变化并停止。
- 找不到 API Key：提示登录状态、额外验证或页面结构问题。
- 注册页脚本设置一次性标记，页面失败刷新不会无限重复提交。
- 原始邮箱和密码不会写入 DataStore、配置备份或 Git。

## 安全边界

- 不绕过验证码、反机器人验证或邮箱验证。
- 不批量注册账号。
- API Key 只有在用户明确点击“确认保存”后才写入本地配置。

## 验证

- `git diff --check` 通过。
- Android 编译未执行成功：当前沙箱缺少 Android SDK（`SDK location not found`）。
- 未推送远程仓库。
