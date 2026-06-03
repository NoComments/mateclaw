package vip.mate.license;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.system.service.SystemSettingService;

import org.springframework.boot.system.ApplicationHome;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Trial license validator with high-water-mark anti-clock-rollback protection.
 * <p>
 * License file: {@code license.lic} resolved relative to the JAR's directory
 * (via {@link ApplicationHome}), with a fallback to the working directory for dev mode.
 * Format: JWT signed with HMAC-SHA256, containing:
 * <ul>
 *   <li>{@code sub} — customer name</li>
 *   <li>{@code iat} — issue timestamp</li>
 *   <li>{@code exp} — expiration timestamp</li>
 * </ul>
 * High-water-mark: last-seen timestamp stored in {@code mate_system_setting}
 * under key {@code license.highWaterMark}. If current time < stored mark,
 * clock rollback is detected and the license is treated as invalid.
 */
@Slf4j
@Service
public class LicenseService {

    private static final String LICENSE_FILE = "license.lic";
    private static final String HWM_KEY = "license.highWaterMark";
    private static final String ACTIVATION_KEY = "license.activationTime";

    private final LicenseProperties properties;
    private final SystemSettingService settingService;

    /**
     * Resolved once at construction time. Uses the JAR's parent directory
     * (via {@link ApplicationHome}) so the file is found regardless of the
     * JVM's working directory (e.g. when launched via systemd / cron).
     */
    private final Path licenseFilePath;

    /** Cached status — refreshed on startup and by scheduled checker */
    private volatile LicenseStatusDTO cachedStatus;

    public LicenseService(LicenseProperties properties, SystemSettingService settingService) {
        this.properties = properties;
        this.settingService = settingService;
        this.licenseFilePath = resolveLicensePath();
    }

    private Path resolveLicensePath() {
        var appHome = new ApplicationHome(LicenseService.class);
        Path homeDir = appHome.getDir().toPath();
        Path resolved = homeDir.resolve(LICENSE_FILE);
        // Fallback: if not found next to JAR, try working directory (dev mode)
        if (!Files.exists(resolved)) {
            Path cwd = Path.of(LICENSE_FILE);
            if (Files.exists(cwd)) {
                return cwd;
            }
        }
        log.info("[License] License path resolved to: {}", resolved);
        return resolved;
    }

    /**
     * Full validation: read file → verify signature → check expiry → check clock rollback → update HWM.
     */
    public LicenseStatusDTO validate() {
        Path licPath = licenseFilePath;
        if (!Files.exists(licPath)) {
            cachedStatus = LicenseStatusDTO.builder()
                    .status("missing")
                    .daysRemaining(0)
                    .message("未找到 license.lic 授权文件")
                    .build();
            return cachedStatus;
        }

        String token;
        try {
            token = Files.readString(licPath).trim();
        } catch (IOException e) {
            log.error("[License] Failed to read license.lic: {}", e.getMessage());
            cachedStatus = LicenseStatusDTO.builder()
                    .status("invalid")
                    .daysRemaining(0)
                    .message("license.lic 文件读取失败")
                    .build();
            return cachedStatus;
        }

        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            Claims expiredClaims = e.getClaims();
            cachedStatus = LicenseStatusDTO.builder()
                    .status("expired")
                    .customer(expiredClaims.getSubject())
                    .expiresAt(formatInstant(expiredClaims.getExpiration().toInstant()))
                    .daysRemaining(daysUntil(expiredClaims.getExpiration().toInstant()))
                    .message("试用授权已到期，请联系供应商续期")
                    .build();
            return cachedStatus;
        } catch (SignatureException e) {
            log.warn("[License] Signature verification failed — file may have been tampered with");
            cachedStatus = LicenseStatusDTO.builder()
                    .status("invalid")
                    .daysRemaining(0)
                    .message("license.lic 签名校验失败，文件可能被篡改")
                    .build();
            return cachedStatus;
        } catch (Exception e) {
            log.warn("[License] Failed to parse license: {}", e.getMessage());
            cachedStatus = LicenseStatusDTO.builder()
                    .status("invalid")
                    .daysRemaining(0)
                    .message("license.lic 格式无效")
                    .build();
            return cachedStatus;
        }

        // Clock rollback detection
        Instant now = Instant.now();
        if (isClockRolledBack(now)) {
            cachedStatus = LicenseStatusDTO.builder()
                    .status("clock_tampered")
                    .customer(claims.getSubject())
                    .expiresAt(formatInstant(claims.getExpiration().toInstant()))
                    .daysRemaining(0)
                    .message("检测到系统时钟异常（回拨），授权校验失败")
                    .build();
            return cachedStatus;
        }

        // Update high-water-mark
        updateHighWaterMark(now);

        // Record activation time on first successful validation
        recordActivationIfAbsent(now);

        long daysLeft = daysUntil(claims.getExpiration().toInstant());
        cachedStatus = LicenseStatusDTO.builder()
                .status("active")
                .customer(claims.getSubject())
                .expiresAt(formatInstant(claims.getExpiration().toInstant()))
                .daysRemaining(daysLeft)
                .message(daysLeft <= 7
                        ? "试用即将到期，剩余 " + daysLeft + " 天"
                        : "试用授权有效")
                .build();
        return cachedStatus;
    }

    /** Get cached status without re-reading the file. Thread-safe via volatile. */
    public LicenseStatusDTO getCachedStatus() {
        return cachedStatus;
    }

    /** Check if license is currently valid (active). */
    public boolean isValid() {
        LicenseStatusDTO s = cachedStatus;
        return s != null && "active".equals(s.getStatus());
    }

    private boolean isClockRolledBack(Instant now) {
        String hwm = settingService.getValuePublic(HWM_KEY, "");
        if (hwm == null || hwm.isBlank()) return false;
        try {
            Instant lastSeen = Instant.parse(hwm);
            // Allow 5-minute tolerance for minor clock drift / NTP adjustments
            return now.isBefore(lastSeen.minus(5, ChronoUnit.MINUTES));
        } catch (Exception e) {
            return false;
        }
    }

    private void updateHighWaterMark(Instant now) {
        settingService.saveValuePublic(HWM_KEY, now.toString(), "License 高水位时间戳（防时钟回拨）");
    }

    private void recordActivationIfAbsent(Instant now) {
        String existing = settingService.getValuePublic(ACTIVATION_KEY, "");
        if (existing == null || existing.isBlank()) {
            settingService.saveValuePublic(ACTIVATION_KEY, now.toString(), "License 首次激活时间");
            log.info("[License] First activation recorded at {}", now);
        }
    }

    private static long daysUntil(Instant expiry) {
        return ChronoUnit.DAYS.between(Instant.now(), expiry);
    }

    private static String formatInstant(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
