package com.yyyplot.dailytime.notification;

import com.yyyplot.dailytime.entity.NotificationOutboxEntity;

public interface FeishuNotificationSender {
    void send(NotificationOutboxEntity notification);
}
