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
