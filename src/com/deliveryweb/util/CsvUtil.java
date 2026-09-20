package com.deliveryweb.util;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * CSV 导出公共工具：
 * 统一 text/csv 响应头、Content-Disposition 附件名与 UTF-8 BOM 前缀（无 BOM 时中文在 Excel 中乱码），
 * 单元格按 RFC4180 转义（含逗号/引号/换行时整体加引号并 doubling 内嵌引号）。
 */
public final class CsvUtil {

    private static final String LINE_SEP = "\r\n";

    private CsvUtil() {
    }

    /** 写响应头与 BOM；调用方随后用 {@link #row(Object...)} 逐行写出 */
    public static void begin(HttpServletResponse response, String filename) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getWriter().print('\uFEFF');
    }

    /** 单行：各单元格转义后以逗号连接，行尾 CRLF */
    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(values[i]));
        }
        return sb.append(LINE_SEP).toString();
    }

    private static String cell(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        // CSV 公式注入防护（OWASP CSV Injection，审查 P1-4）：以 = + - @ 制表符/回车开头的单元格
        // 前置单引号，防止 Excel/WPS 打开导出文件时对用户可控文本执行公式
        if (s.matches("^[=+@\\t\\r-].*")) {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
