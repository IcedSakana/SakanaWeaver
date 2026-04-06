# SakanaWeaver — AI 编曲软件架构设计

## 1. 系统总览

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (UI Layer)                      │
│  ┌───────────┐ ┌──────────┐ ┌───────────┐ ┌────────────────┐   │
│  │ Piano Roll│ │ Track    │ │ Mixer     │ │ AI Assistant   │   │
│  │ Editor    │ │ Timeline │ │ Console   │ │ Panel          │   │
│  └───────────┘ └──────────┘ └───────────┘ └────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                     API Gateway / BFF Layer                      │
├──────────┬──────────┬───────────┬──────────┬────────────────────┤
│  Project │  Audio   │    AI     │  MIDI    │   Collaboration   │
│  Service │  Engine  │  Engine   │  Engine  │   Service         │
├──────────┴──────────┴───────────┴──────────┴────────────────────┤
│                     Core Domain Layer                            │
│  ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ Music    │ │ Arrange-  │ │ Audio    │ │ AI Model         │  │
│  │ Theory   │ │ ment      │ │ DSP      │ │ Pipeline         │  │
│  └──────────┘ └───────────┘ └──────────┘ └──────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                     Infrastructure Layer                         │
│  ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ Storage  │ │ Model     │ │ Plugin   │ │ Message Queue    │  │
│  │ (DB/File)│ │ Registry  │ │ Host     │ │ (Task Scheduler) │  │
│  └──────────┘ └───────────┘ └──────────┘ └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 模块详细设计

### 2.1 Frontend (UI Layer)

| 组件            | 职责                                           | 技术选型             |
| --------------- | ---------------------------------------------- | -------------------- |
| Piano Roll      | MIDI 音符编辑、速度/力度可视化                  | Canvas / WebGL       |
| Track Timeline  | 多轨道排列、片段拖拽、缩放                      | React + Custom Canvas|
| Mixer Console   | 音量、声像、效果器链路控制                       | Web Audio API        |
| AI Assistant    | 自然语言交互、生成建议预览、一键应用              | React + WebSocket    |

### 2.2 API Gateway / BFF

- 统一鉴权与限流
- 请求路由：REST (项目/资源 CRUD) + WebSocket (实时协作 & AI 流式输出)
- 文件上传/下载代理（音频、MIDI、工程文件）

### 2.3 核心服务

#### 2.3.1 Project Service（工程管理）
```
Project
├── metadata (BPM, 调号, 拍号, 标题)
├── tracks[]
│   ├── Track (instrument, clips[], effects[])
│   └── Clip (type: midi | audio, region, data_ref)
├── arrangement_sections[] (intro, verse, chorus …)
└── version_history[]
```

#### 2.3.2 Audio Engine（音频引擎）
- **实时播放**: 基于 Web Audio API / 本地 PortAudio
- **DSP 管线**: 效果器链 (EQ, Reverb, Compressor, Delay …)
- **音频 I/O**: 录音输入、ASIO/CoreAudio 驱动适配
- **渲染导出**: 离线 Bounce → WAV/MP3/FLAC

#### 2.3.3 MIDI Engine（MIDI 引擎）
- MIDI 消息解析 / 生成 (Note On/Off, CC, Program Change)
- 量化 (Quantize)、人性化 (Humanize)
- 虚拟乐器宿主 (VST/AU Plugin Host via JUCE 或 RtMidi)

#### 2.3.4 AI Engine（AI 编曲引擎） ⭐ 核心

```
┌──────────────────────────────────────────────┐
│                AI Engine                      │
│                                              │
│  ┌─────────────┐    ┌──────────────────────┐ │
│  │ NLP Module  │───▶│  Intent Parser       │ │
│  │ (LLM)      │    │  "加一段副歌鼓点"     │ │
│  └─────────────┘    └──────────┬───────────┘ │
│                                │              │
│                     ┌──────────▼───────────┐ │
│                     │  Arrangement Planner  │ │
│                     │  (结构/段落规划)       │ │
│                     └──────────┬───────────┘ │
│                                │              │
│         ┌──────────────────────┼──────────┐  │
│         ▼                      ▼          ▼  │
│  ┌─────────────┐  ┌───────────────┐ ┌──────┐│
│  │ Melody Gen  │  │ Harmony Gen   │ │ Drum ││
│  │ (Transformer│  │ (Chord-Conditi│ │ Patt ││
│  │  / Diffusion│  │  oned Model)  │ │ Gen  ││
│  └──────┬──────┘  └──────┬────────┘ └──┬───┘│
│         │                │             │     │
│         └────────────────┼─────────────┘     │
│                          ▼                   │
│                 ┌────────────────┐            │
│                 │  Post-Process  │            │
│                 │  (量化/对齐/   │            │
│                 │   人性化/混合) │            │
│                 └────────┬───────┘            │
│                          ▼                   │
│                 ┌────────────────┐            │
│                 │  MIDI / Audio  │            │
│                 │  Output        │            │
│                 └────────────────┘            │
└──────────────────────────────────────────────┘
```

**AI 子模块说明：**

| 模块               | 输入                          | 输出                    | 模型参考                  |
| ------------------ | ----------------------------- | ----------------------- | ------------------------- |
| NLP Intent Parser  | 自然语言 / 参考曲风描述        | 结构化编曲指令           | LLM (GPT / LLaMA)        |
| Arrangement Planner| 指令 + 当前工程上下文          | 段落结构 + 配器方案      | Rule-based + LLM          |
| Melody Generator   | 调性 + 和弦进行 + 节奏约束     | MIDI 旋律序列           | Music Transformer / Rave  |
| Harmony Generator  | 旋律 + 风格标签               | 和弦进行 MIDI           | Chord-conditioned Seq2Seq |
| Drum Pattern Gen   | 风格 + BPM + 段落位置         | 鼓组 MIDI Pattern       | Pattern VAE / Groove MIDI |
| Style Transfer     | 源 MIDI + 目标风格            | 风格迁移后的 MIDI        | CycleGAN / Diffusion      |
| Audio Synthesis    | MIDI + 音色描述               | 波形音频                 | DiffWave / SampleRNN      |

### 2.4 Infrastructure Layer

#### 2.4.1 存储
- **项目数据库**: SQLite (本地) / PostgreSQL (云端)
- **文件存储**: 音频文件 → 本地文件系统 / S3
- **模型权重**: 本地缓存 + 远程 Model Registry

#### 2.4.2 Plugin Host
- VST3 / AU 插件加载与管理
- 沙箱进程隔离，防止插件崩溃影响宿主
- 参数自动化曲线支持

#### 2.4.3 Task Scheduler
- AI 生成任务队列（避免阻塞 UI）
- GPU 资源调度（本地 / 远程推理）
- 进度回调 & 流式结果返回

---

## 3. 数据流

### 3.1 AI 编曲核心流程

```
用户输入 (文字/哼唱/参考曲)
        │
        ▼
  ┌─────────────┐     ┌─────────────────┐
  │ NLP 解析    │────▶│ 编曲意图结构化   │
  └─────────────┘     └────────┬────────┘
                               │
                    ┌──────────▼──────────┐
                    │ 上下文融合           │
                    │ (当前工程 + 乐理规则 │
                    │  + 风格约束)         │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
        旋律生成          和弦生成          鼓点生成
              │                │                │
              └────────────────┼────────────────┘
                               ▼
                    ┌──────────────────────┐
                    │ 后处理 & 人性化       │
                    └──────────┬───────────┘
                               ▼
                    ┌──────────────────────┐
                    │ 预览 (用户试听)       │
                    └──────────┬───────────┘
                          ┌────┴────┐
                          ▼         ▼
                       接受       修改 (反馈循环)
                          │
                          ▼
                    写入工程轨道
```

### 3.2 实时播放数据流

```
Project State ──▶ Scheduler (tick-based)
                       │
           ┌───────────┼───────────┐
           ▼           ▼           ▼
     MIDI Track    MIDI Track   Audio Track
           │           │           │
           ▼           ▼           │
     VSTi Plugin  VSTi Plugin     │
           │           │           │
           ▼           ▼           ▼
        ┌──────────────────────────────┐
        │       Mixer Bus              │
        │  (Volume, Pan, Send, FX)     │
        └──────────────┬───────────────┘
                       ▼
                 Master Output
                       │
                       ▼
                 Audio Driver
```

---

## 4. 技术栈建议

| 层级         | 技术                                                         |
| ------------ | ------------------------------------------------------------ |
| Frontend     | Electron + React + TypeScript + Canvas/WebGL                 |
| 音频引擎     | C++/Rust (JUCE or cpal) ← Node Native Addon (NAPI)          |
| AI 推理      | Python (PyTorch) ← gRPC / HTTP 与主进程通信                  |
| MIDI 处理    | Rust (midly) 或 C++ (RtMidi + JUCE)                         |
| 数据库       | SQLite (本地工程) + Redis (任务队列)                          |
| 插件宿主     | JUCE (VST3/AU/CLAP)                                         |
| 构建/打包    | CMake (Native) + Vite (Frontend) + PyInstaller (AI Module)   |

---

## 5. 目录结构

```
SakanaWeaver/
├── apps/
│   ├── desktop/              # Electron 主进程 + 渲染进程
│   │   ├── main/             # Electron main process
│   │   └── renderer/         # React UI
│   └── server/               # 可选：云端 AI 推理服务
│       ├── api/
│       └── workers/
├── packages/
│   ├── core/                 # 核心领域模型 (TypeScript)
│   │   ├── project/          # 工程数据结构
│   │   ├── music-theory/     # 乐理工具 (音阶/和弦/调性)
│   │   └── midi/             # MIDI 解析与生成
│   ├── audio-engine/         # 音频引擎 (Rust/C++)
│   │   ├── dsp/              # DSP 效果器
│   │   ├── playback/         # 实时播放调度
│   │   ├── plugin-host/      # VST/AU 插件宿主
│   │   └── bindings/         # Node.js Native Bindings
│   ├── ai-engine/            # AI 编曲引擎 (Python)
│   │   ├── models/           # 模型定义
│   │   │   ├── melody/
│   │   │   ├── harmony/
│   │   │   ├── drums/
│   │   │   └── style_transfer/
│   │   ├── inference/        # 推理服务
│   │   ├── training/         # 训练脚本
│   │   ├── nlp/              # 自然语言意图解析
│   │   └── postprocess/      # 后处理 (量化/人性化)
│   └── ui-components/        # 共享 UI 组件库
│       ├── piano-roll/
│       ├── timeline/
│       ├── mixer/
│       └── ai-panel/
├── assets/
│   ├── soundfonts/           # 默认音色库
│   ├── presets/              # AI 风格预设
│   └── templates/            # 工程模板
├── scripts/                  # 构建 & 开发脚本
├── docs/                     # 文档
└── tests/                    # 端到端测试
```

---

## 6. AI 模型训练数据 & Pipeline

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  MIDI 数据集  │    │  音频数据集   │    │  文本标注     │
│  (Lakh, MAESTRO│   │  (MusicCaps,  │    │  (风格/情绪   │
│   GiantMIDI)  │    │   MTG-Jamendo)│    │   /结构描述)  │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                   │
       ▼                   ▼                   ▼
┌──────────────────────────────────────────────────────┐
│                  Data Pipeline                        │
│  解析 → 标准化 → 特征提取 → 对齐 → 增强 → 分片       │
└──────────────────────────┬───────────────────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        旋律模型训练   和弦模型训练   鼓点模型训练
              │            │            │
              ▼            ▼            ▼
        模型评估 (乐理一致性 / 听感评分 / FID)
              │
              ▼
        Model Registry (版本管理 + A/B 测试)
```

---

## 7. 关键设计决策

| 决策点                  | 选项                        | 推荐 & 理由                                           |
| ---------------------- | --------------------------- | ----------------------------------------------------- |
| 桌面框架                | Electron vs Tauri           | Electron — 生态成熟、VST UI 集成方便                    |
| 音频引擎语言            | C++ vs Rust                 | Rust — 内存安全、cpal 生态、与 WASM 互通                |
| AI 推理部署             | 本地 vs 云端 vs 混合         | 混合 — 轻量模型本地跑，大模型按需云端                    |
| AI 与宿主通信           | gRPC vs HTTP vs IPC         | gRPC — 流式支持好、类型安全、性能优                      |
| 项目文件格式            | 自定义二进制 vs JSON/SQLite  | SQLite — 单文件、支持事务、可查询                       |
| 实时协作 (可选)         | CRDT vs OT                  | CRDT (Yjs) — 去中心化、离线支持                        |

---

## 8. MVP 路线图建议

**Phase 1 — 基础 DAW 能力**
- [ ] 工程创建/保存/加载
- [ ] 多轨 MIDI 编辑 (Piano Roll)
- [ ] 基础音频播放引擎
- [ ] 内置简单合成器

**Phase 2 — AI 编曲核心**
- [ ] 和弦进行生成 (给定调性+风格)
- [ ] 鼓点 Pattern 生成
- [ ] 旋律续写/生成
- [ ] 自然语言 → 编曲指令解析

**Phase 3 — 高级功能**
- [ ] 风格迁移
- [ ] VST/AU 插件宿主
- [ ] AI 混音建议
- [ ] 多人实时协作

**Phase 4 — 生态建设**
- [ ] 插件/预设市场
- [ ] 社区分享 & 模板库
- [ ] 移动端配套 App
