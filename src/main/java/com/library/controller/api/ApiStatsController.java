package com.library.controller.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.library.service.ReportService;
import com.library.web.ApiRoles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** REST API：库存与罚金总览（仅管理员，复用统计报表的 60 秒缓存）。 */
@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "统计", description = "统计总览（管理员）")
public class ApiStatsController {

    private final ReportService reportService;

    public ApiStatsController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/overview")
    @ApiRoles("admin")
    @Operation(summary = "库存与罚金总览", description = "copies=总册数 available=可借 borrowed=借出 overdue=逾期 …")
    public Map<String, Long> overview() {
        return reportService.get().totals();
    }
}
