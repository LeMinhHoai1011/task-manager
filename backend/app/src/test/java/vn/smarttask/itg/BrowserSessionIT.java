package vn.smarttask.itg;

import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.smarttask.common.AppException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@TestPropertySource(properties={"smarttask.browser.enabled=true","smarttask.browser.allowed-origins=https://app.test"})
class BrowserSessionIT extends ItgBase {
  private Cookie extract(MockHttpServletResponse res,String name) {
    for(String h:res.getHeaders("Set-Cookie"))if(h.startsWith(name+"="))return new Cookie(name,h.substring(name.length()+1,h.indexOf(';')));
    throw new AssertionError("Missing cookie "+name);
  }
  @Test void httpOnlyRotationCsrfAndLogoutAreEnforced() throws Exception {
    var user=activeUser("cookie","Password123!");
    var csrf=mvc.perform(get("/api/v1/auth/web/csrf").with(r->{r.setRemoteAddr(fwd());return r;})).andReturn();
    assertEquals(200,csrf.getResponse().getStatus());
    Cookie proof=extract(csrf.getResponse(),"__Host-stm_csrf");
    var body=om.writeValueAsString(Map.of("username",user.getUsername(),"password","Password123!"));
    assertEquals(403,mvc.perform(post("/api/v1/auth/web/login").header("Origin","https://evil.test").cookie(proof).header("X-CSRF-Token",proof.getValue()).contentType("application/json").content(body).with(r->{r.setRemoteAddr(fwd());return r;})).andReturn().getResponse().getStatus());
    assertEquals(403,mvc.perform(post("/api/v1/auth/web/login").header("Origin","https://app.test").cookie(proof).contentType("application/json").content(body).with(r->{r.setRemoteAddr(fwd());return r;})).andReturn().getResponse().getStatus());
    var login=mvc.perform(post("/api/v1/auth/web/login").header("Origin","https://app.test").cookie(proof).header("X-CSRF-Token",proof.getValue()).contentType("application/json").content(body).with(r->{r.setRemoteAddr(fwd());return r;})).andReturn();
    assertEquals(200,login.getResponse().getStatus());
    assertFalse(login.getResponse().getContentAsString().contains("refresh_token"));
    assertTrue(login.getResponse().getHeaders("Set-Cookie").stream().anyMatch(h->h.contains("HttpOnly")&&h.contains("Secure")&&h.contains("SameSite=Strict")&&h.contains("Path=/api/v1/auth/web")));
    Cookie refresh=extract(login.getResponse(),"__Secure-stm_refresh");
    String access=om.readTree(login.getResponse().getContentAsString()).path("data").path("access_token").asText();
    var rotated=mvc.perform(post("/api/v1/auth/web/refresh").header("Origin","https://app.test").cookie(proof,refresh).header("X-CSRF-Token",proof.getValue()).with(r->{r.setRemoteAddr(fwd());return r;})).andReturn();
    assertEquals(200,rotated.getResponse().getStatus());
    assertEquals(401,mvc.perform(get("/api/v1/me/access").header("Authorization","Bearer "+access).with(r->{r.setRemoteAddr(fwd());return r;})).andReturn().getResponse().getStatus());
    assertEquals(401,mvc.perform(post("/api/v1/auth/web/refresh").header("Origin","https://app.test").cookie(proof,refresh).header("X-CSRF-Token",proof.getValue()).with(r->{r.setRemoteAddr(fwd());return r;})).andReturn().getResponse().getStatus());
    Cookie next=extract(rotated.getResponse(),"__Secure-stm_refresh");
    // Logout sent with the previous cookie must also revoke a raced rotation.
    assertEquals(200,mvc.perform(post("/api/v1/auth/web/logout").header("Origin","https://app.test").cookie(proof,refresh).header("X-CSRF-Token",proof.getValue()).with(r->{r.setRemoteAddr(fwd());return r;})).andReturn().getResponse().getStatus());
    assertEquals(401,mvc.perform(post("/api/v1/auth/web/refresh").header("Origin","https://app.test").cookie(proof,next).header("X-CSRF-Token",proof.getValue()).with(r->{r.setRemoteAddr(fwd());return r;})).andReturn().getResponse().getStatus());
  }
  @Test void concurrentRefreshHasExactlyOneWinnerAndNoServerErrors() throws Exception {
    var user=activeUser("race","Password123!");var login=identity.login(user.getUsername(),"Password123!","race","WEB");
    var start=new CountDownLatch(1);var ready=new CountDownLatch(2);var pool=Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results=new ArrayList<>();
      for(int i=0;i<2;i++)results.add(pool.submit(()->{ready.countDown();start.await();try{identity.refresh(login.refreshToken());return 200;}catch(AppException ex){return ex.getHttpStatus().value();}}));
      assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
      List<Integer> statuses=new ArrayList<>();for(var r:results)statuses.add(r.get(30,TimeUnit.SECONDS));
      Collections.sort(statuses);assertEquals(List.of(200,401),statuses);
      assertEquals(1,jdbc.queryForObject("select count(*) from user_session where user_id=? and active=true",Integer.class,user.getId()));
    }finally{pool.shutdownNow();}
  }
}
