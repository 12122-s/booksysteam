package com.library.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.library.service.SettingsService;

/** 公告与借阅规则页（公开，不需要登录）。 */
@Controller
public class NoticeController {

    private final SettingsService settings;

    public NoticeController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/notice")
    public String noticePage(Model model) {
        String announcement = settings.get("announcement");
        model.addAttribute("announcement",
                announcement == null ? "" : announcement.trim());
        model.addAttribute("borrowDays", settings.getInt("borrow_days"));
        model.addAttribute("maxBooks", settings.getInt("max_books_per_reader"));
        model.addAttribute("maxRenew", settings.getInt("max_renew_times"));
        model.addAttribute("finePerDay", settings.getDouble("fine_per_day"));
        model.addAttribute("libraryName", settings.get("library_name"));
        model.addAttribute("active", "notice");
        return "notice";
    }
}
