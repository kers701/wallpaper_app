# wallpaper_app 自然法则开关持久化修复补丁

本补丁修复自然法则开关点击保存后，重新打开设置被重置关闭的问题。

## 原因

`AppSettings` 和设置读取逻辑已经包含 `naturalLawEnabled`，但 `SettingsRepository.save()` 原先遗漏了 `Keys.NATURAL_LAW` 的写入，因此 DataStore 始终保留默认值 `false`。

## 修复

在 DataStore 保存事务中加入：

`p[Keys.NATURAL_LAW] = settings.naturalLawEnabled`

## 验证

- `git diff --check` 通过。
- 已核对自然法则字段的定义、读取、保存和保存时归一化链路。
- 本地 Kotlin 编译未能执行到源码编译阶段：当前沙箱未配置 Android SDK，Gradle 报 `SDK location not found`。该环境问题与本补丁无关。
- 补丁包含当前工作区全部源码改动，保留从仓库根目录开始的完整相对路径。
- 未推送远程仓库。
