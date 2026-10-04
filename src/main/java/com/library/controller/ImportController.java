package com.library.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.library.service.ImportService;
import com.library.service.LogService;
import com.library.web.Flash;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 图书批量导入（.xlsx / .csv）：下载模板 → 填写 → 上传导入。 */
@Controller
public class ImportController {

    private final ImportService importService;
    private final LogService logService;

    public ImportController(ImportService importService, LogService logService) {
        this.importService = importService;
        this.logService = logService;
    }

    @GetMapping("/books/import")
    public String importPage(Model model) {
        model.addAttribute("headers", ImportService.TEMPLATE_HEADERS);
        model.addAttribute("result", null);
        model.addAttribute("active", "books");
        return "book_import";
    }

    @GetMapping("/books/import/template.csv")
    public ResponseEntity<byte[]> template() {
        byte[] body = importService.templateCsv();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String encoded = URLEncoder.encode("图书导入模板-" + stamp + ".csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition",
                        "attachment; filename=book-import-template.csv; filename*=UTF-8''" + encoded)
                .body(body);
    }

    @PostMapping("/books/import")
    public String upload(@RequestParam(name = "file", required = false) MultipartFile file,
                         @RequestParam(name = "skip_duplicate", required = false) String skipDuplicate,
                         RedirectAttributes ra, Model model) {
        if (file == null || file.isEmpty()) {
            Flash.error(ra, "请先选择要导入的文件（.xlsx 或 .csv）");
            return "redirect:/books/import";
        }
        ImportService.ImportResult result;
        try {
            var rows = importService.readRows(file.getOriginalFilename(), file.getBytes());
            if (rows.size() < 2) {
                Flash.error(ra, "文件里只有表头，没有数据行。");
                return "redirect:/books/import";
            }
            result = importService.doImport(rows, "1".equals(skipDuplicate));
        } catch (IllegalArgumentException e) {
            Flash.error(ra, e.getMessage());
            return "redirect:/books/import";
        } catch (Exception e) {
            Flash.error(ra, "文件读不出来：" + e.getMessage());
            return "redirect:/books/import";
        }
        logService.action("批量导入图书", "成功 " + result.added() + " 本，跳过重复 " + result.skipped() + " 本");
        model.addAttribute("headers", ImportService.TEMPLATE_HEADERS);
        model.addAttribute("result", result);
        model.addAttribute("active", "books");
        return "book_import";
    }
}
