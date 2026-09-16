package com.yyyplot.dailytime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yyyplot.dailytime.entity.ConversationTaskBindingEntity;
import com.yyyplot.dailytime.mapper.ConversationTaskBindingMapper;
import com.yyyplot.dailytime.mapper.TaskNotificationTargetMapper;
import com.yyyplot.dailytime.service.impl.ConversationTaskBindingServiceImpl;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

class ConversationTaskBindingServiceImplTest {
    @SuppressWarnings("unchecked")
    @Test
    void fallsBackToMysqlAndWarmsRedis() {
        ConversationTaskBindingMapper bindingMapper = mock(ConversationTaskBindingMapper.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        String cacheKey = "daily-time:conversation-task:ou_user:oc_chat";
        when(values.get(cacheKey)).thenReturn(null);
        ConversationTaskBindingEntity binding = new ConversationTaskBindingEntity();
        binding.setLatestTaskId("20260904_a1b2c3d4");
        when(bindingMapper.find("oc_chat", "ou_user")).thenReturn(binding);

        ConversationTaskBindingService service =
                new ConversationTaskBindingServiceImpl(
                        bindingMapper,
                        mock(TaskNotificationTargetMapper.class),
                        redisTemplate,
                        mock(ApplicationEventPublisher.class),
                        Duration.ofDays(30));

        Optional<String> taskId = service.findLatestTaskId("oc_chat", "ou_user");

        assertThat(taskId).contains("20260904_a1b2c3d4");
        verify(values).set(cacheKey, "20260904_a1b2c3d4", Duration.ofDays(30));
    }

    @SuppressWarnings("unchecked")
    @Test
    void persistsBindingAndPublishesCommittedSnapshotEvent() {
        ConversationTaskBindingMapper bindingMapper = mock(ConversationTaskBindingMapper.class);
        TaskNotificationTargetMapper notificationTargetMapper =
                mock(TaskNotificationTargetMapper.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        String taskId = "20260904_a1b2c3d4";
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        ConversationTaskBindingService service =
                new ConversationTaskBindingServiceImpl(
                        bindingMapper,
                        notificationTargetMapper,
                        redisTemplate,
                        publisher,
                        Duration.ofDays(30));
        service.bind("oc_chat", "ou_user", taskId);

        verify(bindingMapper)
                .upsert(
                        org.mockito.ArgumentMatchers.eq("oc_chat"),
                        org.mockito.ArgumentMatchers.eq("ou_user"),
                        org.mockito.ArgumentMatchers.eq(taskId),
                        org.mockito.ArgumentMatchers.any());
        verify(notificationTargetMapper)
                .insertOnce(
                        org.mockito.ArgumentMatchers.eq(taskId),
                        org.mockito.ArgumentMatchers.eq("oc_chat"),
                        org.mockito.ArgumentMatchers.eq("ou_user"),
                        org.mockito.ArgumentMatchers.any());
        verify(publisher).publishEvent((Object) org.mockito.ArgumentMatchers.any());
    }
}
