package za.co.tsa.oidcdemo.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import za.co.tsa.oidcdemo.api.UserApi;
import za.co.tsa.oidcdemo.api.model.UserInfoResponse;
import za.co.tsa.oidcdemo.security.AuthenticatedUserService;

/** Implements the {@code User} tag of {@code openapi/openapi.yaml}. */
@RestController
public class UserController implements UserApi {

    private final AuthenticatedUserService authenticatedUserService;

    public UserController(AuthenticatedUserService authenticatedUserService) {
        this.authenticatedUserService = authenticatedUserService;
    }

    @Override
    public UserInfoResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authenticatedUserService.describe(authentication);
    }
}
