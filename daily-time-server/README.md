# daily-time-server

Java 21 / Spring Boot 后端，负责飞书 Agent 工具、视频任务状态机、异步 Worker 编排、MySQL/Redis 持久化和 Transactional Outbox 通知。

## 运行链路

1. `create_daily_video` 创建 `QUEUED` 任务，`active_key` 唯一索引保证活动任务幂等。
2. 创建事务提交后，`TaskDispatchListener` 把任务提交给有界线程池。
3. Worker 以 `QUEUED + version` 条件抢占后顺序执行分析、固定五条素材下载和视频渲染。
4. 每个步骤完成后用短事务更新状态和当前进度；成片只保存 `video_file`。
5. `AWAITING_REVIEW` 与 `FAILED` 同事务写入 Outbox，Dispatcher 通过 OpenClaw 发送飞书通知。
6. 用户仅能在 `AWAITING_REVIEW` 选择通过或取消。

## 配置

主要配置位于 `src/main/resources/application.yml`。数据库、Redis、LLM 和 OpenClaw 凭据全部通过环境变量提供。固定素材数量由 `daily-time.worker.clip-count` 配置，默认值为 5。


## 测试

```powershell
mvn clean test
```

默认测试排除 `integration` 标签。`MySqlMapperIntegrationTest` 需要外部 MySQL 环境变量并显式启用集成测试。
