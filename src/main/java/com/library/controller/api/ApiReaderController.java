package com.library.controller.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.library.dao.BookDao;
import com.library.dao.ReaderDao;
import com.library.entity.Reader;
import com.library.util.DateUtil;
import com.library.web.ApiRoles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** REST API：读者查询（仅管理员）。 */
@RestController
@RequestMapping("/api/v1/readers")
@Tag(name = "读者", description = "读者查询（管理员）")
public class ApiReaderController {

    private final ReaderDao readerDao;

    public ApiReaderController(ReaderDao readerDao) {
        this.readerDao = readerDao;
    }

    @GetMapping
    @ApiRoles("admin")
    @Operation(summary = "读者列表", description = "q=关键词（姓名/卡号/手机/部门），type=读者类型，page=页码")
    public Map<String, Object> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(defaultValue = "1") Integer page) {
        BookDao.ListResult<Reader> result =
                readerDao.list(q, type, page, 15, DateUtil.todayStr());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.total());
        body.put("pages", result.pages());
        body.put("page", result.page());
        body.put("items", result.items());
        return body;
    }
}
