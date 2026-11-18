package vn.smarttask.identity;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.*;
import vn.smarttask.identity.service.IdentityService;

/** Opt-in, same-origin browser transport. Legacy native/mobile bearer API is unchanged. */
@RestController
@RequestMapping("/api/v1/auth/web")
public class BrowserAuthController {
  private final IdentityService identity;
  private final vn.smarttask.identity.repo.AppUserRepository users;
  private final boolean enabled,secure;
  private final Set<String> origins;
  private static final SecureRandom RANDOM=new SecureRandom();
  public BrowserAuthController(IdentityService identity, vn.smarttask.identity.repo.AppUserRepository users,
      @Value("${smarttask.browser.enabled:false}") boolean enabled,
      @Value("${smarttask.browser.secure-cookies:true}") boolean secure,
      @Value("${smarttask.browser.allowed-origins:}") String origins) {
    this.identity=identity;this.users=users;this.enabled=enabled;this.secure=secure;
    this.origins=new HashSet<>(Arrays.asList(origins.split(",")));
  }
  private String refreshName(){return secure?"__Secure-stm_refresh":"stm_refresh";}
  private String csrfName(){return secure?"__Host-stm_csrf":"stm_csrf";}
  private void available(HttpServletRequest req) {
    if(!enabled)throw AppException.notFound("Browser session transport is disabled");
    if(!secure&&!Set.of("localhost","127.0.0.1","::1").contains(req.getServerName()))
      throw AppException.forbidden("Insecure browser cookies require loopback");
  }
  private static String cookie(HttpServletRequest req,String name) {
    if(req.getCookies()!=null)for(var c:req.getCookies())if(name.equals(c.getName()))return c.getValue();
    return "";
  }
  private void check(HttpServletRequest req) {
    available(req);
    String origin=req.getHeader("Origin");
    var uri=URI.create(req.getRequestURL().toString());
    String same=uri.getScheme()+"://"+uri.getRawAuthority();
    if(origin==null||(!origin.equals(same)&&!origins.contains(origin)))throw AppException.forbidden("Browser origin denied");
    String value=cookie(req,csrfName()),header=req.getHeader("X-CSRF-Token");
    if(value.length()<32||header==null||!MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),header.getBytes(StandardCharsets.UTF_8)))
      throw AppException.forbidden("CSRF validation failed");
  }
  private void set(HttpServletResponse res,String name,String value,String path,boolean httpOnly,Duration age) {
    res.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(name,value).path(path).httpOnly(httpOnly)
        .secure(secure).sameSite("Strict").maxAge(age).build().toString());
    res.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
  }
  @GetMapping("/csrf") public ApiResult<?> csrf(HttpServletRequest req,HttpServletResponse res) {
    available(req);byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);
    String existing=cookie(req,csrfName());
    String token=existing.matches("[A-Za-z0-9_-]{43}")?existing:Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    set(res,csrfName(),token,"/",false,Duration.ofDays(7));
    return ApiResult.ok(Map.of("csrf_token",token),"auth.csrf.success");
  }
  private ApiResult<?> tokens(IdentityService.LoginResult r,HttpServletResponse res) {
    set(res,refreshName(),r.refreshToken(),"/api/v1/auth/web",true,Duration.ofDays(7));
    return ApiResult.ok(Map.of("access_token",r.accessToken(),"expires_at",r.expiresAt(),"user_id",r.userId(),"email",users.findById(r.userId()).orElseThrow().getEmail(),
        "role",r.isSystemAdmin()?"SYSTEM_ADMIN":"USER","must_change_password",r.mustChangePassword()),"auth.browser.success");
  }
  @PostMapping("/login") public ApiResult<?> login(@Valid @RequestBody AuthController.LoginRequest body,
      HttpServletRequest req,HttpServletResponse res) {
    check(req);return tokens(identity.loginBrowser(body.username(),body.password(),body.deviceLabel()),res);
  }
  @PostMapping("/refresh") public ApiResult<?> refresh(HttpServletRequest req,HttpServletResponse res) {
    check(req);return tokens(identity.refreshBrowser(cookie(req,refreshName())),res);
  }
  @PostMapping("/logout") public ApiResult<?> logout(HttpServletRequest req,HttpServletResponse res) {
    check(req);identity.logoutBrowser(cookie(req,refreshName()));
    set(res,refreshName(),"","/api/v1/auth/web",true,Duration.ZERO);
    set(res,csrfName(),"","/",false,Duration.ZERO);
    return ApiResult.ok(Map.of("logged_out",true),"auth.logout.success");
  }
}
