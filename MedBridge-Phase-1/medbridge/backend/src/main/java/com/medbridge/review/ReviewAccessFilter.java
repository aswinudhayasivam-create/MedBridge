package com.medbridge.review;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Review isolation, expiry and rate limits supplement password/JWT role authentication. */
@Component
public class ReviewAccessFilter extends OncePerRequestFilter {
    private final ReviewModePolicy policy;
    private final Map<String,Window> clients=new HashMap<>();
    private record Window(long minute,int total,int logins) {}
    public ReviewAccessFilter(ReviewModePolicy policy) { this.policy=policy; }
    private synchronized boolean allow(String remote,boolean login) {
        long minute=policy.now().getEpochSecond()/60;
        clients.entrySet().removeIf(entry -> entry.getValue().minute()!=minute);
        if (!clients.containsKey(remote) && clients.size()>=2048) return false;
        Window prior=clients.getOrDefault(remote,new Window(minute,0,0));
        Window next=new Window(minute,prior.total()+1,prior.logins()+(login?1:0));clients.put(remote,next);
        return next.total()<=300 && (!login || next.logins()<=10);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        if(!policy.requested() || !request.getRequestURI().startsWith("/api/")) { chain.doFilter(request,response);return; }
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Robots-Tag","noindex, nofollow");
        if(!policy.ready()) { reject(response,503,"Review access is unavailable.");return; }
        String path=request.getRequestURI();
        if("GET".equals(request.getMethod()) && (path.equals("/api/health") || path.equals("/api/review/ready"))) { chain.doFilter(request,response);return; }
        // Use only the socket peer. Untrusted Forwarded/X-Forwarded-For cannot evade this limiter.
        if(!allow(request.getRemoteAddr(),path.equals("/api/auth/login"))) { response.setHeader("Retry-After","60");reject(response,429,"Too many requests. Try again later.");return; }
        if("OPTIONS".equals(request.getMethod())) { chain.doFilter(request,response);return; }
        if(path.equals("/api/auth/register")) { reject(response,503,"Registration is unavailable in this environment.");return; }
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String error)throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"error\":\""+error+"\"}");
    }
}
