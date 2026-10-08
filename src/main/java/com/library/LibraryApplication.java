package com.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 图书管理系统启动入口。
 *
 * <p>主要功能：管理端（图书、读者、借还、罚金、报表、
 * 分析、导入导出、备份、日志）+ 读者端（找书、申请借阅、书评、自助办卡）。
 *
 * <p>启动时自动建库建表（已存在则跳过，不影响已有数据），并启动两个后台定时任务：
 * 到期/逾期邮件提醒（每 30 分钟）、每日自动备份（每 30 分钟检查一次当天是否已备份）。
 */
@SpringBootApplication
@EnableScheduling
public class LibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }
}
