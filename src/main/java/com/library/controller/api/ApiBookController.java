package com.library.controller.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.library.dao.BookDao;
import com.library.entity.Book;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** REST API：图书查询（登录身份均可访问）。 */
@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "图书", description = "图书查询")
public class ApiBookController {

    private final BookDao bookDao;

    public ApiBookController(BookDao bookDao) {
        this.bookDao = bookDao;
    }

    @GetMapping
    @Operation(summary = "图书列表", description = "q=关键词，category=分类，available=1 只看可借，page=页码")
    public Map<String, Object> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String available,
                                    @RequestParam(defaultValue = "1") Integer page) {
        BookDao.ListResult<Book> result =
                bookDao.list(q, category, "1".equals(available), page, 15);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.total());
        body.put("pages", result.pages());
        body.put("page", result.page());
        body.put("items", result.items());
        return body;
    }

    @GetMapping("/{id}")
    @Operation(summary = "图书详情")
    public ResponseEntity<?> detail(@PathVariable Integer id) {
        Book book = bookDao.findById(id);
        if (book == null || book.getIsDeleted() == 1) {
            return ResponseEntity.status(404)
                    .body(Map.of("code", 404, "message", "图书不存在或已下架"));
        }
        return ResponseEntity.ok(book);
    }
}
