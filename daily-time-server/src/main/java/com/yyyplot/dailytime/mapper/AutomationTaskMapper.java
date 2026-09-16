package com.yyyplot.dailytime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AutomationTaskMapper extends BaseMapper<AutomationTaskEntity> {
    AutomationTaskEntity findByTaskId(
            @Param("taskId")
            String taskId);

    AutomationTaskEntity findByActiveKey(
            @Param("activeKey")
            String activeKey);

    int claimQueuedTask(
            @Param("taskId")
            String taskId,
            @Param("expectedVersion")
            Integer expectedVersion);

    int updateProgress(
            @Param("taskId")
            String taskId,
            @Param("status")
            String status,
            @Param("progress")
            Integer progress,
            @Param("label")
            String label,
            @Param("expectedVersion")
            Integer expectedVersion);

    List<AutomationTaskEntity> findRunningTasks();
}
