package com.library.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.library.service.AnalyticsService;
import com.library.service.ReportService;

/** 统计报表 + 数据分析看板（两个页面共用本控制器）。 */
@Controller
public class ReportsController {

    private final ReportService reportService;
    private final AnalyticsService analyticsService;

    public ReportsController(ReportService reportService, AnalyticsService analyticsService) {
        this.reportService = reportService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        ReportService.ReportData data = reportService.get();
        model.addAttribute("catRows", data.catRows());
        model.addAttribute("hotBooks", data.hotBooks());
        model.addAttribute("hotReaders", data.hotReaders());
        model.addAttribute("monthly", data.monthly());
        model.addAttribute("totals", data.totals());
        model.addAttribute("active", "reports");
        return "reports";
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        AnalyticsService.AnalyticsData data = analyticsService.get();
        model.addAttribute("trend", data.trend());
        model.addAttribute("categories", data.categories());
        model.addAttribute("readers", data.readers());
        model.addAttribute("turnover", data.turnover());
        model.addAttribute("returns", data.returns());
        model.addAttribute("durations", data.durations());
        model.addAttribute("chartWidth", AnalyticsService.CHART_WIDTH);
        model.addAttribute("chartHeight", AnalyticsService.CHART_HEIGHT);
        model.addAttribute("active", "analytics");
        return "analytics";
    }
}
