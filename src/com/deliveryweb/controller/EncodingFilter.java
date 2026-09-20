package com.deliveryweb.controller;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import java.io.IOException;

/**
 * 统一 UTF-8 编码过滤器，避免 JSON 接口请求与响应中文乱码。
 * 注意：只拦截 /api/*，不得改回 /*！
 * 若对静态资源也设置响应 charset，Tomcat DefaultServlet 会从二进制直传（sendfile）
 * 切换为文本模式，用平台默认编码（中文 Windows 为 GBK）读取 UTF-8 文件，
 * 导致浏览器看到双重编码乱码。静态页面的编码由
 * HTML 的 meta charset 保证，JSON 响应的编码由 JsonUtil.writeJson 显式设置。
 */
@WebFilter(filterName = "EncodingFilter", urlPatterns = "/api/*")
public class EncodingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
