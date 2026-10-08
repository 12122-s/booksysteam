package com.library.util;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 读写工具：
 * 读：逗号分隔、双引号包裹、引号内两个双引号表示一个双引号；
 * 编码依次尝试 UTF-8（带/不带 BOM）→ GB18030（Excel 在中文系统上存的是 GBK）。
 * 写：加 UTF-8 BOM，Excel 直接打开中文不乱码。
 */
public final class CsvUtil {

    private static final Charset GB18030 = Charset.forName("GB18030");

    private CsvUtil() {
    }

    /** 探测编码并把整个文件读成文本，三种编码都失败时抛 IllegalArgumentException。 */
    public static String decode(byte[] data) {
        for (Charset charset : List.of(StandardCharsets.UTF_8, GB18030)) {
            try {
                InputStreamReader reader = new InputStreamReader(
                        new ByteArrayInputStream(data), charset.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT));
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) >= 0) {
                    sb.append(buf, 0, n);
                }
                String text = sb.toString();
                if (charset == StandardCharsets.UTF_8 && !text.isEmpty() && text.charAt(0) == '\ufeff') {
                    text = text.substring(1);   // 去掉 UTF-8 BOM（带 BOM 的 UTF-8）
                }
                return text;
            } catch (Exception e) {
                // 换下一种编码继续试
            }
        }
        throw new IllegalArgumentException("文件编码识别不了，请在 Excel 里另存为「CSV UTF-8」再试");
    }

    /** 解析整段 CSV 文本为行列表（不含空行）。 */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                rowHasContent = true;
            } else if (c == ',') {
                row.add(cell.toString());
                rowHasContent = rowHasContent || !cell.isEmpty();
                cell.setLength(0);
            } else if (c == '\n') {
                row.add(cell.toString());
                rowHasContent = rowHasContent || !cell.isEmpty();
                if (rowHasContent) {
                    rows.add(row);
                }
                row = new ArrayList<>();
                cell.setLength(0);
                rowHasContent = false;
            } else if (c != '\r') {
                cell.append(c);
            }
        }
        row.add(cell.toString());
        rowHasContent = rowHasContent || !cell.isEmpty();
        if (rowHasContent) {
            rows.add(row);
        }
        return rows;
    }

    /** 单元格转义。 */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /** 拼一行。 */
    public static String join(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        return sb.toString();
    }

    /** 整段 CSV 文本（带 BOM），供下载。 */
    public static byte[] buildCsv(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("\ufeff");
        sb.append(join(header)).append("\r\n");
        for (List<String> row : rows) {
            sb.append(join(row)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
