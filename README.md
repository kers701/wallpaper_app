# Wallpaperc Android

原生 Android 版自动换壁纸，对应 Termux 脚本仓库 [wallpaperc](https://github.com/kers701/wallpaperc)。

从 **Wallhaven** 拉取壁纸，支持关键词、多密钥、网络/本地双兜底、定时更换桌面/锁屏。

## 功能（1.1.0）

- 立即更换 / 自动定时更换
- 纯度 / 类别（真人·动漫·轮换）/ 目标（桌面·锁屏·双）
- 分辨率：设备自适应 / 1.5K / 自定义
- **关键词列表**：本地多行编辑；可从远程 txt URL 导入（覆盖或合并）
- **多 API Key**：每行一个，失败自动轮换
- **网络兜底**：Wallhaven 失败时请求自定义兜底 API（支持 `{width}` / `{height}`）
- **本地兜底**：无网或强制本地模式时从指定目录随机选图
- 息屏跳过、WorkManager / 前台服务
- Room 去重、开机恢复

## 更换流程（简要）

```
强制本地 或 无网？
  → 本地目录随机选图
否则
  → Wallhaven（可选关键词 + 多 Key 轮换）
  → 失败且开启网络兜底 → 自定义兜底 API
  → 仍失败且开启本地兜底 → 本地目录
```

## 本地兜底目录

- 默认：App 私有目录 `files/local_fallback/`（应用内路径，随应用卸载清除）
- 可在设置中填写绝对路径，例如：`/storage/emulated/0/Pictures/Wallpapers`
- 支持扩展名：`jpg` / `jpeg` / `png` / `webp` / `bmp`

使用公共目录时需授予「读取照片」权限。

## 远程关键词 txt 格式

```
# 注释行
landscape
anime girl
cyberpunk
```

每行一个词，`#` 开头为注释。

## 环境要求

- Android Studio Ladybug / Koala 或更新（AGP 8.7+）
- JDK 17、Android SDK 35
- 真机或模拟器 API 26+

## 打包

```bash
cd wallpaperc-android
./gradlew assembleDebug
# 输出 app/build/outputs/apk/debug/app-debug.apk
```

或 Android Studio：**Build → Build APK(s)**。

## 版本

- `1.1.0` — 关键词、多密钥、网络/本地兜底
- `1.0.0-mvp` — 首版

## License

个人项目，请注意 Wallhaven 服务条款与图片版权。

## GitHub Actions 自动构建 APK

仓库已包含工作流：

| 文件 | 触发 | 产物 |
|------|------|------|
| `.github/workflows/build-apk.yml` | push / PR 到 main、或手动 Run | Actions 里下载 `wallpaperc-debug-apk` |
| `.github/workflows/release-apk.yml` | 推送标签 `v*`（如 `v1.1.0`） | GitHub Release 附件 |

### 使用步骤

1. 将本项目推到 GitHub（仓库根目录需能直接看到 `gradlew`、`app/`）
2. 打开仓库 **Actions** 页，等待 **Build APK** 跑完（绿色）
3. 点进该次运行 → **Artifacts** → 下载 `wallpaperc-debug-apk` → 解压得到 `.apk`
4. 也可在 Actions 里点 **Run workflow** 手动构建

发布示例：

```bash
git tag v1.1.0
git push origin v1.1.0
```

> 当前产物为 **debug 签名**包，可安装自测；上架需自行配置 release 签名与 Secrets。

