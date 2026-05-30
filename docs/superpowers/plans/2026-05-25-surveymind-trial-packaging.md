# SurveyMind 试用打包 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete trial packaging system for SurveyMind, including JWT-based license verification with clock-rollback protection, and produce a zip archive that Windows customers can unzip-and-run.

**Architecture:** A `vip.mate.license` package provides license validation (JWT + HMAC-SHA256 + high-water-mark anti-rollback). A Servlet filter gates all `/api/**` requests. The frontend shows a trial countdown banner and an expired overlay. A shell script builds the JAR, bundles a Windows JRE 21, and produces a ready-to-deliver zip.

**Tech Stack:** JJWT 0.12.6 (existing), Spring Boot Filter, `mate_system_setting` table (existing KV store), Vue 3 Composition API, Element Plus, shell script + Windows batch files.

---

## File Map

### Backend (new files under `mateclaw-server/src/main/java/vip/mate/license/`)

| File | Responsibility |
|------|---------------|
| `LicenseProperties.java` | `@ConfigurationProperties` — license signing key |
| `LicenseService.java` | Core: read `license.lic`, verify JWT, check expiry, high-water-mark read/write via `SystemSettingService` |
| `LicenseFilter.java` | `OncePerRequestFilter` — intercept `/api/**`, reject if expired |
| `LicenseController.java` | `GET /api/v1/license/status` — public endpoint for frontend |
| `LicenseStartupValidator.java` | `ApplicationRunner` — validate license on boot, record activation |
| `LicenseScheduledChecker.java` | `@Scheduled` — hourly high-water-mark update |
| `LicenseGenerator.java` | Standalone `main()` — CLI tool to generate `license.lic` |
| `LicenseStatusDTO.java` | Response DTO: status, customer, expiresAt, daysRemaining |

### Backend (modify existing)

| File | Change |
|------|--------|
| `SecurityConfig.java` | Add `/api/v1/license/status` to public whitelist |
| `application.yml` | Add `mateclaw.license.secret` config key |

### Frontend (new/modify)

| File | Change |
|------|--------|
| `src/api/index.ts` | Add license-expired interceptor (403 + LICENSE_EXPIRED code) |
| `src/views/layout/MainLayout.vue` | Add trial banner component |
| `src/views/layout/TrialBanner.vue` | New: countdown banner + expired overlay |
| `src/i18n/locales/zh-CN.ts` | Add `license.*` keys |
| `src/i18n/locales/en-US.ts` | Add `license.*` keys |

### Packaging (new)

| File | Responsibility |
|------|---------------|
| `scripts/package-trial.sh` | Build JAR + download JRE + generate license + zip |
| `scripts/windows/start.bat` | Windows launch script |
| `scripts/windows/stop.bat` | Windows stop script |
| `docs/SurveyMind-安装指南.md` | Customer-facing installation guide |

---

## Task 1: LicenseProperties + LicenseStatusDTO

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/license/LicenseProperties.java`
- Create: `mateclaw-server/src/main/java/vip/mate/license/LicenseStatusDTO.java`
- Modify: `mateclaw-server/src/main/resources/application.yml`

- [ ] **Step 1: Add license config to application.yml**

In `application.yml`, under the `mateclaw:` block (after `skill:` section), add:

```yaml
  license:
    # HMAC-SHA256 signing key for trial license verification.
    # MUST match the key used by LicenseGenerator to produce license.lic.
    # Override via env var MATECLAW_LICENSE_SECRET for production.
    secret: ${MATECLAW_LICENSE_SECRET:SurveyMind-License-Secret-2026-HMAC-SHA256-Key-Do-Not-Share}
```

- [ ] **Step 2: Create LicenseProperties**

```java
package vip.mate.license;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * License 签名密钥配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "mateclaw.license")
public class LicenseProperties {
    /** HMAC-SHA256 signing key — must match LicenseGenerator */
    private String secret = "SurveyMind-License-Secret-2026-HMAC-SHA256-Key-Do-Not-Share";
}
```

- [ ] **Step 3: Create LicenseStatusDTO**

```java
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
```

- [ ] **Step 4: Commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/license/LicenseProperties.java \
       mateclaw-server/src/main/java/vip/mate/license/LicenseStatusDTO.java \
       mateclaw-server/src/main/resources/application.yml
git commit -m "feat(license): add LicenseProperties and LicenseStatusDTO"
```

---

## Task 2: LicenseService — Core Validation Logic

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/license/LicenseService.java`

- [ ] **Step 1: Create LicenseService**

```java
package vip.mate.license;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.system.service.SystemSettingService;

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
 * License file: {@code license.lic} in application working directory.
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

    /** Cached status — refreshed on startup and by scheduled checker */
    private volatile LicenseStatusDTO cachedStatus;

    public LicenseService(LicenseProperties properties, SystemSettingService settingService) {
        this.properties = properties;
        this.settingService = settingService;
    }

    /**
     * Full validation: read file → verify signature → check expiry → check clock rollback → update HWM.
     */
    public LicenseStatusDTO validate() {
        Path licPath = Path.of(LICENSE_FILE);
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
```

- [ ] **Step 2: Add `getValuePublic` and `saveValuePublic` methods to SystemSettingService**

These are public wrappers around the existing private `getValue`/`saveValue` methods, needed by `LicenseService`. In `SystemSettingService.java`, change the visibility of `getValue` and `saveValue` — or add public wrappers:

```java
/**
 * Public read access to a system setting (used by LicenseService, etc.)
 */
public String getValuePublic(String key, String defaultValue) {
    return getValue(key, defaultValue);
}

/**
 * Public write access to a system setting (used by LicenseService, etc.)
 */
public void saveValuePublic(String key, String value, String description) {
    saveValue(key, value, description);
}
```

Add these two methods at the end of `SystemSettingService.java`, before the closing `}`.

- [ ] **Step 3: Commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/license/LicenseService.java \
       mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java
git commit -m "feat(license): add LicenseService with JWT validation and clock-rollback protection"
```

---

## Task 3: LicenseFilter — API Request Gating

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/license/LicenseFilter.java`
- Modify: `mateclaw-server/src/main/java/vip/mate/config/SecurityConfig.java`

- [ ] **Step 1: Create LicenseFilter**

```java
package vip.mate.license;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects all {@code /api/**} requests (except whitelisted paths) when the
 * trial license is not valid.
 * <p>
 * Runs BEFORE Spring Security's filter chain ({@code Order(1)}) so that
 * expired-license responses are consistent regardless of auth state.
 */
@Component
@RequiredArgsConstructor
@Order(1)
public class LicenseFilter extends OncePerRequestFilter {

    private final LicenseService licenseService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Allow license status check, login, and non-API requests through
        return !path.startsWith("/api/")
                || path.equals("/api/v1/license/status")
                || path.equals("/api/v1/auth/login")
                || path.startsWith("/api/v1/setup/")
                || path.equals("/api/v1/settings/language");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (licenseService.isValid()) {
            filterChain.doFilter(request, response);
            return;
        }

        // License not valid — block with 403
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        LicenseStatusDTO status = licenseService.getCachedStatus();
        String msg = status != null ? status.getMessage() : "试用授权无效";
        response.getWriter().write(
                "{\"code\":403,\"msg\":\"LICENSE_EXPIRED\",\"data\":\"" + msg + "\"}"
        );
    }
}
```

- [ ] **Step 2: Add `/api/v1/license/status` to SecurityConfig whitelist**

In `SecurityConfig.java`, add to the `.requestMatchers(...)` list inside `permitAll()`:

```java
                    "/api/v1/license/status",
```

Add this line after `"/api/v1/auth/login",` (line 53).

- [ ] **Step 3: Commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/license/LicenseFilter.java \
       mateclaw-server/src/main/java/vip/mate/config/SecurityConfig.java
git commit -m "feat(license): add LicenseFilter to gate API requests on trial expiry"
```

---

## Task 4: LicenseController + LicenseStartupValidator + LicenseScheduledChecker

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/license/LicenseController.java`
- Create: `mateclaw-server/src/main/java/vip/mate/license/LicenseStartupValidator.java`
- Create: `mateclaw-server/src/main/java/vip/mate/license/LicenseScheduledChecker.java`

- [ ] **Step 1: Create LicenseController**

```java
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
```

- [ ] **Step 2: Create LicenseStartupValidator**

```java
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
```

- [ ] **Step 3: Create LicenseScheduledChecker**

```java
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
```

- [ ] **Step 4: Commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/license/LicenseController.java \
       mateclaw-server/src/main/java/vip/mate/license/LicenseStartupValidator.java \
       mateclaw-server/src/main/java/vip/mate/license/LicenseScheduledChecker.java
git commit -m "feat(license): add controller, startup validator, and scheduled checker"
```

---

## Task 5: LicenseGenerator — CLI Tool

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/license/LicenseGenerator.java`

- [ ] **Step 1: Create LicenseGenerator**

```java
package vip.mate.license;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Standalone CLI tool to generate trial license files.
 * <p>
 * Usage:
 * <pre>
 *   java -cp surveymind-server.jar vip.mate.license.LicenseGenerator \
 *       --customer "客户公司名" --days 30
 * </pre>
 * Or with explicit secret:
 * <pre>
 *   java -cp surveymind-server.jar vip.mate.license.LicenseGenerator \
 *       --customer "客户公司名" --days 30 \
 *       --secret "your-hmac-secret"
 * </pre>
 * Output: {@code license.lic} in the current directory.
 */
public class LicenseGenerator {

    private static final String DEFAULT_SECRET =
            "SurveyMind-License-Secret-2026-HMAC-SHA256-Key-Do-Not-Share";

    public static void main(String[] args) throws IOException {
        String customer = "Trial Customer";
        int days = 30;
        String secret = DEFAULT_SECRET;
        String output = "license.lic";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--customer" -> customer = args[++i];
                case "--days" -> days = Integer.parseInt(args[++i]);
                case "--secret" -> secret = args[++i];
                case "--output" -> output = args[++i];
                case "--help" -> {
                    printUsage();
                    return;
                }
            }
        }

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        Instant expiry = now.plus(days, ChronoUnit.DAYS);

        String token = Jwts.builder()
                .subject(customer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();

        Files.writeString(Path.of(output), token);

        System.out.println("=== SurveyMind License Generated ===");
        System.out.println("Customer : " + customer);
        System.out.println("Valid    : " + days + " days");
        System.out.println("Issued   : " + now);
        System.out.println("Expires  : " + expiry);
        System.out.println("File     : " + output);
    }

    private static void printUsage() {
        System.out.println("""
                Usage: java -cp surveymind-server.jar vip.mate.license.LicenseGenerator [options]

                Options:
                  --customer <name>   Customer/company name (default: "Trial Customer")
                  --days <n>          Trial duration in days (default: 30)
                  --secret <key>      HMAC-SHA256 secret (default: built-in)
                  --output <file>     Output file path (default: license.lic)
                  --help              Show this message
                """);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/license/LicenseGenerator.java
git commit -m "feat(license): add LicenseGenerator CLI tool for creating trial licenses"
```

---

## Task 6: Frontend — Trial Banner + Expired Overlay

**Files:**
- Create: `mateclaw-ui/src/views/layout/TrialBanner.vue`
- Modify: `mateclaw-ui/src/views/layout/MainLayout.vue`
- Modify: `mateclaw-ui/src/api/index.ts`
- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts`
- Modify: `mateclaw-ui/src/i18n/locales/en-US.ts`

- [ ] **Step 1: Add i18n keys to zh-CN.ts**

Add to the root level of the exported object in `zh-CN.ts`:

```typescript
  license: {
    trialActive: '试用版',
    daysRemaining: '剩余 {days} 天',
    expiredTitle: '试用已到期',
    expiredMessage: '您的 SurveyMind 试用授权已到期，请联系供应商获取正式授权。',
    contactVendor: '联系供应商',
    clockTampered: '系统时钟异常',
    clockTamperedMessage: '检测到系统时钟被修改，授权校验失败。请恢复正确的系统时间后重启应用。',
    missing: '未授权',
    missingMessage: '未找到授权文件，请联系供应商获取 license.lic 文件。',
    invalid: '授权无效',
  },
```

- [ ] **Step 2: Add i18n keys to en-US.ts**

Add the same structure in English:

```typescript
  license: {
    trialActive: 'Trial',
    daysRemaining: '{days} days left',
    expiredTitle: 'Trial Expired',
    expiredMessage: 'Your SurveyMind trial license has expired. Please contact the vendor to obtain a full license.',
    contactVendor: 'Contact Vendor',
    clockTampered: 'Clock Anomaly',
    clockTamperedMessage: 'System clock modification detected. License validation failed. Please restore the correct system time and restart.',
    missing: 'Unlicensed',
    missingMessage: 'License file not found. Please contact the vendor for a license.lic file.',
    invalid: 'Invalid License',
  },
```

- [ ] **Step 3: Create TrialBanner.vue**

```vue
<template>
  <!-- Top warning banner: shows when <= 7 days remaining -->
  <div v-if="showBanner" class="trial-banner" :class="bannerClass">
    <span class="trial-badge">{{ t('license.trialActive') }}</span>
    <span class="trial-text">{{ t('license.daysRemaining', { days: status?.daysRemaining ?? 0 }) }}</span>
  </div>

  <!-- Full-screen expired overlay -->
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="showOverlay" class="trial-overlay">
        <div class="trial-overlay-card">
          <div class="trial-overlay-icon">⏱</div>
          <h2 class="trial-overlay-title">{{ overlayTitle }}</h2>
          <p class="trial-overlay-message">{{ overlayMessage }}</p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api'

interface LicenseStatus {
  status: string
  customer: string
  expiresAt: string
  daysRemaining: number
  message: string
}

const { t } = useI18n()
const status = ref<LicenseStatus | null>(null)

const showBanner = computed(() => {
  if (!status.value) return false
  return status.value.status === 'active' && status.value.daysRemaining <= 7
})

const bannerClass = computed(() => {
  if (!status.value) return ''
  if (status.value.daysRemaining <= 3) return 'trial-banner--critical'
  return 'trial-banner--warning'
})

const showOverlay = computed(() => {
  if (!status.value) return false
  return ['expired', 'clock_tampered', 'missing', 'invalid'].includes(status.value.status)
})

const overlayTitle = computed(() => {
  if (!status.value) return ''
  switch (status.value.status) {
    case 'expired': return t('license.expiredTitle')
    case 'clock_tampered': return t('license.clockTampered')
    case 'missing': return t('license.missing')
    default: return t('license.invalid')
  }
})

const overlayMessage = computed(() => {
  if (!status.value) return ''
  switch (status.value.status) {
    case 'expired': return t('license.expiredMessage')
    case 'clock_tampered': return t('license.clockTamperedMessage')
    case 'missing': return t('license.missingMessage')
    default: return status.value.message
  }
})

async function fetchLicenseStatus() {
  try {
    const res = await http.get('/license/status')
    status.value = res.data as LicenseStatus
  } catch {
    // License endpoint not available — assume no license system
  }
}

onMounted(() => {
  fetchLicenseStatus()
  // Re-check every 30 minutes
  setInterval(fetchLicenseStatus, 30 * 60 * 1000)
})
</script>

<style scoped>
.trial-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 600;
  z-index: 1000;
}

.trial-banner--warning {
  background: var(--el-color-warning-light-9, #fdf6ec);
  color: var(--el-color-warning-dark-2, #b88230);
  border-bottom: 1px solid var(--el-color-warning-light-5, #f3d19e);
}

.trial-banner--critical {
  background: var(--el-color-danger-light-9, #fef0f0);
  color: var(--el-color-danger-dark-2, #b25252);
  border-bottom: 1px solid var(--el-color-danger-light-5, #fab6b6);
}

.trial-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: currentColor;
  color: #fff;
  /* Use parent's currentColor as background, white text */
}
.trial-banner--warning .trial-badge { background: var(--el-color-warning, #e6a23c); color: #fff; }
.trial-banner--critical .trial-badge { background: var(--el-color-danger, #f56c6c); color: #fff; }

/* Overlay */
.trial-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
}

.trial-overlay-card {
  background: var(--mc-bg-elevated, #fff);
  border-radius: 16px;
  padding: 48px;
  max-width: 420px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.trial-overlay-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.trial-overlay-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--mc-text-primary, #1a1a1a);
  margin: 0 0 12px;
}

.trial-overlay-message {
  font-size: 14px;
  line-height: 1.6;
  color: var(--mc-text-secondary, #666);
  margin: 0;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
```

- [ ] **Step 4: Integrate TrialBanner into MainLayout.vue**

In `MainLayout.vue`, add import and usage:

At the top of `<script setup>`, add:
```typescript
import TrialBanner from './TrialBanner.vue'
```

In the `<template>`, add `<TrialBanner />` at the very top of the root `<div class="app-layout">`, before the mobile backdrop:

```html
<div class="app-layout">
    <TrialBanner />
    <!-- 移动端背景遮罩 -->
```

- [ ] **Step 5: Add LICENSE_EXPIRED handling to API interceptor**

In `src/api/index.ts`, inside the error interceptor (the `http.interceptors.response.use` error handler), add handling for the license-expired response. Find the section that handles `data.code === 401` and add after it:

```typescript
      // LICENSE_EXPIRED — trial license no longer valid
      if (data.code === 403 && data.msg === 'LICENSE_EXPIRED') {
        // Don't show generic error — TrialBanner overlay handles the UI
        return Promise.reject(new Error('LICENSE_EXPIRED'))
      }
```

- [ ] **Step 6: Commit**

```bash
git add mateclaw-ui/src/views/layout/TrialBanner.vue \
       mateclaw-ui/src/views/layout/MainLayout.vue \
       mateclaw-ui/src/api/index.ts \
       mateclaw-ui/src/i18n/locales/zh-CN.ts \
       mateclaw-ui/src/i18n/locales/en-US.ts
git commit -m "feat(license-ui): add trial countdown banner and expired overlay"
```

---

## Task 7: Windows Packaging Scripts

**Files:**
- Create: `scripts/windows/start.bat`
- Create: `scripts/windows/stop.bat`
- Create: `scripts/package-trial.sh`

- [ ] **Step 1: Create start.bat**

```bat
@echo off
chcp 65001 >nul 2>&1
title SurveyMind Server

set "APP_HOME=%~dp0"
cd /d "%APP_HOME%"

set "JAVA_HOME=%APP_HOME%jre"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ============================================
echo   SurveyMind - AI Agent Platform
echo   Starting server...
echo ============================================
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JRE not found at %JAVA_HOME%
    echo Please ensure the jre/ folder is present.
    pause
    exit /b 1
)

if not exist "%APP_HOME%license.lic" (
    echo [WARNING] license.lic not found!
    echo The application will start but API access will be blocked.
    echo Please place license.lic in: %APP_HOME%
    echo.
)

echo Access URL: http://localhost:18088
echo Default login: admin / admin123
echo Press Ctrl+C to stop the server.
echo.

java -Xms512m -Xmx2g -jar "%APP_HOME%surveymind-server.jar"
pause
```

- [ ] **Step 2: Create stop.bat**

```bat
@echo off
chcp 65001 >nul 2>&1
echo Stopping SurveyMind Server...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr :18088 ^| findstr LISTENING') do (
    echo Killing process %%a
    taskkill /F /PID %%a >nul 2>&1
)

echo SurveyMind Server stopped.
pause
```

- [ ] **Step 3: Create package-trial.sh**

```bash
#!/usr/bin/env bash
#
# SurveyMind Trial Packaging Script
#
# Usage:
#   ./scripts/package-trial.sh [--customer "客户名"] [--days 30]
#
# Prerequisites:
#   - JDK 21+ on PATH (for building)
#   - Node.js 18+ and pnpm 10+ (for frontend build)
#   - Maven 3.9+ (for backend build)
#   - curl and unzip (for JRE download)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Defaults
CUSTOMER="Trial Customer"
DAYS=30
JRE_VERSION="21.0.7+6"
JRE_ARCHIVE="OpenJDK21U-jre_x64_windows_hotspot_${JRE_VERSION/+/_}.zip"
JRE_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${JRE_VERSION}/OpenJDK21U-jre_x64_windows_hotspot_$(echo ${JRE_VERSION} | tr '+' '_').zip"

# Parse args
while [[ $# -gt 0 ]]; do
    case $1 in
        --customer) CUSTOMER="$2"; shift 2 ;;
        --days) DAYS="$2"; shift 2 ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

VERSION=$(grep '<version>' "$PROJECT_ROOT/mateclaw-server/pom.xml" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
DIST_NAME="SurveyMind-Trial-v${VERSION}"
DIST_DIR="$PROJECT_ROOT/dist/$DIST_NAME"

echo "=== SurveyMind Trial Packaging ==="
echo "Customer : $CUSTOMER"
echo "Trial    : $DAYS days"
echo "Version  : $VERSION"
echo "Output   : dist/$DIST_NAME.zip"
echo "=================================="

# Step 1: Build frontend
echo ""
echo "[1/6] Building frontend..."
cd "$PROJECT_ROOT/mateclaw-ui"
pnpm install --frozen-lockfile
NODE_OPTIONS=--max-old-space-size=6144 pnpm exec vite build \
    --outDir "$PROJECT_ROOT/mateclaw-server/src/main/resources/static" \
    --emptyOutDir

# Step 2: Build plugin-api
echo ""
echo "[2/6] Building plugin-api..."
cd "$PROJECT_ROOT/mateclaw-plugin-api"
mvn install -Dmaven.test.skip=true -q

# Step 3: Build backend JAR
echo ""
echo "[3/6] Building backend JAR..."
cd "$PROJECT_ROOT/mateclaw-server"
mvn clean package -DskipTests -q

# Step 4: Download Windows JRE
echo ""
echo "[4/6] Downloading Windows JRE 21..."
JRE_CACHE="$PROJECT_ROOT/dist/.jre-cache"
mkdir -p "$JRE_CACHE"
if [ ! -f "$JRE_CACHE/$JRE_ARCHIVE" ]; then
    curl -L -o "$JRE_CACHE/$JRE_ARCHIVE" "$JRE_URL"
fi

# Step 5: Assemble distribution
echo ""
echo "[5/6] Assembling distribution..."
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/data"

# JAR
cp "$PROJECT_ROOT/mateclaw-server/target/"*.jar "$DIST_DIR/surveymind-server.jar"

# JRE
cd "$DIST_DIR"
unzip -qo "$JRE_CACHE/$JRE_ARCHIVE"
# Adoptium extracts to a versioned directory; rename to jre/
mv jdk-*-jre jre 2>/dev/null || true

# Scripts
cp "$PROJECT_ROOT/scripts/windows/start.bat" "$DIST_DIR/"
cp "$PROJECT_ROOT/scripts/windows/stop.bat" "$DIST_DIR/"

# Installation guide
cp "$PROJECT_ROOT/docs/SurveyMind-安装指南.md" "$DIST_DIR/" 2>/dev/null || true

# Step 6: Generate trial license
echo ""
echo "[6/6] Generating trial license..."
cd "$DIST_DIR"
java -cp surveymind-server.jar vip.mate.license.LicenseGenerator \
    --customer "$CUSTOMER" --days "$DAYS"

# Create zip
echo ""
echo "Creating zip archive..."
cd "$PROJECT_ROOT/dist"
zip -r "${DIST_NAME}.zip" "$DIST_NAME/" -x "*.DS_Store"

echo ""
echo "=== Done! ==="
echo "Package: dist/${DIST_NAME}.zip"
echo "Size: $(du -h "${DIST_NAME}.zip" | cut -f1)"
```

- [ ] **Step 4: Make script executable**

Run: `chmod +x scripts/package-trial.sh`

- [ ] **Step 5: Commit**

```bash
git add scripts/windows/start.bat \
       scripts/windows/stop.bat \
       scripts/package-trial.sh
git commit -m "feat(packaging): add Windows trial packaging scripts"
```

---

## Task 8: Customer Installation Guide

**Files:**
- Create: `docs/SurveyMind-安装指南.md`

- [ ] **Step 1: Create installation guide**

```markdown
# SurveyMind 安装指南

## 系统要求

- **操作系统**: Windows 10 / 11（64 位）
- **内存**: 4GB 以上（推荐 8GB）
- **磁盘**: 1GB 可用空间
- **网络**: 需要访问 LLM API 服务（如通义千问、OpenAI 等）
- **端口**: 18088（请确保未被占用）

## 安装步骤

### 1. 解压安装包

将 `SurveyMind-Trial-v1.3.0.zip` 解压到任意目录（建议路径不包含中文和空格）。

解压后目录结构：
```
SurveyMind-Trial-v1.3.0/
├── jre/                    ← Java 运行环境（已内置，无需单独安装）
├── data/                   ← 数据目录
├── license.lic             ← 试用授权文件
├── start.bat               ← 启动脚本
├── stop.bat                ← 停止脚本
└── surveymind-server.jar   ← 应用程序
```

### 2. 启动服务

双击 `start.bat`，等待控制台出现如下提示即启动成功：

```
Started MateClawApplication in X.XX seconds
```

> **注意**: 首次启动需要初始化数据库，耗时约 30 秒，请耐心等待。

### 3. 访问系统

打开浏览器（推荐 Chrome / Edge），访问：

```
http://localhost:18088
```

### 4. 登录

使用默认管理员账号登录：

- **用户名**: `admin`
- **密码**: `admin123`

> 建议首次登录后修改默认密码。

### 5. 配置 AI 模型

登录后，进入 **设置 → 模型管理**，添加至少一个 LLM 服务商：

| 服务商 | 需要的凭证 | 获取方式 |
|--------|-----------|---------|
| 通义千问 (DashScope) | API Key | https://dashscope.console.aliyun.com/ |
| OpenAI | API Key | https://platform.openai.com/api-keys |
| 深度求索 (DeepSeek) | API Key | https://platform.deepseek.com/ |

配置完成后即可开始使用 AI 助手功能。

## 停止服务

方式一：在命令行窗口按 `Ctrl + C`

方式二：双击 `stop.bat`

## 试用说明

- 本安装包为 **试用版**，有效期见 `license.lic` 中的授权信息
- 试用到期前 7 天，系统顶部会显示到期提醒
- 到期后系统将停止服务，已有数据不会丢失
- 如需续期或购买正式版，请联系供应商

## 常见问题

### Q: 启动后无法访问 http://localhost:18088？

1. 检查控制台是否有报错信息
2. 确认端口 18088 未被其他程序占用：在 CMD 中执行 `netstat -ano | findstr 18088`
3. 检查 Windows 防火墙是否阻止了访问

### Q: 提示"试用已到期"？

请联系供应商获取新的 `license.lic` 文件，替换安装目录中的旧文件，然后重启服务。

### Q: 想从其他电脑访问？

默认仅本机可访问。如需局域网内其他电脑访问，使用本机 IP 地址替代 `localhost`，例如 `http://192.168.1.100:18088`，并确保 Windows 防火墙放行 18088 端口。

### Q: 数据存储在哪里？

所有数据存储在安装目录的 `data/` 文件夹中。备份时复制整个 `data/` 目录即可。
```

- [ ] **Step 2: Commit**

```bash
git add docs/SurveyMind-安装指南.md
git commit -m "docs: add SurveyMind customer installation guide"
```

---

## Task 9: Build + Verify

- [ ] **Step 1: Run backend compilation check**

```bash
cd mateclaw-server && mvn compile -q
```

Expected: BUILD SUCCESS, no errors.

- [ ] **Step 2: Run frontend build check**

```bash
cd mateclaw-ui && pnpm build
```

Expected: zero type errors, zero build errors.

- [ ] **Step 3: Run backend tests to catch regressions**

```bash
cd mateclaw-server && mvn test -q
```

Expected: All existing tests pass. The new license classes don't break anything because `license.lic` is simply absent in test env → `LicenseStartupValidator` logs a warning but doesn't block.

- [ ] **Step 4: Fix any issues found in steps 1-3**

If compilation or test failures occur, fix them before proceeding.

- [ ] **Step 5: Final commit with any fixes**

```bash
git add -A && git commit -m "fix(license): resolve build/test issues"
```

---

## Task 10: Execute Packaging

- [ ] **Step 1: Run the packaging script**

```bash
./scripts/package-trial.sh --customer "试用客户" --days 30
```

Expected output: `dist/SurveyMind-Trial-v1.3.0.zip` created successfully.

- [ ] **Step 2: Verify the zip contents**

```bash
unzip -l dist/SurveyMind-Trial-v1.3.0.zip | head -20
```

Expected: should contain `jre/`, `surveymind-server.jar`, `start.bat`, `stop.bat`, `license.lic`.

- [ ] **Step 3: Report to user**

Print the final zip location and size for the user to deliver to the customer.
