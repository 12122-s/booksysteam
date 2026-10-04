package com.library.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 每日自动备份定时任务：每 30 分钟检查一次“今天备份了没有”，没备过就备一次。
 * 开关和保留天数在系统设置里。与 Python 版的维护线程行为一致。
 */
@Service
public class BackupScheduler {

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 30 * 60_000)
    public void dailyBackup() {
        try {
            backupService.runDailyBackupIfNeeded();
        } catch (Exception e) {
            // 备份失败不影响主流程
        }
    }
}
