package vip.mate.analytics.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Unit tests for {@link DatasetFileValidator}. */
class DatasetFileValidatorTest {

    /** OLE2 compound-file magic — how every .xls (Excel 97-2003) starts. */
    private static final byte[] OLE2_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1};

    /** ZIP local-file-header magic — how every .xlsx starts. */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private static byte[] withMagic(byte[] magic) {
        byte[] out = new byte[64];
        System.arraycopy(magic, 0, out, 0, magic.length);
        return out;
    }

    @Test
    @DisplayName("an .xls renamed to .xlsx is named as a legacy Excel file, not POI internals")
    void renamedXlsExplainsItself() {
        MockMultipartFile renamed = new MockMultipartFile(
                "file", "sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                withMagic(OLE2_MAGIC));

        assertThatThrownBy(() -> DatasetFileValidator.validate(renamed))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> {
                    String msg = e.getMessage();
                    // The user must learn what to DO, not what POI calls its packages.
                    assertThat(msg).contains(".xls").contains(".xlsx");
                    assertThat(msg).doesNotContain("OLE2").doesNotContain("OOXML")
                            .doesNotContain("HSSF").doesNotContain("XSSF").doesNotContain("POI");
                });
    }

    @Test
    @DisplayName("a file that is neither zip nor OLE2 is rejected as not a real workbook")
    void garbageContentRejected() {
        MockMultipartFile garbage = new MockMultipartFile(
                "file", "sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "this is plain text, not a workbook".getBytes());

        assertThatThrownBy(() -> DatasetFileValidator.validate(garbage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    @DisplayName("a real .xlsx passes content sniffing")
    void realXlsxPasses() {
        MockMultipartFile ok = new MockMultipartFile(
                "file", "sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                withMagic(ZIP_MAGIC));

        assertThatCode(() -> DatasetFileValidator.validate(ok)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("extension check still fires before content is read")
    void wrongExtensionStillRejected() {
        MockMultipartFile csv = new MockMultipartFile(
                "file", "sales.csv", "text/csv", withMagic(ZIP_MAGIC));

        assertThatThrownBy(() -> DatasetFileValidator.validate(csv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sales.csv");
    }

    @Test
    @DisplayName("empty and oversized files are still rejected")
    void emptyAndOversizeRejected() {
        assertThatThrownBy(() -> DatasetFileValidator.validate(
                new MockMultipartFile("file", "a.xlsx", null, new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
