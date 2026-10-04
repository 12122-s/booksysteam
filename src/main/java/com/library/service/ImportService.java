package com.library.service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.library.util.CsvUtil;
import com.library.util.DateUtil;

/**
 * 图书批量导入：支持 .xlsx（Excel 直接导入）和 .csv（含 Excel 存的 GBK 编码）。
 * 表头支持多种叫法（如“书名/题名/名称”），用户不用背固定模板。
 */
@Service
public class ImportService {

    public static final List<String> TEMPLATE_HEADERS = List.of(
            "书名", "作者", "分类", "ISBN", "出版社", "出版年份", "单价", "馆藏位置", "总册数", "简介");

    /** 同一列可以有多种叫法。 */
    private static final Map<String, List<String>> COLUMN_ALIASES = Map.of(
            "title", List.of("书名", "题名", "名称", "书"),
            "author", List.of("作者", "著者", "作者姓名"),
            "category", List.of("分类", "类别", "类型"),
            "isbn", List.of("isbn", "书号"),
            "publisher", List.of("出版社", "出版单位"),
            "publish_year", List.of("出版年份", "出版年", "年份"),
            "price", List.of("单价", "价格", "定价"),
            "location", List.of("馆藏位置", "位置", "书架", "书架位置"),
            "total_copies", List.of("总册数", "册数", "数量", "复本数"),
            "summary", List.of("简介", "内容简介", "备注"));

    /** 导入结果（页面展示）。 */
    public record ImportResult(int added, int skipped, List<String> errors, int errorTotal) {
    }

    private final JdbcTemplate jdbc;

    public ImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按文件内容解析出所有行（第一行是表头）。 */
    public List<List<String>> readRows(String filename, byte[] data) throws IllegalArgumentException {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return readXlsx(data);
        }
        if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
            return CsvUtil.parse(CsvUtil.decode(data));
        }
        throw new IllegalArgumentException("只支持 .xlsx（Excel）和 .csv 两种格式。"
                + "如果你的是老版 .xls，请在 Excel 里另存为 .xlsx。");
    }

    private List<List<String>> readXlsx(byte[] data) {
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                boolean hasContent = false;
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    String text = cellText(cell);
                    cells.add(text);
                    if (!text.isBlank()) {
                        hasContent = true;
                    }
                }
                if (hasContent) {
                    rows.add(cells);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 文件读不出来：" + e.getMessage(), e);
        }
        return rows;
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            // 年份/册数这类整数不要显示成 2026.0
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
        return cell.toString().trim();
    }

    /** 把表头行翻译成 {字段名: 列号}。 */
    public Map<String, Integer> matchColumns(List<String> headerRow) {
        Map<String, Integer> mapping = new java.util.HashMap<>();
        for (int index = 0; index < headerRow.size(); index++) {
            String name = headerRow.get(index) == null ? "" : headerRow.get(index).trim().toLowerCase();
            if (name.isEmpty()) {
                continue;
            }
            for (var entry : COLUMN_ALIASES.entrySet()) {
                if (mapping.containsKey(entry.getKey())) {
                    continue;
                }
                if (entry.getValue().stream().anyMatch(a -> a.equalsIgnoreCase(name))) {
                    mapping.put(entry.getKey(), index);
                    break;
                }
            }
        }
        return mapping;
    }

    private static String cell(List<String> row, Map<String, Integer> mapping, String field) {
        Integer index = mapping.get(field);
        if (index == null || index >= row.size()) {
            return "";
        }
        return row.get(index) == null ? "" : row.get(index).trim();
    }

    /**
     * 执行导入。rows 为全部数据行（含表头），返回导入结果。
     * skipDuplicate=1 时跳过“书名+ISBN 都相同”的已有图书。
     */
    @Transactional
    public ImportResult doImport(List<List<String>> rows, boolean skipDuplicate) {
        Map<String, Integer> mapping = matchColumns(rows.get(0));
        if (!mapping.containsKey("title")) {
            throw new IllegalArgumentException("第一行必须是表头，而且要有「书名」这一列；建议直接下载模板填写。");
        }
        int added = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        String now = DateUtil.nowStr();

        for (int i = 1; i < rows.size(); i++) {
            int lineNo = i + 1;
            List<String> row = rows.get(i);
            String title = cell(row, mapping, "title");
            if (title.isEmpty()) {
                boolean rowHasContent = row.stream().anyMatch(c -> c != null && !c.isBlank());
                if (rowHasContent) {
                    errors.add("第 " + lineNo + " 行：没填书名，已跳过");
                }
                continue;
            }
            int copies;
            try {
                copies = Integer.parseInt(cell(row, mapping, "total_copies"));
            } catch (NumberFormatException e) {
                copies = 1;
            }
            if (copies < 1) {
                copies = 1;
            }
            String isbn = cell(row, mapping, "isbn");
            if (skipDuplicate) {
                Integer exists = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM books WHERE is_deleted = 0 AND title = ? "
                                + "AND COALESCE(isbn,'') = ?", Integer.class, title, isbn);
                if (exists != null && exists > 0) {
                    skipped++;
                    continue;
                }
            }
            Integer year = null;
            String yearRaw = cell(row, mapping, "publish_year");
            if (!yearRaw.isEmpty()) {
                try {
                    year = (int) Double.parseDouble(yearRaw);
                } catch (NumberFormatException e) {
                    year = null;
                }
            }
            Double price = null;
            String priceRaw = cell(row, mapping, "price");
            if (!priceRaw.isEmpty()) {
                try {
                    price = Double.parseDouble(priceRaw);
                } catch (NumberFormatException e) {
                    price = null;
                }
            }
            jdbc.update("INSERT INTO books(title, author, category, isbn, publisher, publish_year, "
                            + "price, location, summary, total_copies, available_copies, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    title, cell(row, mapping, "author"), cell(row, mapping, "category"), isbn,
                    cell(row, mapping, "publisher"), year, price, cell(row, mapping, "location"),
                    cell(row, mapping, "summary"), copies, copies, now);
            added++;
        }
        return new ImportResult(added, skipped, errors.size() > 30 ? errors.subList(0, 30) : errors,
                errors.size());
    }

    /** 下载模板的第一行示例数据。 */
    public static List<String> templateSample() {
        return List.of("（这里写书名）", "（作者）", "（分类）", "（书号，可留空）", "（出版社）",
                "（年份）", "（单价）", "（书架位置）", "2", "（可留空）");
    }

    /** 生成导入模板 CSV（带 BOM）。 */
    public byte[] templateCsv() {
        return CsvUtil.buildCsv(TEMPLATE_HEADERS, List.of(templateSample()));
    }
}
