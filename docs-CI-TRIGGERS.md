# CI 触发规则

## Debug（`Build APK`）

- `push` 到 `main` / `master`
- `pull_request`
- 手动 **Run workflow**

产物：Artifacts → `jhsy-debug-apk`

## Release（`Build Release APK`）

在以下情况会真正执行打包（否则 job 被跳过）：

1. **提交说明**包含任一关键字（不区分大小写）：
   - `发布`、`正式`
   - `release`
   - `re构建`、`[re]`、`/re`、`re/`（行首 `re `）
2. 推送标签：`v*`（如 `v2.0.3`）
3. 手动 **Run workflow**

产物：Artifacts → `jhsy-release-apk`  
打 `v*` 标签时还会尝试创建 GitHub Release。

### 示例

```bash
git commit -m "修复通知"
git push                    # 仅 debug

git commit -m "发布 2.0.3"
git push                    # debug + release

git commit -m "re构建"
git push                    # debug + release

git tag v2.0.3 && git push origin v2.0.3   # release（+ GitHub Release）
```

需已配置 Secrets：`RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。
