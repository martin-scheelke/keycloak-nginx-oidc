package za.co.tsa.oidcdemo.web;

import java.time.OffsetDateTime;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import za.co.tsa.oidcdemo.api.AdminApi;
import za.co.tsa.oidcdemo.api.model.AdminStats;

/** Implements the {@code Admin} tag of {@code openapi/openapi.yaml}. */
@RestController
public class AdminController implements AdminApi {

    @Override
    @PreAuthorize("hasRole('admin')")
    public AdminStats getAdminStats() {
        return new AdminStats(OffsetDateTime.now(), 1, "process-local, no database");
    }
}
