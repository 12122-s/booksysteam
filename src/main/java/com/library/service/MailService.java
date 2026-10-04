package com.library.service;

import java.util.Properties;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * 发邮件（纯文本）：忘记密码验证码、到期提醒、预约到馆通知都走这一个地方。
 * 发件参数（SMTP 服务器、账号、授权码）保存在系统设置里，管理员可在页面上修改，
 * 所以每次发送时按当前设置动态构建 JavaMailSender，而不是用 application.yml 里的静态配置。
 */
@Service
public class MailService {

    /** 一次发送的结果：成功与否 + 失败原因（给页面提示用）。 */
    public record SendResult(boolean ok, String reason) {
        public static SendResult success() {
            return new SendResult(true, "");
        }
    }

    private final SettingsService settings;

    public MailService(SettingsService settings) {
        this.settings = settings;
    }

    /** 管理员有没有把发件邮箱配好。 */
    public boolean mailConfigured() {
        return notBlank(settings.get("smtp_host"))
                && notBlank(settings.get("smtp_user"))
                && notBlank(settings.get("smtp_pass"));
    }

    /** 发一封纯文本邮件。失败不抛异常，返回原因。 */
    public SendResult send(String toEmail, String subject, String body) {
        if (!mailConfigured()) {
            return new SendResult(false, "图书室还没有配置发件邮箱");
        }
        if (toEmail == null || toEmail.isBlank()) {
            return new SendResult(false, "收件人没有填邮箱");
        }
        try {
            JavaMailSender sender = buildSender();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(settings.get("smtp_user"));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
            return SendResult.success();
        } catch (Exception e) {
            String reason = e.getMessage();
            if (reason == null) {
                reason = e.getClass().getSimpleName();
            }
            return new SendResult(false, reason.length() > 150 ? reason.substring(0, 150) : reason);
        }
    }

    /** 验证码邮件。 */
    public SendResult sendResetCode(String toEmail, String readerName, String code, int validMinutes) {
        String body = readerName + "，你好：\n\n"
                + "你正在重置图书室网上账号的密码，验证码是：" + code + "\n\n"
                + "验证码 " + validMinutes + " 分钟内有效。如果这不是你本人操作，请忽略这封邮件。\n";
        return send(toEmail, "密码重置验证码", body);
    }

    /** 按设置里的 SMTP 参数构建发送器（465 走 SSL，其余端口走 STARTTLS）。 */
    private JavaMailSender buildSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String host = settings.get("smtp_host").trim();
        int port;
        try {
            String portRaw = settings.get("smtp_port");
            port = Integer.parseInt((portRaw == null || portRaw.isBlank() ? "465" : portRaw).trim());
        } catch (NumberFormatException e) {
            port = 465;
        }
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(settings.get("smtp_user").trim());
        sender.setPassword(settings.get("smtp_pass").trim());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
