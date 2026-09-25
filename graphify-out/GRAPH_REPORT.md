# Graph Report - /home/natsu/Documents/Sistema Paqueteria/Herramientas-Backend  (2026-09-11)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 132 nodes · 211 edges · 20 communities (12 shown, 8 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 11 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `efb672f2`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 19|Community 19]]

## God Nodes (most connected - your core abstractions)
1. `User` - 10 edges
2. `JwtTokenProvider` - 6 edges
3. `Override` - 6 edges
4. `UserDetails` - 5 edges
5. `AuthController` - 5 edges
6. `ResponseEntity` - 5 edges
7. `GlobalExceptionHandler` - 5 edges
8. `ResponseEntity` - 5 edges
9. `Map` - 5 edges
10. `ExceptionHandler` - 5 edges

## Surprising Connections (you probably didn't know these)
- `PostgreSQL Datasource` --shares_data_with--> `PostgreSQL Service`  [INFERRED]
  src/main/resources/application.yml → docker-compose.yml
- `User` --implements--> `UserDetails`  [EXTRACTED]
  src/main/java/com/logistics/proyect/group5/model/User.java → src/main/java/com/logistics/proyect/group5/config/CustomUserDetailsService.java

## Import Cycles
- None detected.

## Communities (20 total, 8 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.19
Nodes (12): CustomUserDetailsService, JwtAuthenticationFilter, FilterChain, HttpServletRequest, HttpServletResponse, OncePerRequestFilter, Override, String (+4 more)

### Community 1 - "Community 1"
Cohesion: 0.18
Nodes (7): Collection, GrantedAuthority, User, PrePersist, PreUpdate, Override, String

### Community 2 - "Community 2"
Cohesion: 0.32
Nodes (10): BadCredentialsException, Exception, GlobalExceptionHandler, ExceptionHandler, IllegalArgumentException, Map, MethodArgumentNotValidException, Object (+2 more)

### Community 3 - "Community 3"
Cohesion: 0.25
Nodes (10): AuthController, GetMapping, PostMapping, AuthResponse, LoginRequest, RegisterRequest, ResponseEntity, User (+2 more)

### Community 4 - "Community 4"
Cohesion: 0.36
Nodes (5): Authentication, JwtTokenProvider, SecretKey, String, User

### Community 5 - "Community 5"
Cohesion: 0.42
Nodes (6): AuthService, AuthResponse, LoginRequest, RegisterRequest, User, UserSummaryDto

### Community 6 - "Community 6"
Cohesion: 0.43
Nodes (5): AuthenticationConfiguration, AuthenticationManager, PasswordEncoderConfig, PasswordEncoder, Bean

### Community 7 - "Community 7"
Cohesion: 0.46
Nodes (5): SecurityConfig, CorsConfigurationSource, HttpSecurity, SecurityFilterChain, Bean

### Community 8 - "Community 8"
Cohesion: 0.43
Nodes (4): Optional, UserRepository, String, User

### Community 9 - "Community 9"
Cohesion: 0.50
Nodes (5): Spring Application Configuration, PostgreSQL Datasource, Docker Compose Configuration, pgAdmin Service, PostgreSQL Service

## Knowledge Gaps
- **19 isolated node(s):** `String`, `String`, `Override`, `Override`, `String` (+14 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `User` connect `Community 1` to `Community 0`?**
  _High betweenness centrality (0.117) - this node is a cross-community bridge._
- **Why does `UserDetails` connect `Community 0` to `Community 1`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **What connects `String`, `String`, `Override` to the rest of the system?**
  _19 weakly-connected nodes found - possible documentation gaps or missing edges._