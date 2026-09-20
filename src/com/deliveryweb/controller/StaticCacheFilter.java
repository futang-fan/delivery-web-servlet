package com.deliveryweb.controller;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 静态资源强缓存过滤器：为 /common/* 与 /libs/* 设置 Cache-Control，
 * 使 vue.esm、common.js、style.css 等二次访问走浏览器缓存，缩短多页切换的资源加载白屏。
 * 注意：只设 Cache-Control，绝不 setCharacterEncoding——否则 Tomcat DefaultServlet 会切换文本模式
 * 造成静态资源双重编码乱码（见 EncodingFilter 教训）。
 * max-age 取 300 秒平衡缓存命中与前端更新可见性。
 */
@WebFilter(filterName = "StaticCacheFilter", urlPatterns = { "/common/*", "/libs/*" })
public class StaticCacheFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse res = (HttpServletResponse) response;
        // 强缓存缓解 MPA 切换闪烁（二次访问直接命中）；布局已定稿恢复强缓存。
        // 注意：开发期改动 /common/ /libs/ 下静态资源后需 Ctrl+F5 强刷验证，避免旧缓存干扰
        res.setHeader("Cache-Control", "public, max-age=300");
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
