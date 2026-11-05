# 敏感数据与加速节点（安全说明）

本文说明两件事：密钥在设备上怎么存、加速节点怎么在不进仓库的前提下打进安装包。

## 1. 密钥存储：AndroidKeyStore 加密

`SettingsRepository` 里以下字段**不再明文写入 DataStore**：

| 字段 | DataStore key |
|---|---|
| Wallhaven API Keys | `api_keys` / 旧版 `api_key` |
| Git 同步令牌 | `git_token` |
| 翻译密钥 | `translate_api_key`、`translate_secret` |
| AI 密钥 | `translate_ai_api_key`、`ai_providers_json`、`ai_models_json` |
| 高德 Key | `amap_api_key` |
| 代理 | `proxy_pass`、`proxy_nodes_json`、`proxy_sub_url` |
| 超级代理订阅 | `super_proxy_sub_url` |
| 网盘备份 | `cloud_backup_user`、`cloud_backup_pass` |
| 可能带 token 的 URL | `fallback_api_url`、`keywords_remote_url`、`accel_nodes_url`、`bg_api_url` |

实现见 `data/prefs/SecretCodec.kt`：

- 密钥由 **AndroidKeyStore** 生成并保管（别名 `miroflweat_secret_v1`，AES-256-GCM），应用只持有密文。
- 密文格式 `enc1:` + Base64(IV ‖ ciphertext ‖ tag)。
- `decrypt()` 对**没有前缀**的值原样返回 —— 老版本存下的明文仍可读，下次保存时自动写成密文，无感迁移。
- 卸载 / 清除数据 → Keystore 密钥销毁 → 旧密文不可用，按「未配置」处理（重新填一次即可）。

同时 `AndroidManifest.xml` 的 `android:allowBackup` 已改为 `false`：应用数据不再进入系统自动备份，避免明文/密文被带出设备。

### 仍然会明文出现的地方

- **配置备份 / Git 云同步**：`ConfigBackup.toJson()` 输出的 JSON 是明文（按你的勾选决定是否包含 API Keys）。这是「导出给人看」的场景，需要明文才能跨设备恢复，所以本次没有改。
- 界面上的输入框、日志：本来就只在应用内。

如果希望备份也脱敏，可以另开改动（导出时打码、恢复时要求重新填写密钥）。

## 2. 加速节点：不进仓库

`socks5` 中转地址以前同时写死在两处，随公开仓库一起发布：

1. `data/remote/BuiltinAccelNodes.kt` 的 `compiled` 列表 → 已清空为 `emptyList()`
2. `app/src/main/assets/accel_nodes.json` → 已从仓库删除，并加入 `.gitignore`

现在节点的来源只有两个：

- `assets/accel_nodes.json`：本地或 CI 注入，**不提交**
- 设置页「加速节点远程列表 URL」

两者都为空时 `BuiltinAccelNodes.all()` 返回空列表，`ProxyHttp` 自动回退直连，不会崩。

### 打包时注入（GitHub Actions）

在仓库 Secrets 里加一条 `ACCEL_NODES_JSON`，内容就是节点数组：

```json
[
  {"name":"hk-1","type":"socks5","host":"1.2.3.4","port":701}
]
```

`build-apk.yml` 与 `release-apk.yml` 都会在 checkout 之后把它写成
`app/src/main/assets/accel_nodes.json` 再构建；没配这个 Secret 就跳过。

### 本地打包

```bash
cp app/src/main/assets/accel_nodes.example.json app/src/main/assets/accel_nodes.json
# 编辑成自己的中转，然后正常 ./gradlew assembleRelease
```

该文件已在 `.gitignore` 里，不会被误提交。
