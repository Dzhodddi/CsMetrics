package org.example.utility;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String origin = req.getHeader("Origin");
        if (origin != null) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            resp.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:3000");
        }
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        resp.setHeader("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String uri = req.getRequestURI();
        if (uri.endsWith("/login") || uri.endsWith("/login/") || uri.contains("/crypto/")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = req.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String actualToken = authHeader.substring(7);

            try {
                DecodedJWT decodedJWT = JwtUtil.decodeJWT(actualToken);
                String role = decodedJWT.getClaim("role").asString();

                req.setAttribute("USER_ROLE", role);

                String method = req.getMethod();
                if (!"GET".equalsIgnoreCase(method) && !"ROLE_ADMIN".equals(role)) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.setContentType("application/json");
                    resp.getWriter().write("{\"error\": \"Forbidden: Admin role required\"}");
                    return;
                }

                chain.doFilter(request, response);
                return;

            } catch (Exception e) {
                if (!resp.isCommitted()) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    resp.setContentType("application/json");
                    resp.getWriter().write("{\"error\": \"Unauthorized: Invalid token\"}");
                }
                return;
            }
        }

        if (!resp.isCommitted()) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"Unauthorized: Missing or malformed token\"}");
        }
    }
}
