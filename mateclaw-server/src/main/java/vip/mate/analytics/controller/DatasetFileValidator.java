package vip.mate.analytics.controller;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

/** Shared validation for analytics dataset Excel uploads. */
final class DatasetFileValidator {

    static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    /** ZIP local-file-header magic — every .xlsx is a zip container. */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** OLE2 compound-file magic — every .xls (Excel 97-2003) starts with it. */
    private static final byte[] OLE2_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1};

    private DatasetFileValidator() {
    }

    /**
     * Rejects anything the Excel path cannot read, before POI is handed the bytes.
     *
     * <p>The content check exists because the extension alone lies: renaming a .xls
     * to .xlsx is a common workaround, and POI's own complaint about it names its
     * internal packages ("OLE2 Format", "HSSF instead of XSSF") — accurate, and
     * meaningless to whoever is holding the spreadsheet.
     *
     * @throws IllegalArgumentException with a message intended for the uploader
     */
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
        validateContent(file, originalName);
    }

    private static void validateContent(MultipartFile file, String originalName) {
        byte[] head = readHead(file);
        if (startsWith(head, ZIP_MAGIC)) {
            return;
        }
        if (startsWith(head, OLE2_MAGIC)) {
            throw new IllegalArgumentException(
                    "「" + originalName + "」实际是旧版 .xls 格式（Excel 97-2003），"
                            + "改扩展名不会改变文件本身。请在 Excel 中打开它，"
                            + "选择「另存为」→「Excel 工作簿 (.xlsx)」，然后上传新文件。");
        }
        throw new IllegalArgumentException(
                "「" + originalName + "」不是有效的 .xlsx 文件，内容无法识别为 Excel 工作簿。");
    }

    /** Reads the first bytes without consuming the caller's later stream. */
    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(OLE2_MAGIC.length);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取上传的文件: " + e.getMessage());
        }
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        return head.length >= magic.length
                && Arrays.equals(head, 0, magic.length, magic, 0, magic.length);
    }
}
