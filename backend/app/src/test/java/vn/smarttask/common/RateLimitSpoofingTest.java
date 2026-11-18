package vn.smarttask.common;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class RateLimitSpoofingTest {
  @Test void changingForwardedHeaderCannotBypassAuthBucket() throws Exception {
    var filter=new RateLimitFilter();
    for(int i=0;i<11;i++){
      var request=new MockHttpServletRequest("POST","/api/v1/auth/login");
      request.setRemoteAddr("203.0.113.7");request.addHeader("X-Forwarded-For","10.0.0."+i);
      var response=new MockHttpServletResponse();filter.doFilter(request,response,new MockFilterChain());
      assertEquals(i<10?200:429,response.getStatus());
    }
    var other=new MockHttpServletRequest("POST","/api/v1/auth/login");other.setRemoteAddr("203.0.113.8");
    var response=new MockHttpServletResponse();filter.doFilter(other,response,new MockFilterChain());assertEquals(200,response.getStatus());
  }
}
