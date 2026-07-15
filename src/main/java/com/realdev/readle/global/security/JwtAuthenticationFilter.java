package com.realdev.readle.global.security;

import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final SecurityErrorResponseWriter errorResponseWriter;

  public JwtAuthenticationFilter(
      JwtService jwtService, SecurityErrorResponseWriter errorResponseWriter) {
    this.jwtService = jwtService;
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      String memberUuid = jwtService.memberUuid(header.substring(7));
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(memberUuid, null, List.of()));
      filterChain.doFilter(request, response);
    } catch (CustomException exception) {
      SecurityContextHolder.clearContext();
      errorResponseWriter.write(response, GlobalErrorCode.UNAUTHORIZED);
    }
  }
}
