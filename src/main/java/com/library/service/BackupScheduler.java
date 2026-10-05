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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BackupScheduler.class);

    @Scheduled(initialDelay = 120_000, fixedDelay = 30 * 60_000)
    public void dailyBackup() {
        try {
            String name = backupService.runDailyBackupIfNeeded();
            if (name != null) {
                log.info("每日自动备份完成：{}", name);
            }
        } catch (Exception e) {
            log.warn("每日自动备份执行失败", e);
        }
    }
}
