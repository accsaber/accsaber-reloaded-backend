package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.accsaber.backend.exception.ValidationException;

class MapZipSanitizerTest {

    private static final byte[] INFO = "{\"_version\":\"2.0.0\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void aMapZipComesBackRebuiltWithTheSameFiles() throws IOException {
        byte[] clean = MapZipSanitizer.sanitize(zip(file("Info.dat", INFO), file("ExpertPlusStandard.dat", INFO),
                file("song.egg", new byte[2048]), file("カバー.jpg", new byte[64])));

        List<String> names = new ArrayList<>();
        try (ZipInputStream rebuilt = new ZipInputStream(new ByteArrayInputStream(clean))) {
            for (ZipEntry entry = rebuilt.getNextEntry(); entry != null; entry = rebuilt.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        assertThat(names).containsExactly("Info.dat", "ExpertPlusStandard.dat", "song.egg", "カバー.jpg");
    }

    @ParameterizedTest
    @ValueSource(strings = { "../Info.dat", "/etc/passwd", "maps/Expert.dat", "C:\\Info.dat", "run.exe", "inner.zip",
            "script.py", "noextension" })
    void aFileOutsideTheTopLevelOrOfAnotherKindIsRefused(String name) {
        assertThatThrownBy(() -> MapZipSanitizer.sanitize(zip(file("Info.dat", INFO), file(name, INFO))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void aZipThatUnpacksPastTheLimitIsRefusedHoweverSmallItIs() {
        byte[] bomb = zip(file("Info.dat", INFO), file("Expert.dat", new byte[MapZipSanitizer.MAX_CHART_BYTES + 1]));

        assertThat(bomb.length).isLessThan(100_000);
        assertThatThrownBy(() -> MapZipSanitizer.sanitize(bomb)).isInstanceOf(ValidationException.class);
    }

    @Test
    void somethingThatIsNotAMapIsRefused() {
        assertThatThrownBy(() -> MapZipSanitizer.sanitize("not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MapZipSanitizer.sanitize(zip(file("song.egg", new byte[8]))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MapZipSanitizer.sanitize(zip(file("Info.dat", "MZ".getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MapZipSanitizer.sanitize(zip(file("Info.dat", INFO), file("info.DAT", INFO))))
                .isInstanceOf(ValidationException.class);
    }

    private record ZipFile(String name, byte[] content) {
    }

    private static ZipFile file(String name, byte[] content) {
        return new ZipFile(name, content);
    }

    private static byte[] zip(ZipFile... files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (ZipFile file : files) {
                zip.putNextEntry(new ZipEntry(file.name()));
                zip.write(file.content());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }
}
