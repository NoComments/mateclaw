package vip.mate.analytics.controller;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/** Shared validation for analytics dataset Excel uploads. */
final class DatasetFileValidator {

    static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    private DatasetFileValidator() {
    }

    static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file must not be empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "File too large: " + file.getSize() + " bytes (max "
                            + MAX_FILE_BYTES / (1024 * 1024) + " MB)");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(
                    "Only .xlsx files are accepted; received: " + originalName);
        }
    }
}
