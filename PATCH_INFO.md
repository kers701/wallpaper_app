# wallpaper_app 自然法则 AI 关键词过滤补丁

本补丁优化自然法则模式的 AI 提示词，并增加代码级结果过滤，避免生成没有检索价值的泛化词。

## 过滤内容

AI 不再推荐以下类型：

- 壁纸格式/方向：`vertical wallpaper`、`wallpaper`、`phone wallpaper`、`desktop wallpaper` 等。
- 泛化媒介或作品形式：`digital illustration`、`illustration`、`artwork`、`character artwork`、`fan art`。
- 泛化审美或背景词：`anime aesthetic`、`aesthetic`、`background`、`portrait`、`4k` 等。
- 艺术家模仿或泛化画风：`xxx style`、`in the style of xxx`、`artist style`。

## 保留方向

提示词要求 AI 优先推荐人物外观、服饰、动作、表情、场景、环境、物件、色彩及具体作品/角色名称等具有画面信息的词。

除提示词外，代码还会对 AI 返回结果执行兜底过滤，防止模型未遵守提示词时污染自然关键词组。

## 验证

- `git diff --check` 通过。
- 未推送远程仓库。
- 补丁包含当前工作区全部源码改动，并保留完整相对路径。
