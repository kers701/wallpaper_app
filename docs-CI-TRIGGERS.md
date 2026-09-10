# CI 触发规则

## Debug（Build APK）

任意 `push` / PR / 手动 → Artifacts `jhsy-debug-apk`

## Release 构建（Build Release APK）

提交说明含：`发布`、`正式`、`release`、`re构建`、`[re]` 等 → 签名 APK → Artifacts `jhsy-release-apk`

## 写入 GitHub Releases 页面

| 条件 | 行为 |
|------|------|
| 提交说明含 **`发布`** | **正式 Release**（非 pre-release）。标签优先从说明里的 `1.2.3` / `v1.2.3` 提取，否则 `v日期-短SHA` |
| 推送 **`v*` 标签** | 正式 Release，附件为 release APK |
| 仅 `re构建` / 手动 workflow | **不**创建 Releases，只上传 Artifacts |

### 示例

```bash
git commit -m "发布 2.0.3"
git push
# → debug + release APK + GitHub Release 标签 v2.0.3

git commit -m "re构建"
git push
# → debug + release APK（仅 Artifacts）

git tag v2.0.4 && git push origin v2.0.4
# → GitHub Release v2.0.4
```
