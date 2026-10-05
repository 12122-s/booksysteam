package com.library.controller.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.library.dao.BookDao;
import com.library.dao.BorrowDao;
import com.library.entity.BorrowRecord;
import com.library.service.BorrowService;
import com.library.web.ApiAuthInterceptor;
import com.library.web.ApiRoles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * REST API：借阅。查询与管理员借出/还书（仅 admin）；
 * 续借 admin 可续任何记录、reader 只能续自己的。
 */
@RestController
@RequestMapping("/api/v1/borrows")
@Tag(name = "借阅", description = "借阅查询与管理")
public class ApiBorrowController {

    private final BorrowDao borrowDao;
    private final BorrowService borrowService;

    public ApiBorrowController(BorrowDao borrowDao, BorrowService borrowService) {
        this.borrowDao = borrowDao;
        this.borrowService = borrowService;
    }

    public record BorrowRequest(@NotNull(message = "bookId 不能为空") Integer bookId,
                                @NotNull(message = "readerId 不能为空") Integer readerId,
                                String dueDate,
                                String remark) {
    }

    public record RenewRequest(Integer days) {
    }

    @GetMapping
    @ApiRoles("admin")
    @Operation(summary = "借阅记录列表", description = "status=borrowing/overdue/returned，q=关键词，page=页码")
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "1") Integer page) {
        BookDao.ListResult<BorrowRecord> result =
                borrowDao.list(status, q, page, 15, java.time.LocalDate.now().toString());
        borrowService.decorate(result.items());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.total());
        body.put("pages", result.pages());
        body.put("page", result.page());
        body.put("items", result.items());
        return body;
    }

    @PostMapping
    @ApiRoles("admin")
    @Operation(summary = "办理借出", description = "dueDate 格式 yyyy-MM-dd，留空按系统借期")
    public ResponseEntity<?> create(@Valid @RequestBody BorrowRequest req) {
        BorrowService.OpResult result =
                borrowService.borrowNew(req.bookId(), req.readerId(), req.dueDate(), req.remark());
        return result.ok()
                ? ResponseEntity.ok(Map.of("success", true, "message", result.message()))
                : ResponseEntity.badRequest().body(Map.of("code", 400, "message", result.message()));
    }

    @PostMapping("/{id}/return")
    @ApiRoles("admin")
    @Operation(summary = "办理还书", description = "fine 留空按逾期天数自动计算罚金")
    public ResponseEntity<?> returnBook(@PathVariable Integer id,
                                        @RequestParam(name = "fine", required = false) String fine) {
        BorrowService.ReturnOutcome outcome = borrowService.returnBook(id, fine);
        if (outcome == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "借阅记录不存在"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "还书成功");
        body.put("overdueDays", outcome.overdueDays());
        body.put("fine", outcome.fine());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "续借", description = "读者只能续借自己的记录；days 留空按默认借期")
    public ResponseEntity<?> renew(@PathVariable Integer id,
                        @RequestBody(required = false) RenewRequest req,
                        @RequestAttribute(ApiAuthInterceptor.ATTR_ROLE) String role,
                        @RequestAttribute(value = ApiAuthInterceptor.ATTR_READER_ID,
                                required = false) Integer readerId) {
        Integer days = req == null ? null : req.days();
        // 读者身份只能操作自己的借阅记录（readerId>0 时 DAO 会带 reader_id 条件）
        int operatorReaderId = "reader".equals(role) && readerId != null ? readerId : 0;
        BorrowService.OpResult result = borrowService.renew(id, days, operatorReaderId);
        if (result == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "借阅记录不存在"));
        }
        return result.ok()
                ? ResponseEntity.ok(Map.of("success", true, "message", result.message()))
                : ResponseEntity.badRequest().body(Map.of("code", 400, "message", result.message()));
    }
}
