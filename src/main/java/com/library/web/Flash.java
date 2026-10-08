package com.library.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 一次性提示消息：存进 redirect attributes，重定向后在下一页顶部显示。
 * 一页可以有多条（还书时会连发“还书成功 + 到馆通知”两条）。
 */
public final class Flash {

    public record Item(String category, String message) {
    }

    public static final String KEY = "flashList";

    private Flash() {
    }

    public static void ok(RedirectAttributes ra, String message) {
        add(ra, "ok", message);
    }

    public static void warn(RedirectAttributes ra, String message) {
        add(ra, "warn", message);
    }

    public static void error(RedirectAttributes ra, String message) {
        add(ra, "error", message);
    }

    @SuppressWarnings("unchecked")
    public static void add(RedirectAttributes ra, String category, String message) {
        Object existing = ra.getFlashAttributes().get(KEY);
        List<Item> list;
        if (existing instanceof List<?> l) {
            list = (List<Item>) l;
        } else {
            list = new ArrayList<>();
            ra.addFlashAttribute(KEY, list);
        }
        list.add(new Item(category, message));
    }
}
