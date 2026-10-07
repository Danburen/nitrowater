package cn.nitrowater.account.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 托管登录页（Phase 2 / Q1A）。表单 POST /login 由 Spring Security 表单登录处理，
 * 额外字段 captcha / deviceFp 由 {@code SsoAuthenticationProvider} 读取。
 */
@RestController
public class LoginPageController {

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login() {
        String html = """
                <!doctype html>
                <html lang="zh">
                <head><meta charset="utf-8"><title>nitrowater SSO 登录</title></head>
                <body style="font-family:sans-serif;max-width:360px;margin:3rem auto">
                  <h3>nitrowater SSO 登录</h3>
                  <form method="post" action="/login">
                    <p><input name="username" placeholder="用户名 / 手机 / 邮箱" style="width:100%" required/></p>
                    <p><input name="password" type="password" placeholder="密码" style="width:100%" required/></p>
                    <p>
                      <img id="cap" src="/api/auth/captcha" title="点击刷新"
                           onclick="this.src='/api/auth/captcha?t='+Date.now()" style="cursor:pointer"/>
                      <input name="captcha" placeholder="图形验证码" required/>
                    </p>
                    <input type="hidden" name="deviceFp" id="dfp"/>
                    <p><button type="submit">登录</button></p>
                  </form>
                  <script>
                    document.getElementById('dfp').value =
                      (window.crypto && crypto.randomUUID)
                        ? crypto.randomUUID().replace(/-/g,'').substring(0,16)
                        : 'sso' + Date.now();
                  </script>
                </body>
                </html>
                """;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
