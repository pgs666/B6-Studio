# B6 Studio

面向飞智 B6 / B6X 的开源蓝牙控制工具。基于旧版应用行为还原 BLE 协议，使用独立实现。使用原生 Android Views、Google Material Components 的 Material Design 3、Google Material 圆角图标和 Android 12+ 系统动态取色。

## 使用

1. 打开 B6 Studio，允许「附近设备」权限。
2. 点击「寻找散热器」，选择已通电的 Flydigi B6 / B6X。
3. 概览显示冷面温度、热面温度、实际风扇转速、制冷设定和固件版本。
4. 控制页选择「智能温控」或「固定功率」，调整后点击「应用设置」。固定功率不显示目标温度。
5. 「灯效、颜色与亮度」进入独立灯光页。B6X 可选择流光、呼吸、渐变、切换；智能模式还支持变频旋转。B6 支持流光、呼吸、常亮和智能变频旋转。
6. 呼吸、渐变、切换可编辑 2–8 个颜色，支持色相/饱和度/明度、HEX 输入、长按删除和恢复原版默认色。流光和变频旋转使用设备预置颜色，B6 不支持改色。
7. 速度分慢/中/快三档；亮度仅在设备回报有效亮度值时显示。实测 B6X 固件 3.3.3 不接受亮度修改，故隐藏该滑条。
8. 固定功率模式可调整过冷保护；过热保护由设备强制启用。智能模式支持原版专家参数。
9. 「关于 → 通信日志」打开独立 Activity。支持暂停/继续、复制当前显示列表、清空和关闭原始通知记录。切换页面不会创建第二条蓝牙连接。
10. 「关于 → 主题外观」选择跟随系统、浅色或深色。Android 12+ 保留壁纸动态取色。

风扇调节默认 2000–8000 RPM。如果设备已有更高的设定，控件保留该数值。制冷字节范围 0–255；实验命令使用制冷值加 1，因此实验启用时最大制冷设定为 254。温控目标为 5–35°C。

## 开源范围

本仓库只包含 B6 Studio 的独立实现、协议测试和公开文档，不包含原始 APK、反编译 DEX、厂商 SDK、设备日志或 debug 签名文件。项目与飞智无隶属关系；B6/B6X 是其各自所有者的产品名称。代码使用 Apache License 2.0，Google 图标许可见 `GOOGLE-ICONS-LICENSE.txt`。

## 构建

需要 JDK 17、Android SDK 平台 37 和 Build Tools 37.0.0。Gradle Wrapper 已包含。

SDK Manager 中的平台包名为 `platforms;android-37.0`：

```sh
sdkmanager "platforms;android-37.0" "build-tools;37.0.0"
```

```sh
# 设置 JAVA_HOME、PATH、ANDROID_HOME，或创建 local.properties 指定 sdk.dir。
./gradlew :app:assembleDebug :app:lintDebug
./tests/run.sh
```

ARM Linux 上需要可运行的原生 aapt2，可通过 Gradle 参数指定：

```sh
./gradlew -Pandroid.aapt2FromMavenOverride=/absolute/path/to/aapt2 :app:assembleDebug :app:lintDebug
```

应用包名 `dev.b6.studio`，minSdk 26、targetSdk 36。提供的 APK 为本机 debug 签名构建，可与原应用共存。后续覆盖更新需要使用相同签名密钥。

## 实现

- `Protocol.java`：命令编码、参数范围检查和完整状态包解析。
- `StudioApplication.java` / `BleController.java`：应用进程内共享一条 BLE 会话，只持有 Application 上下文。连接不会因打开独立 Activity 或主题重建而断开；进程被系统回收后需要重新连接。
- `LedActivity.java`：按型号和运行模式提供灯效；先读取配置再编辑应用，分包完整后才发布颜色列表。未知/不回报的固件不会使用猜测参数写入。
- `LogStore.java` / `LogsActivity.java`：最多 500 条、65,536 个 UTF-16 字符，单条最多 512 字符；环形淘汰，不自动写磁盘。RecyclerView 增量移除/插入，最多每 250ms 发布一次日志更新，页面不可见时不刷新。暂停只冻结显示，存储仍保持上限。
- 收包队列最多 128 包，每包最多 512 字节，单一主线程任务每批最多处理 32 包，超出时丢弃并记录；命令队列最多 40 条。状态发布合并到最多 10Hz，主页面秒定时器只更新读数时间。HEX 编码不再逐字节调用 String.format，时间格式器复用。
- `MainActivity.java`：MD3 页面、动态取色和官方推荐的边到边 Insets 处理。模式选择和参数修改先保存在草稿中，点击应用后发送。
- `tests/ProtocolTest.java`：20 项基础协议检查。
- `tests/DiagnosticsTest.java`：LED 黄金报文、第二包亮度/校验和、截断/乱序/不匹配分包、随机异常输入及 100,000 条日志压力检查；同时覆盖条数上限、字符上限、单条截断、清空、快照及序号。

边到边适配按照 [Android 官方 Views 文档](https://developer.android.com/develop/ui/views/layout/edge-to-edge) 使用 `WindowCompat.enableEdgeToEdge()`。根布局处理顶部、刘海和侧边安全区，Material `BottomNavigationView` 单独处理底部区域，避免重复 padding。动态颜色通过 `DynamicColors.applyToActivityIfAvailable()` 在页面创建前应用。

## 验证及限制

已在 Android 16 测试设备上安装，并与 B6X（固件 3.3.3）实际连接。实时状态约每秒更新一次；发送保留现有设定的固定功率命令后，设备回报仍为 7240 RPM / 制冷 247。新版已读取 B6X 的流光、呼吸配置及 8 色分包，实际发送后的回报保持相应灯效/周期/颜色；3.3.3 固件的亮度回报始终为 0。独立日志 Activity 中继续收到实时状态，暂停后列表文本保持不变。通过 assembleDebug、lintDebug（0 错误）与独立协议/日志测试。未进行极限功率或长时间设备压力测试。未测试其他实体 B6 硬件或不同厂商的旧安卓系统。

原版 APK 的分析、额外 SDK 命令及完整验证记录见同目录 `protocol-analysis.md`。

图标来自 [Google Material Design Icons](https://github.com/google/material-design-icons)，遵循 Apache License 2.0；第三方库遵循各自许可。新实现不包含原 APK 的 DEX 或原 SDK 二进制。

## 1.1.1 界面更新

移除主页面顶部重复的「连接」按钮，保留顶部连接状态和「概览 → 我的散热器」中的寻找/断开连接入口。
