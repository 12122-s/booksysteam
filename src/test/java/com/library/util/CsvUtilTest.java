package com.library.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvUtilTest {

    private static final String BOM = "\ufeff";

    @Test
    void decodesUtf8WithBom() {
        // 带 UTF-8 BOM 的 CSV 必须能剥掉 BOM
        byte[] data = (BOM + "书名,作者\n三体,刘慈欣").getBytes(StandardCharsets.UTF_8);
        assertEquals("书名,作者\n三体,刘慈欣", CsvUtil.decode(data));
    }

    @Test
    void decodesGbkFromExcel() {
        // 中文 Windows 上 Excel 直接另存的 CSV 是 GBK/GB18030
        byte[] data = "书名,作者\n活着,余华".getBytes(Charset.forName("GB18030"));
        List<List<String>> rows = CsvUtil.parse(CsvUtil.decode(data));
        assertEquals("书名", rows.get(0).get(0));
        assertEquals("活着", rows.get(1).get(0));
    }

    @Test
    void rejectsUndecodableBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> CsvUtil.decode(new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00}));
    }

    @Test
    void parsesQuotedCells() {
        List<List<String>> rows = CsvUtil.parse("a,\"b,c\",\"d\"\"e\"\nf,g,h");
        assertEquals(2, rows.size());
        assertEquals(List.of("a", "b,c", "d\"e"), rows.get(0));
        assertEquals(List.of("f", "g", "h"), rows.get(1));
    }

    @Test
    void skipsBlankRows() {
        List<List<String>> rows = CsvUtil.parse("a,b\n\nc,d\n,,");
        assertEquals(2, rows.size());
    }

    @Test
    void buildCsvAddsBomAndEscapes() {
        byte[] data = CsvUtil.buildCsv(List.of("书名", "备注"), List.of(List.of("三体", "含,逗号")));
        String text = new String(data, StandardCharsets.UTF_8);
        assertTrue(text.startsWith(BOM));
        assertTrue(text.contains("\"含,逗号\""));
    }
}
