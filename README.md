# 镜花水月（JHSY）

原生 Android 自动换壁纸应用。

从 **Wallhaven** 拉取壁纸，支持关键词 / 跃迁模式、多密钥轮换、网络与本地双兜底、桌面与锁屏隔离、省电休眠等。

| 项目 | 值 |
|------|-----|
| 应用名 | 镜花水月 |
| 包名 | `com.kers.killove.jhsy` |
| 正式版 | **v1.01**（versionCode `101`） |
| 最低系统 | Android 8.0（API 26） |
| 目标 SDK | 35 |

---

## 功能概览

### 更换与调度

- 立即更换 / 按间隔自动更换（5～180 分钟）
- WorkManager 后台调度；可选强制前台服务
- 息屏跳过；亮屏后可恢复判断
- **省电模式**：电量低于设定阈值（5～50%）时休眠；**充电时忽略**；电量恢复后继续
- 开机广播恢复任务

### 壁纸来源与兜底链

```
强制本地？
  → 本地目录随机选图
否则
  → Wallhaven（关键词 / 跃迁词 + 多 Key 轮换）
  → 失败且开启网络兜底 → 自定义兜底 API（可多行多个，依次尝试）
  → 仍失败且开启本地兜底 → 本地目录
```

- **Wallhaven**：纯度、类别（真人 / 动漫 / 轮换）、分辨率（设备自适应 / 1.5K / 自定义）
- **方向过滤**（三选一）：无过滤 / 横屏过滤（仅竖屏）/ 竖屏过滤（仅横屏）
- **多 API Key**：每行一个，请求失败自动轮换
- **兜底 API**：支持 `{width}` `{height}`；响应可为直接图片、一行 URL 或含 path/url/image 的 JSON；多行多个 URL
- **本地兜底**：默认 `files/local_fallback/`，可填公共目录绝对路径

### 关键词与跃迁

- 本地多行关键词；可选远程 txt 导入
- **跃迁模式**：Wallhaven 成功后，用该图标签**覆盖**写入跃迁列表（与正常关键词分离）
- 跃迁开启且列表非空时，搜索从跃迁列表取词；否则用正常列表
- 首页展示跃迁列表与「下次将用」
- **关键词翻译**（仅展示 / 日志，不改搜索词）：谷歌 / 微软 / 腾讯，可配 API 密钥

### 桌面与锁屏

- 目标：仅桌面 / 仅锁屏 / 桌面+锁屏
- **桌面锁屏隔离**：开启后**下载两次、设置两次**（先桌面再锁屏）；可使用**不同关键词**
- **铺满方式**（对齐 Windows）：填充 / 适应 / 拉伸

### 界面与安全

- 半透明主题：遮罩透明度、卡片透明度可调
- 文字颜色预设
- 首页显示设备真实分辨率（设备自适应时）、距下次更换倒计时
- 网络检测：本机网络 · Wallhaven · 各兜底 API 延迟
- **PIN 锁定**：锁定后密钥、关键词、兜底 API、**翻译密钥**均不可见

### 缓存与记录

- 更换记录保留最近 **77** 条（含分辨率、大小、关键词等）
- 应用数据目录超过 **10GB** 时，主动清空 `wallpapers` 下载缓存

---

## 本地兜底目录

- 默认：App 私有目录 `files/local_fallback/`（随应用卸载清除）
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

## 兜底 API 示例

设置中「兜底 API」可多行，例如：

```
https://example.com/random?w={width}&h={height}
https://backup.example.com/pic
```

失败时按顺序尝试下一个。

---

## 环境要求

- Android Studio Ladybug / Koala 或更新（AGP 8.7+）
- JDK 17、Android SDK 35
- 真机或模拟器 API 26+

## 构建

```bash
./gradlew assembleDebug
# 输出 app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# 需自行配置签名；输出 app/build/outputs/apk/release/
```

或 Android Studio：**Build → Build Bundle(s) / APK(s)**。

> 包名已改为 `com.kers.killove.jhsy`，与旧版（如 `com.kers701.wallpaperc`）**不能覆盖安装**，数据不互通。

---

## 版本

| 版本 | 说明 |
|------|------|
| **1.01** | 正式版：镜花水月命名与新包名；省电模式；隔离双关键词；PIN 隐藏翻译密钥；10GB 清缓存；记录 77 条；撤销动态壁纸 |
| 1.5.x | 方向三选一、Windows 风格填充、隔离两次设置、关键词翻译、设备分辨率展示等 |
| 1.3.x～1.4.x | 多兜底 URL、跃迁列表可见、网络探测、主题透明度等 |
| 1.1.0 | 关键词、多密钥、网络/本地兜底 |
| 1.0.0-mvp | 首版 |

---

## GitHub Actions

| 工作流 | 触发 | 产物 |
|--------|------|------|
| `.github/workflows/build-apk.yml` | push / PR 到 main、或手动 Run | Artifacts 中的 debug APK |
| `.github/workflows/release-apk.yml` | 推送标签 `v*`（如 `v1.01`） | GitHub Release 附件 |

```bash
git tag v1.01
git push origin v1.01
```

当前 Actions 产物一般为 **debug 签名**包，便于自测；上架请配置 release 签名与 Secrets。

---

## License 与声明

个人项目。请遵守 [Wallhaven](https://wallhaven.cc/) 服务条款，并注意壁纸图片版权。第三方翻译 API 的密钥与费用由使用者自行承担。
