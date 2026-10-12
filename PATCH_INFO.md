# wallpaper_app 关键词过滤与 Wallhaven 排序补丁

## 关键词过滤列表

设置 → 关键词 → 关键词过滤列表：

- 每行一个过滤词。
- 支持词组，例如 `red hair`、`watermark`。
- 搜索 Wallhaven 时自动附加排除语法：`-关键词`。
- 词组会使用带引号的排除形式：`-"red hair"`。
- 跃迁标签提取时会忽略过滤列表中的词，避免这些词重新进入跃迁列表。
- 过滤列表会写入 DataStore、配置备份和关键词云同步。

## Wallhaven 排序

基础设置新增排序方式，API 参数使用 `sorting`：

| 界面选项 | API 值 |
|---|---|
| 相关性 | `relevance` |
| 随机 | `random` |
| 浏览量 | `views` |
| 收藏数 | `favorites` |
| 排行榜 | `toplist` |

排序和过滤条件会参与搜索页缓存键，切换后不会复用不匹配的旧分页信息。

## 验证

- `git diff --check` 通过。
- 构建未能在当前沙箱执行：缺少 Android SDK，Gradle 报 `SDK location not found`。
- 未推送远程仓库。
