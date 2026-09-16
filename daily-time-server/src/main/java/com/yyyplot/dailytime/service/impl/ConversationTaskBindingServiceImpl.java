package com.yyyplot.dailytime.service.impl;

import com.yyyplot.dailytime.entity.ConversationTaskBindingEntity;
import com.yyyplot.dailytime.entity.TaskNotificationTargetEntity;
import com.yyyplot.dailytime.mapper.ConversationTaskBindingMapper;
import com.yyyplot.dailytime.mapper.TaskNotificationTargetMapper;
import com.yyyplot.dailytime.service.ConversationTaskBindingService;
import com.yyyplot.dailytime.workflow.ConversationTaskBoundEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationTaskBindingServiceImpl implements ConversationTaskBindingService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ConversationTaskBindingServiceImpl.class);
    private static final String CACHE_PREFIX = "daily-time:conversation-task:";

    private final ConversationTaskBindingMapper bindingMapper;
    private final TaskNotificationTargetMapper notificationTargetMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration cacheTtl;

    public ConversationTaskBindingServiceImpl(
            ConversationTaskBindingMapper bindingMapper,
            TaskNotificationTargetMapper notificationTargetMapper,
            RedisTemplate<String, Object> redisTemplate,
            ApplicationEventPublisher eventPublisher,
            @Value("${daily-time.conversation-binding-cache-ttl:30d}") Duration cacheTtl) {
        this.bindingMapper = bindingMapper;
        this.notificationTargetMapper = notificationTargetMapper;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
        this.cacheTtl = cacheTtl;
    }

    @Override
    @Transactional
    public void bind(String conversationId, String userId, String taskId) {
        LocalDateTime now = LocalDateTime.now();
        bindingMapper.upsert(conversationId, userId, taskId, now);
        notificationTargetMapper.insertOnce(taskId, conversationId, userId, now);
        writeCache(conversationId, userId, taskId);
        eventPublisher.publishEvent(new ConversationTaskBoundEvent(taskId));
    }

    @Override
    public Optional<String> findLatestTaskId(String conversationId, String userId) {
        String cacheKey = cacheKey(conversationId, userId);
        try {
            Object cachedTaskId = redisTemplate.opsForValue().get(cacheKey);
            if (cachedTaskId != null && !cachedTaskId.toString().isBlank()) {
                return Optional.of(cachedTaskId.toString());
            }
        } catch (RuntimeException cacheError) {
            LOGGER.warn("读取会话任务 Redis 缓存失败，回退 MySQL，conversationId={}", conversationId);
        }

        ConversationTaskBindingEntity binding = bindingMapper.find(conversationId, userId);
        if (binding == null) {
            return Optional.empty();
        }
        writeCache(conversationId, userId, binding.getLatestTaskId());
        return Optional.of(binding.getLatestTaskId());
    }

    @Override
    public List<TaskNotificationTargetEntity> findNotificationTargets(String taskId) {
        return notificationTargetMapper.findByTaskId(taskId);
    }

    private void writeCache(String conversationId, String userId, String taskId) {
        try {
            redisTemplate.opsForValue().set(cacheKey(conversationId, userId), taskId, cacheTtl);
        } catch (RuntimeException cacheError) {
            // MySQL 已持久化关联；Redis 不可用不影响正确性和重启恢复。
            LOGGER.warn("写入会话任务 Redis 缓存失败，已保留 MySQL 关联，conversationId={}", conversationId);
        }
    }

    private String cacheKey(String conversationId, String userId) {
        return CACHE_PREFIX + userId + ":" + conversationId;
    }
}
