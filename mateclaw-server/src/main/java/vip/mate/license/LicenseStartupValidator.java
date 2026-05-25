package vip.mate.license;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Validates the trial license on application startup.
 * Runs early (Order 1) so that license problems are visible in boot logs.
 * Does NOT prevent startup — lets the app boot so the user can see the
 * license status page; the {@link LicenseFilter} blocks API calls.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LicenseStartupValidator implements ApplicationRunner {

    private final LicenseService licenseService;

    @Override
    public void run(ApplicationArguments args) {
        LicenseStatusDTO status = licenseService.validate();
        switch (status.getStatus()) {
            case "active" -> log.info("[License] Trial active — customer: {}, expires: {}, {} days remaining",
                    status.getCustomer(), status.getExpiresAt(), status.getDaysRemaining());
            case "expired" -> log.warn("[License] Trial EXPIRED — customer: {}, expired at: {}",
                    status.getCustomer(), status.getExpiresAt());
            case "missing" -> log.error("[License] license.lic not found! Place it in the application directory.");
            case "invalid" -> log.error("[License] license.lic is invalid: {}", status.getMessage());
            case "clock_tampered" -> log.error("[License] Clock rollback detected! {}", status.getMessage());
            default -> log.warn("[License] Unknown status: {}", status.getStatus());
        }
    }
}
