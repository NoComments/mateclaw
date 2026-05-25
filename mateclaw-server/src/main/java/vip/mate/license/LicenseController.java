package vip.mate.license;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint for trial license status.
 * Used by the frontend to show trial countdown and expired overlay.
 */
@RestController
@RequestMapping("/api/v1/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping("/status")
    public ResponseEntity<LicenseStatusDTO> status() {
        LicenseStatusDTO status = licenseService.getCachedStatus();
        if (status == null) {
            status = licenseService.validate();
        }
        return ResponseEntity.ok(status);
    }
}
