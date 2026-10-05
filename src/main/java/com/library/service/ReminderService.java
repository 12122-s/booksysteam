package com.library.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.library.util.DateUtil;

/**
 * 到期提醒：给“快到期”和“已逾期”的读者自动发提醒邮件。
 * 每 30 分钟检查一次（启动后 90 秒才第一次跑，别拖慢启动）；
 * 同一本书同一种提醒 20 小时内不重复发；逾期提醒固定在第 1/3/7/14/21/30 天，
 * 之后每 7 天一次 —— 与 Python 版的节奏完全一致。
 */
@Service
public class ReminderService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ReminderService.class);

    private static final int SAME_REMINDER_HOURS = 20;
    private static final Set<Integer> OVERDUE_POINTS = Set.of(1, 3, 7, 14, 21, 30);

    /** 一次提醒任务的统计。 */
    public record ReminderStats(boolean configured, int sent, int skipped, int failed,
                                List<String> detail) {
    }

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final MailService mailService;
    private final LogService logService;
    private final com.library.dao.AuxDao auxDao;

    public ReminderService(JdbcTemplate jdbc, SettingsService settings, MailService mailService,
                           LogService logService, com.library.dao.AuxDao auxDao) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.mailService = mailService;
        this.logService = logService;
        this.auxDao = auxDao;
    }

    /**
     * 检查所有未归还记录并发提醒。force=true 时不看“是否已发过”，全部重发一遍
     * （管理端“立即发送”按钮用）。
     */
    public ReminderStats run(boolean force) {
        if (!mailService.mailConfigured()) {
            return new ReminderStats(false, 0, 0, 0, List.of());
        }
        int daysBefore = settings.getInt("reminder_days_before", 3);
        LocalDate today = LocalDate.now();

        record Row(int id, String dueDate, String title, String readerName, String email) {
        }
        List<Row> rows = jdbc.query("""
                SELECT br.id, br.due_date, b.title, r.name AS reader_name, r.email
                FROM borrow_records br JOIN books b ON b.id = br.book_id
                JOIN readers r ON r.id = br.reader_id
                WHERE br.return_date IS NULL ORDER BY br.due_date ASC
                """, (rs, i) -> new Row(rs.getInt(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)));

        int sent = 0;
        int skipped = 0;
        int failed = 0;
        List<String> detail = new ArrayList<>();
        for (Row row : rows) {
            LocalDate due = DateUtil.parseDate(row.dueDate());
            if (due == null) {
                continue;
            }
            int daysLeft = (int) (due.toEpochDay() - today.toEpochDay());
            String kind = null;
            if (daysLeft == daysBefore) {
                kind = "due_soon";
            } else if (daysLeft < 0) {
                int overdueDays = -daysLeft;
                if (OVERDUE_POINTS.contains(overdueDays) || (overdueDays > 30 && overdueDays % 7 == 0)) {
                    kind = "overdue";
                }
            }
            if (kind == null) {
                continue;
            }
            if (row.email() == null || row.email().isBlank()) {
                skipped++;
                detail.add(row.readerName() + "：没有留邮箱，没发");
                continue;
            }
            if (!force && auxDao.reminderSentRecently(row.id(), kind, SAME_REMINDER_HOURS)) {
                skipped++;
                continue;
            }
            String subject;
            String body;
            if ("due_soon".equals(kind)) {
                subject = "【还书提醒】《" + row.title() + "》还有 " + daysBefore + " 天到期";
                body = row.readerName() + "，你好：\n\n"
                        + "你借的《" + row.title() + "》将在 " + row.dueDate() + " 到期"
                        + "（还有 " + daysLeft + " 天），请记得归还或续借。\n\n如已归还，请忽略此邮件。\n";
            } else {
                int overdueDays = -daysLeft;
                subject = "【逾期提醒】《" + row.title() + "》已逾期 " + overdueDays + " 天";
                body = row.readerName() + "，你好：\n\n"
                        + "你借的《" + row.title() + "》应还日期是 " + row.dueDate()
                        + "，现在已经逾期 " + overdueDays + " 天，请尽快归还。\n\n如已归还，请忽略此邮件。\n";
            }
            MailService.SendResult result = mailService.send(row.email(), subject, body);
            if (result.ok()) {
                sent++;
                auxDao.insertReminder(row.id(), kind, row.email(), true, subject);
                detail.add(row.readerName() + "：已发送「" + subject + "」");
            } else {
                failed++;
                auxDao.insertReminder(row.id(), kind, row.email(), false, result.reason());
                detail.add(row.readerName() + "：发送失败（" + result.reason() + "）");
            }
        }
        if (sent > 0) {
            // 后台发送的记录为“系统”身份
            logService.action("发送到期提醒", "成功 " + sent + " 封",
                    "跳过 " + skipped + "，失败 " + failed, "系统", "系统", "");
        }
        log.info("到期提醒执行完成：发送 {} 封，失败 {}，跳过 {}", sent, failed, skipped);
        return new ReminderStats(true, sent, skipped, failed, detail);
    }

    /** 定时任务：每 30 分钟检查一次（启动后延迟 90 秒）。 */
    @Scheduled(initialDelay = 90_000, fixedDelay = 30 * 60_000)
    public void scheduledRun() {
        try {
            if (settings.getBool("reminder_enabled")) {
                run(false);
            }
        } catch (Exception e) {
            // 后台任务失败不影响主流程
        }
    }
}
