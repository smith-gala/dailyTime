---
name: daily-time-video
description: 通过 Daily Time Java Agent 创建、查询和审核每日一句视频任务。
---

# Daily Time Video

只使用 Java Agent 暴露的四个领域工具：

- `create_daily_video`：创建新任务。
- `get_task_status`：查询当前状态和进度。
- `approve_task`：仅当用户原始消息明确说“通过”或“采用”时调用。
- `cancel_task`：仅当用户原始消息明确说“取消”或“不采用”时调用。

运行中的任务只能查询，不能取消。只有 `AWAITING_REVIEW` 能通过或取消。失败任务不重试；用户需要再次制作时创建新任务。

不要直接执行下载、FFmpeg、Shell、SQL 或文件操作，不要自行构造用户身份、会话 ID 或确认信息。成片待审核和失败通知由 Java Transactional Outbox 与 OpenClaw 飞书发送链路负责。
