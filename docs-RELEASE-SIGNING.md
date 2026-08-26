# Release 签名与 CI 打包

密钥**不要**提交到 Git。CI 通过 GitHub Secrets 注入。

## 一、配置 4 个 Secrets

仓库 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

| Name | 内容 |
|------|------|
| `RELEASE_KEYSTORE_BASE64` | 密钥库文件的 Base64（整段一行） |
| `RELEASE_STORE_PASSWORD` | 密钥库密码 |
| `RELEASE_KEY_ALIAS` | 别名（keytool -list 可见） |
| `RELEASE_KEY_PASSWORD` | 密钥密码（常与库密码相同） |

### 生成 Base64（本机，不要发到聊天）

```bash
# Linux / Git Bash
base64 -w0 jhsy-release.keystore > keystore.b64
# macOS
base64 -i jhsy-release.keystore -o keystore.b64

# 查看 alias
keytool -list -keystore jhsy-release.keystore -storetype PKCS12
```

把 `keystore.b64` 全文粘贴到 `RELEASE_KEYSTORE_BASE64`。

## 二、触发构建

1. **Actions** → **Build Release APK** → **Run workflow**
2. 或推送标签：`git tag v2.0.3 && git push origin v2.0.3`

成功后在该次 run 的 **Artifacts** 下载 `jhsy-release-apk`。

## 三、本机打包（可选）

```bash
cp keystore.properties.example keystore.properties
# 编辑路径与密码
./gradlew assembleRelease
```

## 四、包名

- Release：`com.kers.killove.jhsy`
- Debug：`com.kers.killove.jhsy.debug`（签名不同，需卸载后再装）
