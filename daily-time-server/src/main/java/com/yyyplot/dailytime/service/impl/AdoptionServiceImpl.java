package com.yyyplot.dailytime.service.impl;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.entity.AdoptionEntity;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.entity.SentenceEntity;
import com.yyyplot.dailytime.enums.SentenceStatus;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.mapper.AdoptionMapper;
import com.yyyplot.dailytime.mapper.AutomationTaskMapper;
import com.yyyplot.dailytime.mapper.SentenceMapper;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.AdoptionService;
import com.yyyplot.dailytime.workflow.TaskStateMachine;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AdoptionServiceImpl implements AdoptionService {
    private final AdoptionMapper adoptionMapper;
    private final SentenceMapper sentenceMapper;
    private final AutomationTaskMapper taskMapper;
    private final TaskStateMachine stateMachine;

    public AdoptionServiceImpl(
            AdoptionMapper adoptionMapper,
            SentenceMapper sentenceMapper,
            AutomationTaskMapper taskMapper,
            TaskStateMachine stateMachine) {
        this.adoptionMapper = adoptionMapper;
        this.sentenceMapper = sentenceMapper;
        this.taskMapper = taskMapper;
        this.stateMachine = stateMachine;
    }

    @Override
    @Transactional
    public void adopt(AutomationTaskEntity task, UserContext user) {
        stateMachine.requireTransition(task.getStatus(), TaskStatus.ADOPTED);
        String videoFile = task.getVideoFile();
        if (videoFile == null || videoFile.isBlank()) {
            throw new BusinessException(ErrorCode.TASK_STATE_CONFLICT, "成片元数据不存在，不能审核通过");
        }

        LocalDateTime now = LocalDateTime.now();
        insertAdoption(task, user, videoFile, now);
        markSentenceAdopted(task.getSentenceId(), now);
        markTaskAdopted(task, now);
    }

    private void insertAdoption(
            AutomationTaskEntity task,
            UserContext user,
            String videoFile,
            LocalDateTime adoptedAt) {
        AdoptionEntity adoption = new AdoptionEntity();
        UUID randomRecordId = UUID.randomUUID();
        String recordIdWithHyphens = randomRecordId.toString();
        String normalizedRecordId = recordIdWithHyphens.replace("-", "");
        String recordId = "adp_" + normalizedRecordId.substring(0, 16);
        adoption.setRecordId(recordId);
        adoption.setSentenceId(task.getSentenceId());
        adoption.setTaskId(task.getTaskId());
        adoption.setVideoFile(videoFile);
        adoption.setOrientation("PORTRAIT");
        adoption.setAdoptedBy(user.userId());
        adoption.setAdoptedAt(adoptedAt);
        try {
            adoptionMapper.insert(adoption);
        } catch (DuplicateKeyException duplicateKeyException) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_ADOPTED, "该句子已经采纳");
        }
    }

    private void markSentenceAdopted(Long sentenceId, LocalDateTime adoptedAt) {
        SentenceEntity sentence = sentenceMapper.selectById(sentenceId);
        if (sentence == null) {
            throw new BusinessException(ErrorCode.SENTENCE_NOT_FOUND, "任务关联句子不存在");
        }
        sentence.setStatus(SentenceStatus.ADOPTED);
        sentence.setAdoptedAt(adoptedAt);
        sentence.setUpdatedAt(adoptedAt);
        if (sentenceMapper.updateById(sentence) != 1) {
            throw new BusinessException(ErrorCode.TASK_STATE_CONFLICT, "句库状态版本冲突");
        }
    }

    private void markTaskAdopted(AutomationTaskEntity task, LocalDateTime adoptedAt) {
        // 审核事务同时提交 adoption、句库状态和任务终态；任一步失败都会整体回滚。
        task.setStatus(TaskStatus.ADOPTED);
        task.setProgressPercent(100);
        task.setProgressLabel("已审核采纳");
        task.setActiveKey(null);
        task.setCompletedAt(adoptedAt);
        task.setUpdatedAt(adoptedAt);
        if (taskMapper.updateById(task) != 1) {
            throw new BusinessException(ErrorCode.TASK_STATE_CONFLICT, "审核时任务版本冲突");
        }
    }
}
