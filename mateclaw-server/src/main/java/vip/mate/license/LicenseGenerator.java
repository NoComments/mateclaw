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
