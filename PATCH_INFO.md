# wallpaper_app 等待归零系统唤醒修复补丁

本补丁基于 `wallpaper_app` 的 `main` 分支，包含此前壁纸铺满方式、Fit 黑边、过期更换和归零顺延修复，以及本次系统唤醒修复；未推送远程仓库。

## 本次修复

此前归零主要改写了 DataStore、SharedPreferences 和时钟文件，但没有注册真正的系统到点事件；如果 `:svc` 前台服务被系统挂起，时间文件不会自行触发更换。本版在归零时注册 `AlarmManager` 精确唤醒：Android 6+ 使用 `setExactAndAllowWhileIdle`，旧版本使用 `setExact`。

闹钟到点后通过 `ACTION_DUE_CHECK` 唤起独立服务，并调用与自动到期相同的完整 `runForcedChange(TriggerType.Auto)` 流程。正常自动更换成功后会重新注册下一次系统闹钟，前台轮询仍保留作为兜底。归零目标仍为当前时间约一分钟后，避免把状态写到过去造成“已到期但没有事件”的假归零。

## 文件路径

补丁内保留从仓库根目录开始的完整相对路径，可直接覆盖对应文件。

## 验证

已执行 `git diff --check`，确认到期动作、AlarmManager 注册、归零一分钟顺延和自动更换入口均存在，并通过 ZIP 完整性测试。
