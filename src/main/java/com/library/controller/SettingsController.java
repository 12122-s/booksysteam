package com.library.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.library.dao.AuxDao;
import com.library.service.BackupService;
import com.library.service.LogService;
import com.library.service.MailService;
import com.library.service.ReminderService;
import com.library.service.SettingsService;
import com.library.util.PasswordUtil;
import com.library.web.Flash;

import static com.library.controller.BookController.param;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 系统设置：借阅规则 / 管理密码 / 发件邮箱 / 备份与恢复 / 示例数据。
 * 与 Python 版 /settings 系列路由一一对应。
 */
@Controller
public class SettingsController {

    private final SettingsService settings;
    private final BackupService backupService;
    private final MailService mailService;
    private final ReminderService reminderService;
    private final LogService logService;
    private final JdbcTemplate jdbc;
    private final AuxDao auxDao;

    public SettingsController(SettingsService settings, BackupService backupService,
                              MailService mailService, ReminderService reminderService,
                              LogService logService, JdbcTemplate jdbc, AuxDao auxDao) {
        this.settings = settings;
        this.backupService = backupService;
        this.mailService = mailService;
        this.reminderService = reminderService;
        this.logService = logService;
        this.jdbc = jdbc;
        this.auxDao = auxDao;
    }

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("books", scalar("SELECT COUNT(*) FROM books WHERE is_deleted = 0"));
        counts.put("readers", scalar("SELECT COUNT(*) FROM readers WHERE is_deleted = 0"));
        counts.put("records", scalar("SELECT COUNT(*) FROM borrow_records"));
        counts.put("borrowing", scalar("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL"));
        model.addAttribute("counts", counts);
        model.addAttribute("backups", backupService.listBackups(10));
        boolean defaultPassword = settings.getAdminPasswordHash().isEmpty()
                || PasswordUtil.check(settings.getAdminPasswordHash(), "123456");
        model.addAttribute("defaultPassword", defaultPassword);
        model.addAttribute("active", "settings");
        return "settings";
    }

    private long scalar(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }

    @PostMapping("/settings")
    public String saveSettings(HttpServletRequest request, RedirectAttributes ra) {
        // 文本型设置
        Map<String, String> textValues = new LinkedHashMap<>();
        textValues.put("library_name", param(request, "library_name"));
        textValues.put("admin_account", param(request, "admin_account"));
        textValues.put("allow_self_borrow", checkbox(request, "allow_self_borrow"));
        textValues.put("borrow_need_approval", checkbox(request, "borrow_need_approval"));
        textValues.put("reader_self_register", checkbox(request, "reader_self_register"));
        textValues.put("reader_register_need_approval", checkbox(request, "reader_register_need_approval"));
        textValues.put("reminder_enabled", checkbox(request, "reminder_enabled"));
        textValues.put("mail_notify_ready", checkbox(request, "mail_notify_ready"));
        textValues.put("allow_multi_login", checkbox(request, "allow_multi_login"));
        textValues.put("auto_backup_enabled", checkbox(request, "auto_backup_enabled"));
        textValues.put("announcement", param(request, "announcement"));
        textValues.put("pay_note", param(request, "pay_note"));

        // 数字型设置：范围校验（与 Python 版一致）
        Map<String, String> numberValues = new LinkedHashMap<>();
        String[][] checks = {
                {"borrow_days", "1", "365", "默认借期请填 1~365 之间的天数"},
                {"max_books_per_reader", "1", "100", "每人最多可借请填 1~100 之间的册数"},
                {"max_renew_times", "0", "10", "续借次数上限请填 0~10 之间"},
                {"fine_per_day", "0", "100", "逾期罚金请填 0~100 之间的金额"},
                {"reminder_days_before", "1", "30", "提前提醒天数请填 1~30 之间"},
                {"auto_backup_keep_days", "1", "365", "备份保留天数请填 1~365 之间"},
                {"request_expire_days", "0", "90", "申请自动取消天数请填 0~90 之间（0=不自动取消）"}};
        String error = null;
        if (textValues.get("library_name").isEmpty()) {
            error = "图书室名称不能为空";
        } else if (textValues.get("admin_account").isEmpty()) {
            error = "管理员账号不能为空";
        } else if (textValues.get("admin_account").contains(" ")) {
            error = "管理员账号不能有空格";
        } else {
            for (String[] check : checks) {
                String raw = param(request, check[0]);
                String msg = check[3];
                double value;
                try {
                    value = Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    error = msg;
                    break;
                }
                double low = Double.parseDouble(check[1]);
                double high = Double.parseDouble(check[2]);
                if (value < low || value > high) {
                    error = msg;
                    break;
                }
                numberValues.put(check[0],
                        value == Math.floor(value) ? String.valueOf((long) value)
                                : String.valueOf(Math.round(value * 100.0) / 100.0));
            }
        }
        if (error != null) {
            Flash.error(ra, error);
            return "redirect:/settings";
        }
        textValues.forEach(settings::save);
        numberValues.forEach(settings::save);
        Flash.ok(ra, "设置已保存，立即生效");
        return "redirect:/settings";
    }

    private static String checkbox(HttpServletRequest request, String name) {
        return request.getParameter(name) != null ? "1" : "0";
    }

    @PostMapping("/settings/password")
    public String changePassword(HttpServletRequest request, RedirectAttributes ra) {
        String current = request.getParameter("current_password");
        String newPw = request.getParameter("new_password");
        String confirm = request.getParameter("confirm_password");
        String hashed = settings.getAdminPasswordHash();
        if (hashed.isEmpty() || !PasswordUtil.check(hashed, current)) {
            Flash.error(ra, "当前密码不正确，密码没有修改");
        } else if (newPw == null || newPw.length() < 6) {
            Flash.error(ra, "新密码至少要 6 位");
        } else if (!newPw.equals(confirm)) {
            Flash.error(ra, "两次输入的新密码不一样，请重新输入");
        } else {
            settings.save("admin_password_hash", PasswordUtil.hash(newPw));
            Flash.ok(ra, "密码修改成功，下次登录请用新密码");
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/test-email")
    public String testEmail(HttpServletRequest request, RedirectAttributes ra) {
        String to = param(request, "to");
        if (to.isEmpty()) {
            to = settings.get("smtp_user") == null ? "" : settings.get("smtp_user").trim();
        }
        if (to.isEmpty()) {
            Flash.error(ra, "请先填写一个接收测试邮件的邮箱地址。");
        } else {
            var result = mailService.send(to, "测试邮件（图书管理系统）",
                    "这是一封测试邮件。收到说明发件邮箱配置正确。\n验证码示例：123456\n");
            if (result.ok()) {
                Flash.ok(ra, "测试邮件已发到 " + to + "，请去收件箱看看（也可能在垃圾邮件里）。");
            } else {
                Flash.error(ra, "发送失败：" + result.reason());
            }
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/email")
    public String saveEmailSettings(HttpServletRequest request, RedirectAttributes ra) {
        String host = param(request, "smtp_host");
        String port = paramOr(request, "smtp_port", "465");
        String user = param(request, "smtp_user");
        String password = request.getParameter("smtp_pass") == null
                ? "" : request.getParameter("smtp_pass").trim();
        if (!port.matches("\\d+")) {
            Flash.error(ra, "端口请填数字，常用的是 465（QQ/163）或 587。");
            return "redirect:/settings";
        }
        if (!host.isEmpty() && user.isEmpty()) {
            Flash.error(ra, "填了发件服务器，就要一起填发件邮箱账号。");
            return "redirect:/settings";
        }
        settings.save("smtp_host", host);
        settings.save("smtp_port", port);
        settings.save("smtp_user", user);
        settings.save("smtp_pass", password);
        Flash.ok(ra, "发件邮箱设置已保存");
        return "redirect:/settings";
    }

    private static String paramOr(HttpServletRequest request, String name, String fallback) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    @PostMapping("/settings/load-demo")
    public String loadDemo(RedirectAttributes ra) {
        seedDemoData();
        Flash.ok(ra, "示例数据已载入，可以到图书列表查看");
        return "redirect:/books";
    }

    @PostMapping("/settings/clear-data")
    public String clearData(RedirectAttributes ra) {
        jdbc.update("DELETE FROM borrow_records");
        jdbc.update("DELETE FROM books");
        jdbc.update("DELETE FROM readers");
        Flash.warn(ra, "所有图书、读者和借阅记录已清空（系统设置和密码保留）");
        return "redirect:/settings";
    }

    /** 写入示例图书、读者和借阅记录（首次体验用，与 Python 版相同的数据）。 */
    private void seedDemoData() {
        String now = com.library.util.DateUtil.nowStr();
        String[][] books = {
                {"活着", "余华", "文学小说", "9787506365437", "作家出版社", "2012", "28.0", "A-01", "3",
                        "讲述一个人一生的苦难与坚韧。"},
                {"三体", "刘慈欣", "科幻", "9787536692930", "重庆出版社", "2008", "23.0", "A-02", "4",
                        "中国科幻代表作，地球往事三部曲第一部。"},
                {"百年孤独", "加西亚·马尔克斯", "文学小说", "9787544253994", "南海出版公司", "2011", "39.5", "A-03", "2",
                        "魔幻现实主义文学经典。"},
                {"红楼梦", "曹雪芹", "古典文学", "9787020002207", "人民文学出版社", "1996", "59.7", "B-01", "5",
                        "中国古典四大名著之一。"},
                {"平凡的世界", "路遥", "文学小说", "9787530216781", "北京十月文艺出版社", "2017", "108.0", "B-02", "3",
                        "普通人在大时代中的奋斗历程。"},
                {"人类简史", "尤瓦尔·赫拉利", "历史", "9787508647357", "中信出版社", "2014", "68.0", "C-01", "2",
                        "从认知革命到科学革命的人类发展史。"},
                {"明朝那些事儿", "当年明月", "历史", "9787801656087", "中国海关出版社", "2009", "358.0", "C-02", "3",
                        "用通俗笔法讲述明朝三百年。"},
                {"小王子", "圣埃克苏佩里", "外国文学", "9787020042494", "人民文学出版社", "2003", "22.0", "D-01", "4",
                        "写给大人的童话。"},
                {"窗边的小豆豆", "黑柳彻子", "儿童读物", "9787544270878", "南海出版公司", "2011", "25.0", "E-01", "3",
                        "巴学园里的成长故事。"},
                {"你当像鸟飞往你的山", "塔拉·韦斯特弗", "传记", "9787544276986", "南海出版公司", "2019", "59.0", "D-02", "2",
                        "一个女孩靠教育改变命运的自传。"},
                {"数学之美", "吴军", "科普", "9787115373557", "人民邮电出版社", "2014", "49.0", "F-01", "2",
                        "用通俗语言讲清数学在信息技术中的作用。"},
                {"围城", "钱钟书", "文学小说", "9787020090006", "人民文学出版社", "1991", "36.0", "A-04", "2",
                        "一部风格独特的讽刺小说。"},
                {"乡土中国", "费孝通", "社会科学", "9787208061644", "上海人民出版社", "2006", "22.0", "G-01", "3",
                        "理解中国基层社会结构的经典之作。"},
                {"球状闪电", "刘慈欣", "科幻", "9787536693968", "重庆出版社", "2005", "25.0", "A-05", "2",
                        "关于量子物理与命运的科幻长篇。"},
                {"明朝那些事儿·第一部", "当年明月", "历史", "9787801656094", "中国海关出版社", "2009", "29.8", "C-03", "1",
                        "系列第一部：洪武大帝。"}};
        String[][] readers = {
                {"2024001", "王小明", "男", "学生", "高一(3)班", "13800000001", "正常"},
                {"2024002", "李静", "女", "学生", "高一(1)班", "13800000002", "正常"},
                {"T001", "张老师", "男", "教师", "语文教研组", "13800000003", "正常"},
                {"2019007", "陈雨欣", "女", "学生", "高三(2)班", "13800000004", "正常"},
                {"W005", "刘工", "男", "职工", "后勤处", "13800000005", "正常"}};

        List<Integer> bookIds = new java.util.ArrayList<>();
        for (String[] b : books) {
            jdbc.update("INSERT INTO books(title, author, category, isbn, publisher, publish_year, "
                            + "price, location, total_copies, available_copies, summary, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    b[0], b[1], b[2], b[3], b[4], Integer.parseInt(b[5]), Double.parseDouble(b[6]),
                    b[7], Integer.parseInt(b[8]), Integer.parseInt(b[8]), b[9], now);
            Integer id = jdbc.queryForObject(
                    "SELECT id FROM books WHERE title = ? AND created_at = ? ORDER BY id DESC LIMIT 1",
                    Integer.class, b[0], now);
            bookIds.add(id == null ? 0 : id);
        }
        List<Integer> readerIds = new java.util.ArrayList<>();
        for (String[] r : readers) {
            jdbc.update("INSERT INTO readers(card_no, name, gender, reader_type, department, phone, "
                            + "status, created_at) VALUES (?,?,?,?,?,?,?,?)",
                    r[0], r[1], r[2], r[3], r[4], r[5], r[6], now);
            Integer id = jdbc.queryForObject(
                    "SELECT id FROM readers WHERE card_no = ? AND created_at = ? ORDER BY id DESC LIMIT 1",
                    Integer.class, r[0], now);
            readerIds.add(id == null ? 0 : id);
        }

        // 借阅样例：借出中 / 已逾期 / 已归还（含一条逾期归还留罚金）
        record Sample(int book, int reader, int borrowOffset, int dueOffset,
                      Integer returnOffset, int renew, double fine, String remark) {
        }
        List<Sample> samples = List.of(
                new Sample(0, 0, -5, 25, null, 0, 0, ""),
                new Sample(1, 1, -12, 18, null, 0, 0, ""),
                new Sample(7, 3, -3, 27, null, 0, 0, ""),
                new Sample(3, 2, -45, -15, null, 0, 0, ""),
                new Sample(2, 4, -40, -10, null, 1, 0, ""),
                new Sample(5, 0, -60, -30, -26, 0, 2.0, "逾期 4 天归还"),
                new Sample(8, 3, -50, -20, -25, 0, 0, ""),
                new Sample(10, 1, -35, -5, -8, 0, 0, ""),
                new Sample(12, 2, -80, -50, -55, 0, 0, ""),
                new Sample(6, 4, -70, -40, -42, 0, 0, ""),
                new Sample(4, 0, -20, 10, -2, 0, 0, ""));
        java.time.LocalDate today = java.time.LocalDate.now();
        for (Sample s : samples) {
            String borrowDate = today.plusDays(s.borrowOffset()).toString();
            String dueDate = today.plusDays(s.dueOffset()).toString();
            String returnDate = s.returnOffset() == null ? null
                    : today.plusDays(s.returnOffset()).toString();
            jdbc.update("INSERT INTO borrow_records(book_id, reader_id, borrow_date, due_date, "
                            + "return_date, renew_count, fine_amount, remark) VALUES (?,?,?,?,?,?,?,?)",
                    bookIds.get(s.book()), readerIds.get(s.reader()), borrowDate, dueDate,
                    returnDate, s.renew(), s.fine(), s.remark());
            if (returnDate == null) {
                jdbc.update("UPDATE books SET available_copies = available_copies - 1 WHERE id = ?",
                        bookIds.get(s.book()));
            }
        }
    }

    // ---------------- 备份与恢复 ----------------

    @PostMapping("/settings/backup-now")
    public String backupNow(RedirectAttributes ra) {
        String name = backupService.makeBackup("手动备份");
        backupService.cleanupOldBackups(settings.getInt("auto_backup_keep_days", 30));
        logService.action("手动备份数据", name);
        Flash.ok(ra, "备份完成，文件名：" + name + "（在程序的 backups 文件夹里）");
        return "redirect:/settings";
    }

    @GetMapping("/backups/{name}")
    public org.springframework.http.ResponseEntity<byte[]> downloadBackup(@PathVariable String name) {
        Path path = backupService.resolveBackup(name);
        if (path == null || !Files.exists(path)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        }
        try {
            byte[] body = Files.readAllBytes(path);
            String encoded = java.net.URLEncoder.encode(path.getFileName().toString(),
                    StandardCharsets.UTF_8);
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Disposition",
                            "attachment; filename=backup.sql; filename*=UTF-8''" + encoded)
                    .body(body);
        } catch (IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/backups/{name}/delete")
    public String deleteBackup(@PathVariable String name, RedirectAttributes ra) throws IOException {
        Path path = backupService.resolveBackup(name);
        if (path == null || !Files.exists(path)) {
            return "redirect:/settings";
        }
        Files.deleteIfExists(path);
        logService.action("删除备份文件", path.getFileName().toString());
        Flash.ok(ra, "已删除备份 " + path.getFileName());
        return "redirect:/settings";
    }

    @PostMapping("/settings/restore")
    public String restore(@RequestParam("file") MultipartFile file, RedirectAttributes ra)
            throws IOException {
        if (file == null || file.isEmpty()) {
            Flash.error(ra, "请先选择要恢复的 .sql 备份文件。");
            return "redirect:/settings";
        }
        byte[] data = file.getBytes();
        String text = null;
        for (String charset : List.of("UTF-8", "GB18030")) {
            try {
                text = new String(data, java.nio.charset.Charset.forName(charset));
                break;
            } catch (Exception e) {
                // 换编码继续
            }
        }
        if (text == null || (!text.contains("INSERT INTO `books`") && !text.contains("DELETE FROM `books`"))) {
            Flash.error(ra, "这个文件不像是本系统导出的备份（里面没有图书数据），为安全起见已经拒绝恢复。");
            return "redirect:/settings";
        }

        // 恢复前先自动备份一份当前数据，万一恢复错了还能退回来
        String backupPath = backupService.makeBackup("恢复前自动备份");
        List<String> statements = new java.util.ArrayList<>();
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            statements.add(line);
        }
        try {
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String sql : statements) {
                if (sql.toUpperCase().startsWith("SET ")) {
                    continue;
                }
                jdbc.execute(sql);
            }
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
            settings.evict();
        } catch (Exception e) {
            Flash.error(ra, "恢复失败：" + e.getMessage() + "。你的数据没有被改动，"
                    + "恢复前的备份放在 backups 文件夹里。");
            return "redirect:/settings";
        }
        logService.action("从备份恢复数据", file.getOriginalFilename(),
                "共执行 " + statements.size() + " 条语句");
        Flash.ok(ra, "数据已恢复（共执行 " + statements.size() + " 条语句）。"
                + "恢复前的旧数据已自动存到 backups 文件夹，文件名是「" + backupPath + "」。");
        return "redirect:/settings";
    }

    // ---------------- 提醒 ----------------

    @PostMapping("/settings/send-reminders")
    public String sendReminders(RedirectAttributes ra) {
        ReminderService.ReminderStats result = reminderService.run(true);
        if (!result.configured()) {
            Flash.error(ra, "还没有配置发件邮箱，无法发送提醒。请先到「验证码发件邮箱」里填好邮箱和授权码。");
        } else if (result.sent() == 0 && result.failed() == 0 && result.skipped() == 0) {
            Flash.warn(ra, "暂时没有需要提醒的读者（没有快到期的，也没有逾期的）。");
        } else if (result.failed() == 0) {
            Flash.ok(ra, "提醒发送完成：成功 " + result.sent() + " 封，失败 " + result.failed()
                    + " 封，跳过 " + result.skipped() + " 条。");
        } else {
            Flash.warn(ra, "提醒发送完成：成功 " + result.sent() + " 封，失败 " + result.failed()
                    + " 封，跳过 " + result.skipped() + " 条。");
        }
        return "redirect:/settings";
    }

    @GetMapping("/reminders")
    public String reminderList(Model model) {
        model.addAttribute("rows", auxDao.listReminders());
        model.addAttribute("mailConfigured", mailService.mailConfigured());
        model.addAttribute("active", "settings");
        return "reminders";
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String role,
                       Model model) {
        model.addAttribute("rows", auxDao.listLogs(q, role));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("role", role == null ? "" : role);
        model.addAttribute("active", "logs");
        return "logs";
    }
}
