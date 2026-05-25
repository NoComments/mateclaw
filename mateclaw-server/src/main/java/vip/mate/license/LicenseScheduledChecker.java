package vip.mate.license;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic license re-validation and high-water-mark update.
 * Runs every hour to catch mid-session expiration and keep the HWM fresh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseScheduledChecker {

    private final LicenseService licenseService;

    @Scheduled(fixedRate = 3600_000, initialDelay = 3600_000)
    public void checkLicense() {
        LicenseStatusDTO prev = licenseService.getCachedStatus();
        LicenseStatusDTO current = licenseService.validate();
        if (prev != null && "active".equals(prev.getStatus()) && !"active".equals(current.getStatus())) {
            log.warn("[License] Status changed from active to {}: {}", current.getStatus(), current.getMessage());
        }
    }
}
