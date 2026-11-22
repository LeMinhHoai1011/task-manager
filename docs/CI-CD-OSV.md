# SmartTask Maven OSV triage

Rà soát nhánh `codex/ci-cd` trên graph Maven `compile` và `runtime`. Hai scope này đều có thể được đóng gói vào ứng dụng. Dependency test nằm ngoài phạm vi phép quét hiện tại. App dùng Tomcat nhúng, Spring MVC, phiên Bearer, PostgreSQL qua JDBC và MinIO qua SDK Java. Phân loại dưới đây dựa vào advisory, graph Maven và đường gọi trong repository. Kết luận về phạm vi áp dụng không thay đổi kết quả gate.

## Baseline

2026-11-22T05:37:24.6071417Z UTC theo báo cáo gốc. 109 dependency được quét, 107 cặp dependency–advisory, 106 GHSA và 106 CVE có alias. Mức của GHSA duy nhất theo `database_specific.severity`: 11 CRITICAL, 39 HIGH, 42 MODERATE, 14 LOW.

GHSA-4gc7-5j7h-4qph (CVE-2024-38820) xuất hiện ở cả `spring-context` và `spring-web`. Đếm một advisory khi phân loại mức độ và giữ hai dòng dependency khi xác minh bản vá. Các CVE về bản vá chưa đầy đủ vẫn là các CVE riêng. Alias BIT và CVE của cùng bản ghi OSV không được cộng thêm.

| Dependency | Phiên bản | Nguồn và scope | CRITICAL | HIGH | MODERATE | LOW |
|---|---|---|---:|---:|---:|---:|
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.20 | Gián tiếp, compile | 8 | 18 | 7 | 5 |
| `org.springframework:spring-webmvc` | 6.1.6 | Gián tiếp, compile | 1 | 4 | 8 | 2 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.15.4 | Gián tiếp, compile | 0 | 5 | 7 | 0 |
| `ch.qos.logback:logback-core` | 1.4.14 | Gián tiếp, compile | 0 | 0 | 2 | 4 |
| `org.bouncycastle:bcprov-jdk15on` | 1.70 | Gián tiếp, compile | 0 | 0 | 5 | 0 |
| `org.xerial.snappy:snappy-java` | 1.1.10.1 | Gián tiếp, compile | 0 | 3 | 1 | 0 |
| `com.fasterxml.jackson.core:jackson-core` | 2.15.4 | Gián tiếp, compile | 0 | 2 | 1 | 0 |
| `org.springframework.security:spring-security-web` | 6.2.4 | Gián tiếp, compile | 2 | 0 | 1 | 0 |
| `org.springframework:spring-expression` | 6.1.6 | Gián tiếp, compile | 0 | 1 | 1 | 1 |
| `org.springframework:spring-web` | 6.1.6 | Gián tiếp, compile | 0 | 0 | 3 | 0 |
| `org.springframework.data:spring-data-commons` | 3.2.5 | Gián tiếp, compile | 0 | 0 | 2 | 0 |
| `org.springframework:spring-context` | 6.1.6 | Gián tiếp, compile | 0 | 0 | 1 | 1 |
| `org.springframework:spring-core` | 6.1.6 | Gián tiếp, compile | 0 | 1 | 0 | 1 |
| `com.google.guava:guava` | 32.0.0-jre | Gián tiếp, compile | 0 | 1 | 0 | 0 |
| `io.minio:minio` | 8.5.4 | Trực tiếp, compile | 0 | 1 | 0 | 0 |
| `org.apache.commons:commons-lang3` | 3.13.0 | Gián tiếp, compile | 0 | 0 | 1 | 0 |
| `org.apache.logging.log4j:log4j-api` | 2.21.1 | Gián tiếp, compile | 0 | 0 | 1 | 0 |
| `org.apache.poi:poi-ooxml` | 5.3.0 | Trực tiếp, compile | 0 | 0 | 1 | 0 |
| `org.postgresql:postgresql` | 42.6.2 | Trực tiếp, runtime | 0 | 1 | 0 | 0 |
| `org.springframework.boot:spring-boot` | 3.2.5 | Gián tiếp, compile | 0 | 1 | 0 | 0 |
| `org.springframework.security:spring-security-core` | 6.2.4 | Gián tiếp, compile | 0 | 0 | 1 | 0 |
| `org.springframework.security:spring-security-crypto` | 6.2.4 | Gián tiếp, compile | 0 | 1 | 0 | 0 |

## Phạm vi áp dụng

| Nhóm | Đánh giá trên mã nguồn và cấu hình |
|---|---|
| Tomcat nhúng và Spring MVC | HTTP parser, multipart và SSE có đường chạy thực tế. Ưu tiên bản vá HTTP và multipart. Với CVE-2026-24880, đường Caddy → Tomcat có thật nhưng chưa xác minh điều kiện chuyển tiếp cụ thể. Spring stream dùng `SseEmitter`; phân biệt CVE-2026-22735 về luồng SSE với CVE-2026-47890 về view fragments. |
| Spring Security | `IdentityService` dùng BCrypt và `SecurityConfig` chạy trên Servlet. CVE-2025-22228 liên quan trực tiếp đến xác minh mật khẩu. Khả năng ảnh hưởng của từng điều kiện ghi header cần đánh giá riêng. CVE-2024-38821 yêu cầu WebFlux phục vụ static resource, trong khi classpath và cấu hình hiện tại dùng MVC và Caddy. |
| JDBC và MinIO | Hai dependency trực tiếp tham gia runtime. Các advisory SCRAM và XML phụ thuộc dữ liệu từ endpoint DB hoặc S3. Compose dùng DB và MinIO nội bộ, giúp giới hạn bề mặt nhưng vẫn cần vá SDK/driver. |
| Jackson | Dependency gián tiếp nhưng xử lý API JSON. Các ca polymorphic/default typing, lớp đích `InetAddress`, async parser và annotation đặc thù cần các đường gọi riêng. Chưa thấy bật default typing hay ánh xạ các lớp đích nêu trên trong mã ứng dụng. Chưa xác nhận toàn bộ điều kiện của converter và thư viện. |
| POI | Dependency trực tiếp. CVE-2025-31672 cần đọc OOXML do bên ngoài cung cấp. `ExportWorker` tạo workbook mới để xuất báo cáo. Không thấy luồng đọc workbook upload. POI và POI OOXML được nâng cùng phiên bản. |
| Bouncy Castle, Guava, Snappy | Đi qua MinIO SDK. Chưa thấy app gọi trực tiếp các bộ phân tích, chế độ crypto hoặc Java object deserialization được nêu. Mức độ khai thác còn phụ thuộc đường gọi trong SDK, vì vậy giữ nguyên gate và nâng cả nhóm. |
| Logback, Log4j API, Commons Lang | Có trong runtime. Nhiều điều kiện đòi hỏi cấu hình log do bên ngoài kiểm soát, receiver/deserialization hoặc API cụ thể. Cấu hình thực tế được đối chiếu ở mức repository, chưa xác minh mọi cấu hình host riêng. |
| Tính năng không thấy trong stack hiện tại | Toàn bộ triển khai công bố ở đây dùng Linux/Caddy, Bearer và MVC REST. Không thấy Tomcat Windows installer, AJP, CGI/WebDAV, DIGEST/FORM realm, writable Default Servlet hoặc session persistence kiểu Tomcat trong cấu hình đã đọc. Những advisory cần đúng các tính năng này không áp dụng cho cấu hình đó. Trường hợp Tomcat khác vẫn giữ trạng thái cần xem xét theo điều kiện riêng. |
| Spring Boot Actuator | CVE-2025-22235 (`GHSA-rc42-6c7j-7h5r`) cần matcher `EndpointRequest`. Health của SmartTask do `HealthController` phục vụ; không thấy Actuator hay matcher đó. |

## Thay đổi và hướng xử lý

- Đợt 1 nâng Spring Boot 3.2.5 → 3.5.16, MinIO Java 8.5.4 → 8.6.0, POI/POI OOXML 5.3.0 → 5.5.1, Commons Compress 1.26.2 → 1.28.0 và thêm module Flyway PostgreSQL do BOM quản lý. Graph sau nâng có 32 lượt khớp OSV. Build backend phát hiện Maven thiếu artifact JVM của OkHttp 5 mà MinIO 8.6 dùng. [Spring Boot 3.5.16](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/) · [MinIO Java 8.6.0](https://github.com/minio/minio-java/releases/tag/8.6.0) · [Apache POI Security](https://poi.apache.org/security.html)
- Đợt 2 bổ sung OkHttp JVM 5.1.0 và Kotlin 2.2.0 đúng theo POM OkHttp. Bản vá đồng bộ ở đợt này gồm Tomcat 10.1.62, Jackson BOM 2.21.8, pgJDBC 42.7.14, Log4j BOM 2.26.1, Bouncy Castle 1.87, Guava 33.8.0-jre, Snappy 1.1.10.12 và Commons Lang 3.22.0.
- Đợt 2 còn 2 lượt khớp trên 106 dependency. Spring Boot 3.5.16 là bản OSS cuối của dòng 3.5. Hai advisory Spring MVC cần xem riêng. GHSA xếp cả hai CRITICAL. Spring xếp CVE-2026-47884 MEDIUM và CVE-2026-47890 LOW vì các điều kiện dưới đây. Bản sửa có trong Spring Framework 7.0.9 OSS hoặc 6.2.20 Enterprise. Đợt 3 chuyển sang Spring Boot 4.1.3 với Spring Framework 7.0.11. [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) · [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) · [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

| Advisory Spring MVC | Điều kiện của nhà cung cấp | Đối chiếu SmartTask |
|---|---|---|
| [GHSA-pc63-qcmh-9cmg](https://osv.dev/vulnerability/GHSA-pc63-qcmh-9cmg) / CVE-2026-47884 | `XsltView`, mapping rộng dẫn tới render view và tên view ngầm định | Không thấy `XsltView`, resolver XSLT hoặc route backend render view ngầm. SPA do Caddy phục vụ. |
| [GHSA-j9f9-w8pj-32f8](https://osv.dev/vulnerability/GHSA-j9f9-w8pj-32f8) / CVE-2026-47890 | SSE dùng view fragments và dữ liệu đưa đến người khác do attacker kiểm soát | `RealtimeRegistry` gửi heartbeat và bản đồ dữ liệu JSON qua `SseEmitter`. Không thấy render view fragment. |

Ở đợt 2, hai phiên bản bị match vẫn chặn gate dù chưa thấy đường kích hoạt trong stack đã đối chiếu. Ở đợt 3, OSV trả 0 lượt khớp trên 125 dependency. Không thêm ngoại lệ OSV.

Spring Boot 4 chuyển các module MVC, Flyway và test sang starter riêng. SmartTask chọn Jackson 2 cho HTTP để giữ đường tuần tự hóa API hiện tại. Đây là cấu hình tương thích tạm thời vì Boot 4 đã đánh dấu Jackson 2 là deprecated. Flyway 12 dùng module PostgreSQL riêng, còn migration SQL giữ nguyên. [Tài liệu Jackson của Spring Boot](https://docs.spring.io/spring-boot/reference/features/json.html)

## Kết quả kiểm thử đợt 3

| Kiểm tra | Kết quả cục bộ |
|---|---|
| OSV Maven | PASS, 125 dependency runtime và 0 lượt khớp. Không có ngoại lệ OSV. |
| Backend | PASS, Maven ghi nhận 561 ca, trong đó 555 chạy thành công và 6 skip đã duyệt. PostgreSQL, MinIO và Testcontainers chạy trên dữ liệu kiểm thử. Hai XML bị tiến trình kiểm thử đợt trước ghi đè đã được tạo lại bằng cùng phiên bản Boot 4.1.3. Bộ xác thực báo cáo xác nhận không có skip ngoài danh sách. |
| Frontend | PASS, 82 test, lint, typecheck và audit pnpm. |
| Image và bảo mật | PASS, hai Docker build, Gitleaks lịch sử Git và save/load giữ nguyên ID của cả hai image. |
| Backup và rollback | PASS trên Compose fixture. Backup đã xác minh trước khi đổi image. Flyway history và ID container PostgreSQL/MinIO khớp sau nâng. Smoke xác nhận health, đăng nhập, phân quyền, SSE, upload và download trên image mới và sau khi quay về image cũ. |
| Workflow | PASS actionlint và 10 tổ hợp mô phỏng success/failure/skipped/cancelled. Publish phụ thuộc release gate. Deploy phụ thuộc cả release gate và publish. |

Các kết quả trên chạy cục bộ. Xác nhận GitHub Actions trên commit được duyệt vẫn là bước trước khi kích hoạt phát hành tự động.

## Danh mục GHSA/CVE gốc

Mỗi mục chứa các dependency đã khớp trong baseline, phiên bản nằm ở bảng trên. Mức lấy từ GHSA qua OSV. Dấu — ở cột CVE nghĩa là metadata chưa có alias CVE.

| GHSA | CVE alias | Mức | Dependency | Mô tả của nguồn |
|---|---|---|---|---|---|
| [GHSA-5m62-pw8w-7w9f](https://osv.dev/vulnerability/GHSA-5m62-pw8w-7w9f) | CVE-2026-43515 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - Security constraints not correctly applied |
| [GHSA-83qj-6fr2-vhqg](https://osv.dev/vulnerability/GHSA-83qj-6fr2-vhqg) | CVE-2025-24813 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat: Potential RCE and/or information disclosure and/or information corruption with partial PUT |
| [GHSA-9xv2-5v5q-p794](https://osv.dev/vulnerability/GHSA-9xv2-5v5q-p794) | CVE-2026-65905 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat's DIGEST authenticator has an Authentication Bypass by Capture-replay vulnerability |
| [GHSA-c4q5-6c82-3qpw](https://osv.dev/vulnerability/GHSA-c4q5-6c82-3qpw) | CVE-2024-38821 | CRITICAL | `org.springframework.security:spring-security-web` | Spring Security vulnerable to Authorization Bypass of Static Resources in WebFlux Applications |
| [GHSA-gcx9-497g-6cp6](https://osv.dev/vulnerability/GHSA-gcx9-497g-6cp6) | CVE-2026-65182 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat has an Improper Access Control, Incorrect Authorization vulnerability |
| [GHSA-h3x4-894j-xpx5](https://osv.dev/vulnerability/GHSA-h3x4-894j-xpx5) | CVE-2026-68525 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat's FORM authentication process has an Incorrect Authorization vulnerability |
| [GHSA-h6fc-48rj-7qqh](https://osv.dev/vulnerability/GHSA-h6fc-48rj-7qqh) | CVE-2026-43512 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - Digest authenticator will authenticate any unknown user |
| [GHSA-mf92-479x-3373](https://osv.dev/vulnerability/GHSA-mf92-479x-3373) | CVE-2026-22732 | CRITICAL | `org.springframework.security:spring-security-web` | Spring Security HTTP Headers Are not Written Under Some Conditions |
| [GHSA-mqg3-r7h5-24x4](https://osv.dev/vulnerability/GHSA-mqg3-r7h5-24x4) | CVE-2026-55276 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat has an Always-Incorrect Control Flow Implementation vulnerability |
| [GHSA-pc63-qcmh-9cmg](https://osv.dev/vulnerability/GHSA-pc63-qcmh-9cmg) | CVE-2026-47884 | CRITICAL | `org.springframework:spring-webmvc` | Spring Framework Improper Path Limitation in XsltView |
| [GHSA-r29c-68gh-xp6x](https://osv.dev/vulnerability/GHSA-r29c-68gh-xp6x) | CVE-2026-41293 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - HTTP/2 request headers not validated |
| [GHSA-25xr-qj8w-c4vf](https://osv.dev/vulnerability/GHSA-25xr-qj8w-c4vf) | CVE-2025-53506 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Coyote vulnerable to Denial of Service via excessive HTTP/2 streams |
| [GHSA-27hp-xhwr-wr2m](https://osv.dev/vulnerability/GHSA-27hp-xhwr-wr2m) | CVE-2024-56337 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Time-of-check Time-of-use (TOCTOU) Race Condition vulnerability |
| [GHSA-3chg-m5w7-qfv5](https://osv.dev/vulnerability/GHSA-3chg-m5w7-qfv5) | CVE-2026-41845 | HIGH | `org.springframework:spring-webmvc` | Spring Framework Cross-site Scripting via JavaScriptUtils |
| [GHSA-55g7-9cwv-5qfv](https://osv.dev/vulnerability/GHSA-55g7-9cwv-5qfv) | CVE-2023-43642 | HIGH | `org.xerial.snappy:snappy-java` | snappy-java's missing upper bound check on chunk length can lead to Denial of Service (DoS) impact |
| [GHSA-563x-q5rq-57qp](https://osv.dev/vulnerability/GHSA-563x-q5rq-57qp) | CVE-2026-24880 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat has an HTTP Request/Response Smuggling vulnerability |
| [GHSA-5j33-cvvr-w245](https://osv.dev/vulnerability/GHSA-5j33-cvvr-w245) | CVE-2024-50379 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Time-of-check Time-of-use (TOCTOU) Race Condition vulnerability |
| [GHSA-5mp6-jrq3-r938](https://osv.dev/vulnerability/GHSA-5mp6-jrq3-r938) | CVE-2026-43513 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat: LockOutRealm treats user names as case-sensitive |
| [GHSA-7895-gffq-w6jq](https://osv.dev/vulnerability/GHSA-7895-gffq-w6jq) | CVE-2026-55957 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat has a Missing Critical Step in Authentication vulnerability |
| [GHSA-7hhh-6rmp-j9qf](https://osv.dev/vulnerability/GHSA-7hhh-6rmp-j9qf) | CVE-2026-89425 | HIGH | `com.fasterxml.jackson.core:jackson-core` | jackson-core: UTF8DataInputJsonParser._reportInvalidToken() missing maxErrorTokenLength limit -> unbounded StringBuilder growth (DoS) |
| [GHSA-7jqf-v358-p8g7](https://osv.dev/vulnerability/GHSA-7jqf-v358-p8g7) | CVE-2024-38286 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Allocation of Resources Without Limits or Throttling vulnerability |
| [GHSA-98qh-xjc8-98pq](https://osv.dev/vulnerability/GHSA-98qh-xjc8-98pq) | CVE-2026-42198 | HIGH | `org.postgresql:postgresql` | pgjdbc: Unbounded PBKDF2 iterations in SCRAM authentication allows CPU exhaustion DoS |
| [GHSA-cx7f-g6mp-7hqm](https://osv.dev/vulnerability/GHSA-cx7f-g6mp-7hqm) | CVE-2024-38816 | HIGH | `org.springframework:spring-webmvc` | Path traversal vulnerability in functional web frameworks |
| [GHSA-cxp5-3px4-pw24](https://osv.dev/vulnerability/GHSA-cxp5-3px4-pw24) | CVE-2026-91777 | HIGH | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind quadratic forward-reference completion |
| [GHSA-fv25-8xcx-gqjc](https://osv.dev/vulnerability/GHSA-fv25-8xcx-gqjc) | CVE-2026-42498 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - WebSocket authentication header exposure |
| [GHSA-g5vr-rgqm-vf78](https://osv.dev/vulnerability/GHSA-g5vr-rgqm-vf78) | CVE-2024-38819 | HIGH | `org.springframework:spring-webmvc` | Spring Framework Path Traversal vulnerability |
| [GHSA-gqp3-2cvr-x8m3](https://osv.dev/vulnerability/GHSA-gqp3-2cvr-x8m3) | CVE-2025-48989 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Improper Resource Shutdown or Release vulnerability |
| [GHSA-gwq9-9ffj-mvrv](https://osv.dev/vulnerability/GHSA-gwq9-9ffj-mvrv) | CVE-2026-93451 | HIGH | `org.xerial.snappy:snappy-java` | snappy-java: Heap buffer overflow in Snappy.uncompress*Array typed-array methods |
| [GHSA-gx5v-xp9w-j4cg](https://osv.dev/vulnerability/GHSA-gx5v-xp9w-j4cg) | CVE-2026-41284 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat: Unbounded read in WebDAV LOCK and PROPFIND handling |
| [GHSA-h3gc-qfqq-6h8f](https://osv.dev/vulnerability/GHSA-h3gc-qfqq-6h8f) | CVE-2025-48988 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - DoS in multipart upload |
| [GHSA-h792-v28v-ppgr](https://osv.dev/vulnerability/GHSA-h792-v28v-ppgr) | CVE-2026-53404 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat's rewrite valve has an Always-Incorrect Control Flow Implementation vulnerability |
| [GHSA-h7rh-xfpj-hpcm](https://osv.dev/vulnerability/GHSA-h7rh-xfpj-hpcm) | CVE-2025-59952 | HIGH | `io.minio:minio` | MinIO Java Client XML Tag Value Substitution Vulnerability |
| [GHSA-j3rv-43j4-c7qm](https://osv.dev/vulnerability/GHSA-j3rv-43j4-c7qm) | CVE-2026-54512 | HIGH | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind has a PolymorphicTypeValidator bypass via generic type parameters that allows arbitrary class instantiation |
| [GHSA-jmp9-x22r-554x](https://osv.dev/vulnerability/GHSA-jmp9-x22r-554x) | CVE-2025-41249 | HIGH | `org.springframework:spring-core` | Spring Framework annotation detection mechanism may result in improper authorization |
| [GHSA-mg83-c7gq-rv5c](https://osv.dev/vulnerability/GHSA-mg83-c7gq-rv5c) | CVE-2025-22228 | HIGH | `org.springframework.security:spring-security-crypto` | Spring Security Does Not Enforce Password Length |
| [GHSA-mgp5-rv84-w37q](https://osv.dev/vulnerability/GHSA-mgp5-rv84-w37q) | CVE-2026-24734 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat has an Improper Input Validation vulnerability |
| [GHSA-q233-g92q-qxp6](https://osv.dev/vulnerability/GHSA-q233-g92q-qxp6) | CVE-2026-108106 | HIGH | `org.xerial.snappy:snappy-java` | snappy-java: Unbounded memory allocation from attacker-declared uncompressed lengtha |
| [GHSA-q4xh-88c3-wmh7](https://osv.dev/vulnerability/GHSA-q4xh-88c3-wmh7) | CVE-2026-68497 | HIGH | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Duration XMLGregorianCalendar Unbounded Number Parse DoS |
| [GHSA-r5w3-xv2f-j59q](https://osv.dev/vulnerability/GHSA-r5w3-xv2f-j59q) | CVE-2026-41850 | HIGH | `org.springframework:spring-expression` | Spring Framework Algorithmic Denial of Service via SpEL Expressions |
| [GHSA-r7wm-3cxj-wff9](https://osv.dev/vulnerability/GHSA-r7wm-3cxj-wff9) | CVE-2026-68494 | HIGH | `com.fasterxml.jackson.core:jackson-core` | jackson-core: Async parser maxNumberLength bypass via chunked digit accumulation (incomplete fix for GHSA-72hv-8253-57qq) |
| [GHSA-rc42-6c7j-7h5r](https://osv.dev/vulnerability/GHSA-rc42-6c7j-7h5r) | CVE-2025-22235 | HIGH | `org.springframework.boot:spring-boot` | Spring Boot EndpointRequest.to() creates wrong matcher if actuator endpoint is not exposed |
| [GHSA-rmj7-2vxq-3g9f](https://osv.dev/vulnerability/GHSA-rmj7-2vxq-3g9f) | CVE-2026-54513 | HIGH | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind has an array subtype allowlist bypass in BasicPolymorphicTypeValidator (allowIfSubTypeIsArray) |
| [GHSA-rv64-5gf8-9qq8](https://osv.dev/vulnerability/GHSA-rv64-5gf8-9qq8) | CVE-2026-34483 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat has an Improper Encoding or Escaping of Output vulnerability in the JsonAccessLogValve |
| [GHSA-wm9w-rjj3-j356](https://osv.dev/vulnerability/GHSA-wm9w-rjj3-j356) | CVE-2024-34750 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - Denial of Service |
| [GHSA-wmwf-9ccg-fff5](https://osv.dev/vulnerability/GHSA-wmwf-9ccg-fff5) | CVE-2025-55752 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Vulnerable to Relative Path Traversal |
| [GHSA-wr62-c79q-cv37](https://osv.dev/vulnerability/GHSA-wr62-c79q-cv37) | CVE-2025-52520 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Catalina is vulnerable to DoS attack through bypassing of size limits |
| [GHSA-wv8q-qhhj-9h54](https://osv.dev/vulnerability/GHSA-wv8q-qhhj-9h54) | CVE-2026-91776 | HIGH | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind retains every unknown raw type ID |
| [GHSA-x23c-287f-qqv5](https://osv.dev/vulnerability/GHSA-x23c-287f-qqv5) | CVE-2026-41842 | HIGH | `org.springframework:spring-webmvc` | Spring Framework Denial of Service via Versioned Resources in Spring MVC and WebFlux |
| [GHSA-x4m4-345f-5h5g](https://osv.dev/vulnerability/GHSA-x4m4-345f-5h5g) | CVE-2026-34487 | HIGH | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat vulnerable to Insertion of Sensitive Information into Log File |
| [GHSA-xxph-c9ww-hj94](https://osv.dev/vulnerability/GHSA-xxph-c9ww-hj94) | CVE-2026-102554 | HIGH | `com.google.guava:guava` | Google Guava: CompactHashMap/CompactHashSet/MapMakerInternalMap deserialization triggers uncontrolled memory allocation |
| [GHSA-23hv-mwm6-g8jf](https://osv.dev/vulnerability/GHSA-23hv-mwm6-g8jf) | CVE-2025-55668 | MODERATE | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Session Fixation vulnerability |
| [GHSA-25qh-j22f-pwp8](https://osv.dev/vulnerability/GHSA-25qh-j22f-pwp8) | CVE-2025-11226 | MODERATE | `ch.qos.logback:logback-core` | QOS.CH logback-core is vulnerable to Arbitrary Code Execution through file processing |
| [GHSA-293q-567p-wmwq](https://osv.dev/vulnerability/GHSA-293q-567p-wmwq) | CVE-2026-47838 | MODERATE | `org.springframework.security:spring-security-web` | Spring Security Vulnerable to Unauthorized User Impersonation when Using X.509 Client Certificates |
| [GHSA-2rmj-mq67-h97g](https://osv.dev/vulnerability/GHSA-2rmj-mq67-h97g) | CVE-2024-38809 | MODERATE | `org.springframework:spring-web` | Spring Framework DoS via conditional HTTP request |
| [GHSA-3p2h-wqq4-wf4h](https://osv.dev/vulnerability/GHSA-3p2h-wqq4-wf4h) | CVE-2025-31650 | MODERATE | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Denial of Service via invalid HTTP priority header |
| [GHSA-3pjw-73gf-8qr5](https://osv.dev/vulnerability/GHSA-3pjw-73gf-8qr5) | CVE-2026-59888 | MODERATE | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: @JsonIgnore on a Record property is bypassed with a PropertyNamingStrategy |
| [GHSA-42wg-hm62-jcwg](https://osv.dev/vulnerability/GHSA-42wg-hm62-jcwg) | CVE-2025-49124 | MODERATE | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat installer for Windows has an untrusted search path vulnerability |
| [GHSA-4773-3jfm-qmx3](https://osv.dev/vulnerability/GHSA-4773-3jfm-qmx3) | CVE-2026-22737 | MODERATE | `org.springframework:spring-webmvc` | Spring Framework Improper Path Limitation with Script View Templates |
| [GHSA-4gc7-5j7h-4qph](https://osv.dev/vulnerability/GHSA-4gc7-5j7h-4qph) | CVE-2024-38820 | MODERATE | `org.springframework:spring-web`, `org.springframework:spring-context` | Spring Framework DataBinder Case Sensitive Match Exception |
| [GHSA-4h8f-2wvx-gg5w](https://osv.dev/vulnerability/GHSA-4h8f-2wvx-gg5w) | CVE-2024-34447 | MODERATE | `org.bouncycastle:bcprov-jdk15on` | Bouncy Castle Java Cryptography API vulnerable to DNS poisoning |
| [GHSA-5gvw-p9qm-jgwh](https://osv.dev/vulnerability/GHSA-5gvw-p9qm-jgwh) | CVE-2026-59889 | MODERATE | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: @JsonView bypassed for @JsonUnwrapped container properties on deserialization |
| [GHSA-5jmj-h7xm-6q6v](https://osv.dev/vulnerability/GHSA-5jmj-h7xm-6q6v) | CVE-2026-54515 | MODERATE | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind has case-insensitive deserialization bypasses per-property @JsonIgnoreProperties |
| [GHSA-5m4m-73w9-8433](https://osv.dev/vulnerability/GHSA-5m4m-73w9-8433) | CVE-2026-41721 | MODERATE | `org.springframework.data:spring-data-commons` | Spring Data Commons: Denial of Service via excessive memory allocation in projection binding |
| [GHSA-5v3h-fjv2-54fg](https://osv.dev/vulnerability/GHSA-5v3h-fjv2-54fg) | CVE-2026-55956 | MODERATE | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat has an Improper Authorization vulnerability |
| [GHSA-5vpf-xvv7-c8vh](https://osv.dev/vulnerability/GHSA-5vpf-xvv7-c8vh) | CVE-2026-41711 | MODERATE | `org.springframework.data:spring-data-commons` | Spring Data Commons: StackOverflowException when parsing Sort parameters (DoS) |
| [GHSA-6p4f-wcwh-5vvm](https://osv.dev/vulnerability/GHSA-6p4f-wcwh-5vvm) | CVE-2026-22745 | MODERATE | `org.springframework:spring-webmvc` | Spring MVC and WebFlux applications are vulnerable to Denial of Service attacks when resolving static resources |
| [GHSA-6r3c-xf4w-jxjm](https://osv.dev/vulnerability/GHSA-6r3c-xf4w-jxjm) | CVE-2025-41234 | MODERATE | `org.springframework:spring-web` | Spring Framework vulnerable to a reflected file download (RFD) |
| [GHSA-72hv-8253-57qq](https://osv.dev/vulnerability/GHSA-72hv-8253-57qq) | CVE-2026-18401 | MODERATE | `com.fasterxml.jackson.core:jackson-core` | jackson-core: Number Length Constraint Bypass in Async Parser Leads to Potential DoS Condition |
| [GHSA-72pg-x5f8-j25j](https://osv.dev/vulnerability/GHSA-72pg-x5f8-j25j) | CVE-2026-41843 | MODERATE | `org.springframework:spring-webmvc` | Spring Framework Path Traversal via Versioned Static Resources in Spring MVC and WebFlux |
| [GHSA-8xfc-gm6g-vgpv](https://osv.dev/vulnerability/GHSA-8xfc-gm6g-vgpv) | CVE-2024-29857 | MODERATE | `org.bouncycastle:bcprov-jdk15on` | Bouncy Castle certificate parsing issues cause high CPU usage during parameter evaluation. |
| [GHSA-957g-f97v-vppc](https://osv.dev/vulnerability/GHSA-957g-f97v-vppc) | CVE-2026-41846 | MODERATE | `org.springframework:spring-webmvc` | Spring Framework Cross-site Scripting via JSP Form Tags |
| [GHSA-9m3c-qcxr-9x87](https://osv.dev/vulnerability/GHSA-9m3c-qcxr-9x87) | CVE-2026-25854 | MODERATE | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat has an Open Redirect vulnerability |
| [GHSA-cjpg-rgq5-fr37](https://osv.dev/vulnerability/GHSA-cjpg-rgq5-fr37) | CVE-2026-41853 | MODERATE | `org.springframework:spring-webmvc` | Spring Framework Multipart Request Smuggling in Spring MVC and WebFlux |
| [GHSA-fpj8-gq4v-p354](https://osv.dev/vulnerability/GHSA-fpj8-gq4v-p354) | CVE-2025-66614 | MODERATE | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - Client certificate verification bypass |
| [GHSA-gmg8-593g-7mv3](https://osv.dev/vulnerability/GHSA-gmg8-593g-7mv3) | CVE-2025-31672 | MODERATE | `org.apache.poi:poi-ooxml` | Apache POI OOXML Vulnerable to Improper Input Validation in OOXML File Parsing |
| [GHSA-gx83-3vf8-gh7j](https://osv.dev/vulnerability/GHSA-gx83-3vf8-gh7j) | CVE-2026-83557 | MODERATE | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Comparable missing from DefaultBaseTypeLimitingValidator's unsafe base types (incomplete PolymorphicTypeValidator denylist) |
| [GHSA-h3qp-gqrc-q736](https://osv.dev/vulnerability/GHSA-h3qp-gqrc-q736) | CVE-2026-41844 | MODERATE | `org.springframework:spring-webmvc` | Spring Framework Open Redirect in Spring MVC and WebFlux |
| [GHSA-hgj6-7826-r7m5](https://osv.dev/vulnerability/GHSA-hgj6-7826-r7m5) | CVE-2026-54514 | MODERATE | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: InetSocketAddress deserialization triggers eager DNS resolution (SSRF) |
| [GHSA-hr8g-6v94-x4m9](https://osv.dev/vulnerability/GHSA-hr8g-6v94-x4m9) | CVE-2023-33201 | MODERATE | `org.bouncycastle:bcprov-jdk15on` | Bouncy Castle For Java LDAP injection vulnerability |
| [GHSA-j288-q9x7-2f5v](https://osv.dev/vulnerability/GHSA-j288-q9x7-2f5v) | CVE-2025-48924 | MODERATE | `org.apache.commons:commons-lang3` | Apache Commons Lang is vulnerable to Uncontrolled Recursion when processing long inputs |
| [GHSA-mq64-j8f9-9gcj](https://osv.dev/vulnerability/GHSA-mq64-j8f9-9gcj) | CVE-2026-41841 | MODERATE | `org.springframework:spring-webmvc` | Spring Framework Information Disclosure via Static Resource Cache in Spring MVC and WebFlux |
| [GHSA-pr98-23f8-jwxv](https://osv.dev/vulnerability/GHSA-pr98-23f8-jwxv) | CVE-2024-12798 | MODERATE | `ch.qos.logback:logback-core` | QOS.CH logback-core Expression Language Injection vulnerability |
| [GHSA-q3v6-hm2v-pw99](https://osv.dev/vulnerability/GHSA-q3v6-hm2v-pw99) | CVE-2024-38827 | MODERATE | `org.springframework.security:spring-security-core` | Spring Framework has Authorization Bypass for Case Sensitive Comparisons |
| [GHSA-qv9r-c865-cp47](https://osv.dev/vulnerability/GHSA-qv9r-c865-cp47) | CVE-2026-49844 | MODERATE | `org.apache.logging.log4j:log4j-api` | Apache Log4j API: Improper encoding of non-finite floating-point values during MapMessage JSON serialization |
| [GHSA-r936-gwx5-v52f](https://osv.dev/vulnerability/GHSA-r936-gwx5-v52f) | CVE-2025-41242 | MODERATE | `org.springframework:spring-webmvc` | Spring Framework MVC Applications Path Traversal Vulnerability |
| [GHSA-v435-xc8x-wvr9](https://osv.dev/vulnerability/GHSA-v435-xc8x-wvr9) | CVE-2024-30171 | MODERATE | `org.bouncycastle:bcprov-jdk15on` | Bouncy Castle affected by timing side-channel for RSA key exchange ("The Marvin Attack") |
| [GHSA-vvgp-rfg2-7rr6](https://osv.dev/vulnerability/GHSA-vvgp-rfg2-7rr6) | CVE-2026-77310 | MODERATE | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Incomplete fix for CVE-2026-54514: eager DNS resolution (SSRF) still present in InetAddress deserialization |
| [GHSA-wc4r-xq3c-5cf3](https://osv.dev/vulnerability/GHSA-wc4r-xq3c-5cf3) | CVE-2025-49125 | MODERATE | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - Security constraint bypass for pre/post-resources |
| [GHSA-wjgm-6hv5-3cvf](https://osv.dev/vulnerability/GHSA-wjgm-6hv5-3cvf) | CVE-2026-19032 | MODERATE | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Path Deserialization Missing Scheme Allowlist for FileSystemProvider Resolution |
| [GHSA-wjxj-5m7g-mg7q](https://osv.dev/vulnerability/GHSA-wjxj-5m7g-mg7q) | CVE-2023-33202 | MODERATE | `org.bouncycastle:bcprov-jdk15on` | Bouncy Castle Denial of Service (DoS) |
| [GHSA-wxpp-56q6-5pcg](https://osv.dev/vulnerability/GHSA-wxpp-56q6-5pcg) | CVE-2026-41851 | MODERATE | `org.springframework:spring-expression` | Spring Framework Denial of Service via Unbounded Cache in SpEL |
| [GHSA-xqrx-qh46-34m7](https://osv.dev/vulnerability/GHSA-xqrx-qh46-34m7) | CVE-2026-116590 | MODERATE | `org.xerial.snappy:snappy-java` | snappy-java: JVM crash from unchecked ranges in Snappy.arrayCopy and SnappyOutputStream.rawWrite |
| [GHSA-4wp7-92pw-q264](https://osv.dev/vulnerability/GHSA-4wp7-92pw-q264) | CVE-2025-22233 | LOW | `org.springframework:spring-context` | Spring Framework DataBinder Case Sensitive Match Exception |
| [GHSA-659m-px2c-25wj](https://osv.dev/vulnerability/GHSA-659m-px2c-25wj) | CVE-2026-41848 | LOW | `org.springframework:spring-core` | Spring Framework Denial of Service via AntPathMatcher |
| [GHSA-6hcq-hmm3-jj3c](https://osv.dev/vulnerability/GHSA-6hcq-hmm3-jj3c) | CVE-2026-22735 | LOW | `org.springframework:spring-webmvc` | Spring MVC and WebFlux has Server Sent Event stream corruption |
| [GHSA-6v67-2wr5-gvf4](https://osv.dev/vulnerability/GHSA-6v67-2wr5-gvf4) | CVE-2024-12801 | LOW | `ch.qos.logback:logback-core` | QOS.CH logback-core Server-Side Request Forgery vulnerability |
| [GHSA-9f52-rjqv-25qv](https://osv.dev/vulnerability/GHSA-9f52-rjqv-25qv) | CVE-2026-41852 | LOW | `org.springframework:spring-expression` | Spring Framework Arbitrary Method Invocation in SpEL Expressions |
| [GHSA-9m89-8frq-c98c](https://osv.dev/vulnerability/GHSA-9m89-8frq-c98c) | CVE-2026-43514 | LOW | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - AJP secret compared in non-constant time |
| [GHSA-ff77-26x5-69cr](https://osv.dev/vulnerability/GHSA-ff77-26x5-69cr) | CVE-2025-31651 | LOW | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Rewrite rule bypass |
| [GHSA-h2fw-rfh5-95r3](https://osv.dev/vulnerability/GHSA-h2fw-rfh5-95r3) | CVE-2025-46701 | LOW | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat - CGI security constraint bypass |
| [GHSA-hgrr-935x-pq79](https://osv.dev/vulnerability/GHSA-hgrr-935x-pq79) | CVE-2025-61795 | LOW | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Vulnerable to Improper Resource Shutdown or Release |
| [GHSA-jhq6-gfmj-v8fx](https://osv.dev/vulnerability/GHSA-jhq6-gfmj-v8fx) | CVE-2026-10532 | LOW | `ch.qos.logback:logback-core` | Logback vulnerable to Object Injection through HardenedObjectInputStream modules |
| [GHSA-p47f-322f-whfh](https://osv.dev/vulnerability/GHSA-p47f-322f-whfh) | CVE-2026-9828 | LOW | `ch.qos.logback:logback-core` | QOS.CH Sarl logback logback-core has a deserialization of untrusted data vulnerability |
| [GHSA-qqpg-mvqg-649v](https://osv.dev/vulnerability/GHSA-qqpg-mvqg-649v) | CVE-2026-1225 | LOW | `ch.qos.logback:logback-core` | Logback allows an attacker to instantiate classes already present on the class path |
| [GHSA-vfww-5hm6-hx2j](https://osv.dev/vulnerability/GHSA-vfww-5hm6-hx2j) | CVE-2025-55754 | LOW | `org.apache.tomcat.embed:tomcat-embed-core` | Apache Tomcat Vulnerable to Improper Neutralization of Escape, Meta, or Control Sequences |
| [GHSA-wg35-8jpf-2xv3](https://osv.dev/vulnerability/GHSA-wg35-8jpf-2xv3) | CVE-2026-22741 | LOW | `org.springframework:spring-webmvc` | Spring MVC and WebFlux applications are vulnerable to cache poisoning when resolving static resources. |

## GHSA mới xuất hiện trong graph đợt 1

| GHSA | CVE alias | Mức | Dependency và phiên bản | Mô tả của nguồn |
|---|---|---|---|---|---|
| [GHSA-p6pp-m3f8-5c89](https://osv.dev/vulnerability/GHSA-p6pp-m3f8-5c89) | CVE-2026-89407 | HIGH | `com.fasterxml.jackson.core:jackson-core` 2.21.4 | jackson-core: ReDoS: quadratic backtracking in NumberInput.PATTERN_FLOAT via looksLikeValidNumber() |
| [GHSA-mhm7-754m-9p8w](https://osv.dev/vulnerability/GHSA-mhm7-754m-9p8w) | — | MODERATE | `com.fasterxml.jackson.core:jackson-databind` 2.21.4 | jackson-databind: `@JsonView` bypass for creator properties with `@JsonTypeInfo(include=As.EXTERNAL_PROPERTY)` |
| [GHSA-f525-44xv-f2qj](https://osv.dev/vulnerability/GHSA-f525-44xv-f2qj) | CVE-2026-65637 | CRITICAL | `org.apache.tomcat.embed:tomcat-embed-core` 10.1.55 | Apache Tomcat - HTTP/2 no-authority bypass of strict SNI validation |
| [GHSA-574f-3g2m-x479](https://osv.dev/vulnerability/GHSA-574f-3g2m-x479) | CVE-2025-14813 | CRITICAL | `org.bouncycastle:bcprov-jdk18on` 1.81 | Bouncy Castle for Java GOST 28147 CTR mode reuses keystream after 255 blocks |
| [GHSA-9pwp-9qqc-pr26](https://osv.dev/vulnerability/GHSA-9pwp-9qqc-pr26) | CVE-2026-8763 | CRITICAL | `org.bouncycastle:bcprov-jdk18on` 1.81 | Bouncy Castle: Name Constraints bypass via trailing dot in rfc822Name and URI |
| [GHSA-c3fc-8qff-9hwx](https://osv.dev/vulnerability/GHSA-c3fc-8qff-9hwx) | CVE-2026-0636 | MODERATE | `org.bouncycastle:bcprov-jdk18on` 1.81 | Bouncy Castle has an LDAP injection |
| [GHSA-p93r-85wp-75v3](https://osv.dev/vulnerability/GHSA-p93r-85wp-75v3) | CVE-2026-5598 | HIGH | `org.bouncycastle:bcprov-jdk18on` 1.81 | Bouncy Castle Has Covert Timing Channel Vulnerability |
| [GHSA-qp49-qgx5-5m26](https://osv.dev/vulnerability/GHSA-qp49-qgx5-5m26) | CVE-2026-13506 | HIGH | `org.bouncycastle:bcprov-jdk18on` 1.81 | Bouncy Castle: Lazy ASN.1 sequence forcing resets nesting-depth guard |
| [GHSA-j92g-9f8w-j867](https://osv.dev/vulnerability/GHSA-j92g-9f8w-j867) | CVE-2026-54291 | HIGH | `org.postgresql:postgresql` 42.7.11 | PostgreSQL JDBC Driver: Silent channel-binding authentication downgrade via unsupported certificate algorithms |
| [GHSA-j9f9-w8pj-32f8](https://osv.dev/vulnerability/GHSA-j9f9-w8pj-32f8) | CVE-2026-47890 | CRITICAL | `org.springframework:spring-webmvc` 6.2.19 | Spring Framework Server Sent Event stream corruption while rendering fragments |
