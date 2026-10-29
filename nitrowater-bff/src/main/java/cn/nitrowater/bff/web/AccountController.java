package cn.nitrowater.bff.web;

import cn.nitrowater.bff.security.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * Account-pool endpoints for the SPA.
 *
 * <ul>
 *   <li>{@code GET /bff/accounts} — list accounts and the active one.</li>
 *   <li>{@code POST /bff/accounts/{uid}/activate} — instant switch; no Authorization Server round
 *       trip, because the tokens are already in the session.</li>
 *   <li>{@code DELETE /bff/accounts/{uid}} — remove one account (revokes its refresh token);
 *       the response tells the SPA whether the last account went away and a full logout is due.</li>
 *   <li>{@code GET /bff/accounts/add} — start a fresh authorization with {@code prompt=login}.</li>
 * </ul>
 *
 * <p>All endpoints except {@code /add} require a session. {@code /add} is intentionally public:
 * it only redirects, and requiring authentication would create a redirect loop for an anonymous
 * visitor who navigates to it directly.</p>
 */
@RestController
@RequestMapping("/bff/accounts")
public class AccountController {

    private final AccountSessionService accounts;
    private final String registrationId;

    public AccountController(AccountSessionService accounts,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId) {
        this.accounts = accounts;
        this.registrationId = registrationId;
    }

    @GetMapping
    public AccountListResponse list(HttpServletRequest request) {
        return new AccountListResponse(accounts.views(request), accounts.activeUid(request));
    }

    @PostMapping("/{uid}/activate")
    public AccountListResponse activate(@PathVariable("uid") String uid,
            HttpServletRequest request, HttpServletResponse response) {
        accounts.activate(request, response, uid);
        return list(request);
    }

    @DeleteMapping("/{uid}")
    public AccountSessionService.RemoveResult remove(@PathVariable("uid") String uid,
            HttpServletRequest request, HttpServletResponse response) {
        return accounts.remove(request, response, uid);
    }

    @GetMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/oauth2/authorization/" + registrationId + "?switch=1");
    }

    /**
     * @param accounts  accounts in insertion order
     * @param activeUid active account id, {@code null} when the pool is empty
     */
    public record AccountListResponse(List<AccountSessionService.AccountView> accounts, String activeUid) {
    }
}
