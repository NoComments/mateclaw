package vip.mate.license;

import lombok.Builder;
import lombok.Data;

/**
 * License status response for frontend
 */
@Data
@Builder
public class LicenseStatusDTO {
    /** "active" | "expired" | "missing" | "invalid" | "clock_tampered" */
    private String status;
    /** Customer name from license */
    private String customer;
    /** ISO-8601 expiration timestamp */
    private String expiresAt;
    /** Days remaining (negative if expired) */
    private long daysRemaining;
    /** Human-readable message */
    private String message;
}
