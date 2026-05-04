# Qwen3 Voice Input for Android

Android 本地语音输入法 — 基于 **Qwen3-ASR-0.6B** + [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)。

按住麦克风按钮说话，松开即识别并输入文字。完全离线，不经过任何服务器。

## 特性

- 🎤 Push-to-talk — 长按麦克风按钮录音
- ⚡ 本地推理 — 所有计算在设备端完成
- 🔒 完全离线 — 无需联网
- 🌐 30+ 语言 — 中文、英文、粤语等
- 📦 Qwen3-ASR-0.6B-int8 — ~941MB 模型，首次启动下载

## 构建

```bash
# 打开 Android Studio 或命令行构建
./gradlew assembleDebug
```

## 模型

模型文件 (~941MB) 在首次启动时自动下载到 app 内部存储：

```
/data/data/com.qwen3.voice/files/models/
├── conv_frontend.onnx     (42 MB)
├── encoder.int8.onnx      (174 MB)
├── decoder.int8.onnx      (721 MB)
└── tokenizer/             (~4 MB)
```

来源: [sherpa-onnx-qwen3-asr-0.6B-int8](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)

## 使用

1. 安装 APK
2. 系统设置 → 语言与输入法 → 启用 "Qwen3 Voice Input"
3. 在任意输入框中切换到本输入法
4. 长按麦克风按钮说话，松开识别

## 架构

```
app/src/main/java/com/qwen3/voice/
├── MainActivity.kt          # 启动页 + 模型下载管理
├── Qwen3VoiceIME.kt         # InputMethodService 输入法核心
├── AsrEngine.kt             # sherpa-onnx ASR 引擎封装
├── ModelDownloader.kt       # 模型下载 + 解压
└── ui/
    └── VoiceKeyboardView.kt # 自定义键盘视图（麦克风按钮）
```

## 技术栈

| 组件 | 技术 |
|------|------|
| ASR 模型 | Qwen3-ASR-0.6B (int8 ONNX) |
| 推理框架 | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) |
| 输入法框架 | Android InputMethodService |
| 语言 | Kotlin |
| 最低版本 | Android 8.0 (API 26) |

## License

MIT
