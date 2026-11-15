package ro.stucanii.aissociation.identity;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerSessionFilter extends OncePerRequestFilter {
    private final IdentityService service;
    public BearerSessionFilter(IdentityService service) { this.service = service; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                unauthorized(response); return;
            }
            var user = service.authenticate(header.substring(7));
            if (user.isEmpty()) { unauthorized(response); return; }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.orElseThrow(), null, List.of()));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    public static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Sign in with a valid session.\",\"fieldErrors\":{}}");
    }
}
