package com.yyyplot.dailytime.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.mapper.AutomationTaskMapper;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

/**
 * 仅由项目开发者配置 MySQL 并明确启用 integration 标签后执行。
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "MYSQL_HOST", matches = ".+")
class MySqlMapperIntegrationTest {
    @Autowired
    private AutomationTaskMapper taskMapper;

    @Test
    void onlyOneWorkerCanClaimByVersion() {
        AutomationTaskEntity task =
                task("it_" + System.nanoTime(), "it sentence " + System.nanoTime());
        taskMapper.insert(task);
        assertThat(taskMapper.claimQueuedTask(task.getTaskId(), 0)).isOne();
        assertThat(taskMapper.claimQueuedTask(task.getTaskId(), 0)).isZero();
    }

    private AutomationTaskEntity task(String id, String normalized) {
        AutomationTaskEntity task = new AutomationTaskEntity();
        task.setTaskId(id);
        task.setSentence("integration");
        task.setNormalizedSentence(normalized);
        task.setActiveKey(normalized);
        task.setRequestedTranslation("");
        task.setStatus(TaskStatus.QUEUED);
        task.setProgressPercent(0);
        task.setProgressLabel("");
        task.setCreatedBy("integration");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setVersion(0);
        return task;
    }
}
