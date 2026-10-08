package com.library.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.library.dao.BookDao;
import com.library.entity.Book;
import com.library.service.LogService;
import com.library.service.SettingsService;
import com.library.web.Flash;

/**
 * 图片上传：图书封面（static 上传到项目 uploads/covers/）、收款码（uploads/pay/）。
 * 只允许 jpg/png/gif/webp，单张不超过 3MB。
 */
@Controller
public class MediaController {

    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final long MAX_BYTES = 3 * 1024 * 1024;

    private final BookDao bookDao;
    private final SettingsService settings;
    private final LogService logService;
    private final Path uploadsDir;

    public MediaController(BookDao bookDao, SettingsService settings, LogService logService,
                           @Value("${library.uploads-dir}") String uploadsDir) {
        this.bookDao = bookDao;
        this.settings = settings;
        this.logService = logService;
        this.uploadsDir = Path.of(uploadsDir);
    }

    private Path coverDir() throws IOException {
        Path dir = uploadsDir.resolve("covers");
        Files.createDirectories(dir);
        return dir;
    }

    private Path payDir() throws IOException {
        Path dir = uploadsDir.resolve("pay");
        Files.createDirectories(dir);
        return dir;
    }

    @PostMapping("/books/{bookId}/cover")
    public String uploadCover(@PathVariable Integer bookId,
                              @RequestParam(name = "cover", required = false) MultipartFile file,
                              RedirectAttributes ra) throws IOException {
        Book book = bookDao.findById(bookId);
        if (book == null) {
            return "redirect:/books";
        }
        String error = validateImage(file);
        if (error != null) {
            Flash.error(ra, error);
            return "redirect:/books/" + bookId + "/edit";
        }
        String ext = extOf(file.getOriginalFilename());
        String name = "book" + bookId + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        file.transferTo(coverDir().resolve(name));

        // 换封面后删除旧图片文件
        deleteImage(coverDir(), book.getCover());
        bookDao.updateCover(bookId, name);
        logService.action("上传图书封面", book.getTitle(), name);
        Flash.ok(ra, "《" + book.getTitle() + "》的封面已更新。");
        return "redirect:/books/" + bookId + "/edit";
    }

    @PostMapping("/books/{bookId}/cover/delete")
    public String deleteCover(@PathVariable Integer bookId, RedirectAttributes ra) throws IOException {
        Book book = bookDao.findById(bookId);
        if (book == null) {
            return "redirect:/books";
        }
        deleteImage(coverDir(), book.getCover());
        bookDao.updateCover(bookId, null);
        logService.action("删除图书封面", book.getTitle());
        Flash.ok(ra, "封面已删除。");
        return "redirect:/books/" + bookId + "/edit";
    }

    @PostMapping("/settings/pay-qr")
    public String uploadPayQr(@RequestParam(name = "slot", required = false) String slot,
                              @RequestParam(name = "qr", required = false) MultipartFile file,
                              RedirectAttributes ra) throws IOException {
        String key = "wechat".equals(slot) ? "pay_qr_wechat"
                : "alipay".equals(slot) ? "pay_qr_alipay" : null;
        if (key == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        String error = validateImage(file);
        if (error != null) {
            Flash.error(ra, error);
            return "redirect:/settings";
        }
        String ext = extOf(file.getOriginalFilename());
        String name = "pay-" + slot + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        file.transferTo(payDir().resolve(name));

        deleteImage(payDir(), settings.get(key));
        settings.save(key, name);
        logService.action("上传收款码", "wechat".equals(slot) ? "微信" : "支付宝", name);
        Flash.ok(ra, "收款码已保存，读者缴纳罚金时会看到。");
        return "redirect:/settings";
    }

    @PostMapping("/settings/pay-qr/{slot}/delete")
    public String deletePayQr(@PathVariable String slot, RedirectAttributes ra) throws IOException {
        String key = "wechat".equals(slot) ? "pay_qr_wechat"
                : "alipay".equals(slot) ? "pay_qr_alipay" : null;
        if (key == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        deleteImage(payDir(), settings.get(key));
        settings.save(key, "");
        Flash.ok(ra, "收款码已删除。");
        return "redirect:/settings";
    }

    private String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "请先选择一张图片（jpg / png / gif / webp）";
        }
        String ext = extOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            return "只支持 jpg / png / gif / webp 四种图片格式。";
        }
        if (file.getSize() > MAX_BYTES) {
            return "图片太大了（超过 3MB），请先压缩一下再上传。";
        }
        return null;
    }

    private static String extOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase();
    }

    private void deleteImage(Path dir, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(dir.resolve(Path.of(name).getFileName().toString()));
        } catch (IOException e) {
            // 删不掉旧文件不阻塞主流程
        }
    }
}
