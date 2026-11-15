# ADR 0001: Database-backed opaque bearer sessions

Status: implemented for the first account slice.

The frontend and backend use different hosting origins. Use an explicit bearer
header with a server-side session rather than cookie-dependent authentication.
Generate a secure-random token, persist only its hash with expiry/user linkage,
and resolve it on every request. Keep mutable membership permissions outside
the token. BCrypt stores account passwords.

This makes current-session logout effective immediately and keeps permission
changes independent of token claims. It adds a database lookup per authenticated
request and requires cleanup/retention management for expired session records as
usage grows. The client stores tokens in memory; refresh requires sign-in again.
It does not provide a complete OAuth provider, refresh flow or password reset.

Only explicitly public read endpoints bypass authentication. Each feature owner
still must implement association/project authorization. The existing CORS policy
remains origin-specific and is evaluated inside Spring Security before bearer
session processing. No browser cookie authenticates requests, so cookie-oriented
CSRF protection is not enabled for this API.

References: Spring Security architecture and Spring Boot database initialization.
- https://docs.spring.io/spring-security/reference/servlet/architecture.html
- https://docs.spring.io/spring-boot/how-to/data-initialization.html
