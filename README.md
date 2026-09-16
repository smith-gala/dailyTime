<div align="center">

# Daily Time · 每日一句

**把一句英语，变成一条值得反复听的短视频。**

飞书发起 · AI 分析 · 影视素材检索 · 竖屏渲染 · 人工审核

`Java 21` · `Spring Boot` · `Spring AI` · `Node.js` · `Python` · `FFmpeg`

[实际账号](#实际账号) · [功能与设计](#功能与设计) · [本地运行](#本地运行) · [项目结构](#项目结构)

</div>

Daily Time 是一个面向英语学习内容创作的短视频自动化项目。随便给它一句英文，系统会分析中文释义与音标，从 PlayPhrase 获取影视片段，合成为统一风格的 **1080 × 1920 竖屏视频**，再通过飞书把成片送回给你审核。

它把选句之后的重复工作串成了一条完整流程：内容分析、素材下载、字幕排版、音频处理、视频渲染和结果通知。你负责内容判断，系统负责把制作过程跑完。

## 实际账号

本项目已用于 **Listening time** 英语学习账号的内容制作，账号已在抖音和快手运营。下面是作者提供的实际账号截图，展示了「每日一句」的成片风格与发布效果。

<table>
  <tr>
    <th align="center">抖音 · Listening time</th>
    <th align="center">快手 · Listening time</th>
  </tr>
  <tr>
    <td align="center"><img src="assets/showcase/douyin.png" width="330" alt="抖音 Listening time 账号主页及每日一句作品"></td>
    <td align="center"><img src="assets/showcase/kuaishou.png" width="330" alt="快手 Listening time 账号主页及每日一句作品"></td>
  </tr>
  <tr>
    <td align="center">抖音号：<code>81990392846</code></td>
    <td align="center">快手号：<code>5699808479</code></td>
  </tr>
</table>

截图中的粉丝、获赞和播放量仅代表截图时的状态。账号发布由人工完成；当前项目实现的是视频制作、飞书交付与审核，不包含抖音或快手自动发布。

## 一句话到一条视频

在接入飞书与 OpenClaw 后，可以这样发起任务：

> 帮我制作一句 “Time will tell.” 的每日一句视频。

系统依次完成：

1. **理解内容**：生成中文翻译、整句 IPA、讲解、两条双语例句及音乐标签。
2. **获取素材**：通过 OpenClaw Browser 的 CDP 接口检索 PlayPhrase，默认下载 5 段素材。
3. **制作成片**：用 Python / FFmpeg 合成竖屏视频，加入英文原句、中文释义、音标和字幕，处理音量与片段衔接。
4. **回传审核**：通过飞书发送视频，等待你明确回复「通过 / 采用」或「取消 / 不采用」。

分析得到的讲解与例句属于内容结果；视频画面按 `config.yaml` 中的模板排版，不会把所有分析字段都堆进画面。

```text
飞书消息
   ↓
OpenClaw → Python 转发脚本 → Java Agent / ToolGateway
                                      ↓
                                创建任务并提交事务
                                      ↓
                            AI 分析 → Node.js 下载
                                      ↓
                               Python / FFmpeg 渲染
                                      ↓
                         待审核状态 + Outbox 通知记录
                                      ↓
                            OpenClaw → 飞书发送成片
                                      ↓
                                用户通过 / 取消
```

## 功能与设计

| 能力 | 实现方式 |
| --- | --- |
| 对话驱动制作 | Spring AI 暴露创建、查询、通过、取消四个领域工具 |
| 统一工具入口 | `ToolGateway` 校验参数、任务归属、状态及用户原始消息中的明确确认 |
| 防止重复任务 | 标准化英文句子，利用活动任务唯一键避免重复创建 |
| 并发与状态控制 | 条件抢占、乐观锁和显式状态机约束任务执行 |
| 异步制作 | 事务提交后派发任务，有界线程池串联分析、下载和渲染 |
| 可靠通知 | Transactional Outbox 将任务状态与通知记录同事务提交，支持发送失败退避重试 |
| 统一视觉风格 | 配置字体、颜色、字幕位置、视频窗口、音量及片尾提示 |
| 多语言分工 | Java 管业务，Node.js 管浏览器素材获取，Python / FFmpeg 管媒体处理 |

任务状态：

```text
QUEUED → ANALYZING → DOWNLOADING → RENDERING → AWAITING_REVIEW
                                                 ├→ ADOPTED
                                                 └→ CANCELED

分析、下载、渲染等执行阶段发生错误 → FAILED
```

运行中的任务只能查询；只有待审核任务可以通过或取消。失败后需创建新任务，当前没有视频断点续作。服务重启时，中断的执行中任务会被标记为失败；已持久化的通知由 Outbox 继续调度。Outbox 的事件去重不等同于外部消息严格只投递一次。

## 本地运行

当前配置以 **Windows 本地运行** 为基础。Java、Python、Node.js、FFmpeg 和 OpenClaw 应能访问同一项目目录；更换机器时需要设置自己的路径和字体。

### 1. 准备环境

| 依赖 | 用途与要求 |
| --- | --- |
| JDK 21、Maven | 构建与运行 Java 服务 |
| Python 3.10+ | 媒体 Worker；项目依赖见 `requirements.txt` |
| Node.js | 素材 Worker；需支持原生 `fetch` 与 `WebSocket`，无需安装 npm 依赖 |
| FFmpeg、ffprobe | 视频与音频处理，加入 `PATH` 或配置 `render.ffmpeg_path` |
| MySQL 8.x、Redis | 任务、句库、通知持久化与会话缓存 |
| OpenClaw、飞书通道 | 消息入口、浏览器 CDP 与成片通知 |
| 兼容 OpenAI 协议的模型服务 | 需要支持项目使用的工具调用和结构化内容分析 |

在项目根目录安装 Python 依赖：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt

node -p "typeof fetch + ' / ' + typeof WebSocket"
ffmpeg -version
ffprobe -version
```

Node.js 的检查应输出 `function / function`。把 `PYTHON_COMMAND` 设置为刚创建的 `.venv\Scripts\python.exe` 的绝对路径。

### 2. 配置 Java 服务

以根目录的 [`.env.example`](.env.example) 为参考，在 **IDEA Run Configuration 或启动 Java 的进程环境** 中填写配置。Spring Boot 不会自动读取本项目的 `.env`，仅复制文件还不能完成配置。

必须设置：

- `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`。
- `REDIS_HOST`、`REDIS_PORT`，以及需要时的 `REDIS_PASSWORD`。
- `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`。
- `DAILY_TIME_PROJECT_ROOT`：你电脑上的项目根目录，**不是** `daily-time-server` 子目录。
- `PYTHON_COMMAND`、`NODE_COMMAND`、`OPENCLAW_COMMAND`：本机可执行程序路径或命令。

数据库需要预先创建。**首次连接新的空库时，显式设置 `FLYWAY_ENABLED=true`**，由 Flyway 执行 `V1` 到 `V3` 的结构迁移。默认配置关闭迁移；已有数据库应先核对迁移历史。仓库中的迁移 SQL 是程序的一部分，已保留。

### 3. 配置字体与背景音乐

编辑 [`config.yaml`](config.yaml)：

- `fonts` 默认引用 Windows 字体；请确认文件存在，或改为自己的字体路径。
- `render.ffmpeg_path` 留空时从 `PATH` 查找 FFmpeg。
- `music.enabled` 默认是 `true`。**首次运行且没有准备音乐时，改为 `false`**，即可生成无背景音乐的视频。

仓库不附带本地音乐文件、影视下载素材或历史成片。若启用背景音乐，在 `data/music/` 放入自己有权使用的音频，并按照 [`app/storage.py`](app/storage.py) 的 `MUSIC_TRACKS` 映射配置：

| 音乐标签 | 当前预期文件名 |
| --- | --- |
| `LOVE_WARM` | `唯一.mp3` |
| `HOPE_MOTIVATION` | `夜空中最亮的星.mp3` |
| `HAPPY_DAILY` | `有何不可.mp3` |
| `SAD_REFLECTION` | `lll.mp3` |
| `NOSTALGIA_LIFE` | `起风了.mp3` |

文件名是当前代码的映射约定，音频内容可使用自行制作或获授权的替代素材。输出和任务目录会在运行中创建。

### 4. 启动与接入

先启动 MySQL 和 Redis，再启动已配置好的 OpenClaw Gateway 与 Browser，最后运行 Java 服务：

```powershell
# 在已安装并配置 OpenClaw 的环境中执行
openclaw gateway restart
openclaw browser start

# 从项目根目录进入 Java 模块
cd daily-time-server
mvn spring-boot:run
```

浏览器 CDP 默认地址为 `http://127.0.0.1:18800`，可以通过 `OPENCLAW_CDP_URL` 覆盖。PlayPhrase 的可访问性、登录状态和可用素材数量会影响下载结果。

OpenClaw 接入使用以下项目文件：

- [`openclaw-skill/daily-time-video/SKILL.md`](openclaw-skill/daily-time-video/SKILL.md)：运行时 Skill 的工具边界与审核规则。
- [`scripts/daily_time_agent.py`](scripts/daily_time_agent.py)：把消息转发到 `POST /api/v1/agent/chat`。

需要在自己的 OpenClaw 环境中配置 Skill 发现、飞书消息入口和转发调用。Skill 文件本身不包含一键安装或完整通道配置。转发进程需要接收可信消息上下文中的 `DAILY_TIME_USER_ID`（飞书 `ou_` 用户 ID）、`DAILY_TIME_CONVERSATION_ID` 和原始 `DAILY_TIME_MESSAGE`。可通过 `DAILY_TIME_AGENT_URL` 覆盖默认 Java 接口地址。

`scripts/daily_time_agent.cmd` 保留了作者的本地启动路径；换机器时请调整该文件，或直接用自己的 Python 执行 `scripts/daily_time_agent.py`。

当前 HTTP 接口依赖受信任调用方传入 `X-User-Id`，没有独立的公网身份认证层。部署时应限制后端入口；若要面向公网提供服务，需要补充认证与可信身份转发。

## 项目结构

```text
daily-time/
├── daily-time-server/       # Java 业务、Agent、状态机、数据库迁移与通知
├── automation/playphrase/   # Node.js CDP 与素材下载
├── app/                    # Python 配置、字幕排版及媒体渲染
├── scripts/                # Java 调用的 Worker 与消息转发入口
├── openclaw-skill/          # OpenClaw 运行时 Skill
├── tests/                  # Python / Node.js 测试
├── assets/showcase/         # README 使用的实际账号截图
├── config.yaml             # 媒体与视觉配置
├── requirements.txt        # Python 依赖
└── .env.example            # 环境变量模板
```

`data/` 用于本地运行数据，已加入忽略规则。个人笔记、IDE 配置、凭据、缓存和构建产物也不会进入源码发布包。

## 验证

在项目根目录执行 Python 与 Node.js 测试：

```powershell
.\.venv\Scripts\python.exe -m pytest tests
node --test tests/playphrase.test.mjs
```

Java 测试：

```powershell
cd daily-time-server
mvn clean test
```

默认 Java 测试排除带 `integration` 标签的 MySQL 集成测试。完整的飞书 → 下载 → 渲染 → 通知验证，还需要真实的模型、数据库、浏览器及飞书配置；单元测试通过不代表外部链路已接通。

---

**Listen more. Understand more.**

如果这个项目对你有帮助，欢迎 Star，也欢迎通过 Issue 交流使用体验和改进建议。
