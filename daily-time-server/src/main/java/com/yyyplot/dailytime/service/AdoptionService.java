package com.yyyplot.dailytime.service;

import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.security.UserContext;

public interface AdoptionService {
    void adopt(AutomationTaskEntity task, UserContext user);
}
