package com.yyyplot.dailytime.service;

import com.yyyplot.dailytime.entity.TaskNotificationTargetEntity;

import java.util.List;
import java.util.Optional;

public interface ConversationTaskBindingService {
    void bind(String conversationId, String userId, String taskId);

    Optional<String> findLatestTaskId(String conversationId, String userId);

    List<TaskNotificationTargetEntity> findNotificationTargets(String taskId);
}
