package com.deliveryweb.controller;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 安全响应头过滤器：为全部响应统一输出基础安全头。
 * 只设头部、绝不触碰字符编码（避免 EncodingFilter 注释所述的静态资源文本模式乱码坑）。
 */
@WebFilter("/*")
public class SecurityHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse res = (HttpServletResponse) response;
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "SAMEORIGIN");
        res.setHeader("Referrer-Policy", "same-origin");
        chain.doFilter(request, response);
    }
}
