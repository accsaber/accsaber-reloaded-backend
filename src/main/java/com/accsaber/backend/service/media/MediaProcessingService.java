package com.accsaber.backend.service.media;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.annotation.PostConstruct;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.exception.ValidationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    public static final Set<String> ALLOWED_MIME = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/avif", "image/gif");

    private final CdnProperties cdn;
    private WebClient downloadClient;

    @PostConstruct
    void initDownloadClient() {
        int bufferBytes = (int) Math.min(cdn.getMaxUploadBytes(), Integer.MAX_VALUE);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(bufferBytes))
                .build();
        this.downloadClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .build();
    }

    public String storeImage(MultipartFile file, String subdir, String key) {
        validate(file);
        return encodeAndPublish(
                subdir,
                key,
                suffixFor(file),
                tempInput -> file.transferTo(tempInput.toFile()));
    }

    public String storeFromUrl(String sourceUrl, String subdir, String key) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new MediaProcessingException("Source URL is required");
        }
        return encodeAndPublish(
                subdir,
                key,
                suffixFromUrl(sourceUrl),
                tempInput -> downloadTo(sourceUrl, tempInput));
    }

    public boolean fileExists(String subdir, String key) {
        return Files.exists(targetPath(subdir, key));
    }

    public void deleteIfExists(String subdir, String key) {
        deletePathIfExists(targetPath(subdir, key));
        deletePathIfExists(pngTargetPath(subdir, key));
        deletePathIfExists(gifTargetPath(subdir, key));
    }

    public boolean avifExists(String subdir, String key) {
        return Files.exists(targetPath(subdir, key));
    }

    private void deletePathIfExists(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("failed to delete CDN file {}", target, e);
        }
    }

    private String encodeAndPublish(String subdir, String key, String inputSuffix, InputPopulator populator) {
        Path baseDir = baseDir(subdir);
        Path avifTarget = baseDir.resolve(key + ".avif");
        Path tempInput = null;
        Path tempAvif = null;
        Path tempStatic = null;
        String staticExt;
        try {
            Files.createDirectories(baseDir);
            tempInput = Files.createTempFile("cdn-in-", inputSuffix);
            populator.populate(tempInput);

            boolean animated = sourcePageCount(tempInput) > 1;
            staticExt = animated ? ".gif" : ".png";

            tempAvif = Files.createTempFile("cdn-out-", ".avif");
            tempStatic = Files.createTempFile("cdn-out-", staticExt);

            runVipsAvif(tempInput, tempAvif, animated);
            if (animated) {
                runVips(tempInput + "[n=-1]", tempStatic.toString());
            } else {
                runVipsPng(tempInput, tempStatic);
            }

            Path staticTarget = baseDir.resolve(key + staticExt);
            atomicMove(tempAvif, avifTarget);
            atomicMove(tempStatic, staticTarget);
            makeWorldReadable(avifTarget);
            makeWorldReadable(staticTarget);

            Path otherStatic = baseDir.resolve(key + (animated ? ".png" : ".gif"));
            deletePathIfExists(otherStatic);
        } catch (IOException e) {
            log.error("CDN store I/O failure for {}/{}", subdir, key, e);
            throw new MediaProcessingException("Failed to store image");
        } finally {
            deleteQuietly(tempInput);
            deleteQuietly(tempAvif);
            deleteQuietly(tempStatic);
        }
        return cdn.getBaseUrl() + "/" + subdir + "/" + key + staticExt + "?v=" + Instant.now().getEpochSecond();
    }

    private int sourcePageCount(Path input) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cdn.getVipsBinary(), "getfield", input.toString(), "n-pages");
            pb.environment().put("VIPS_CONCURRENCY", "1");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            byte[] out = proc.getInputStream().readAllBytes();
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return 1;
            }
            if (proc.exitValue() != 0) return 1;
            return Integer.parseInt(new String(out).trim());
        } catch (IOException | InterruptedException | NumberFormatException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return 1;
        }
    }

    public String regenerateStaticVariant(String subdir, String key) {
        Path avif = targetPath(subdir, key);
        if (!Files.exists(avif)) return null;
        boolean animated = sourcePageCount(avif) > 1;
        String ext = animated ? ".gif" : ".png";
        Path baseDir = baseDir(subdir);
        Path target = baseDir.resolve(key + ext);
        Path other = baseDir.resolve(key + (animated ? ".png" : ".gif"));
        Path tempOutput = null;
        try {
            tempOutput = Files.createTempFile("cdn-out-", ext);
            if (animated) {
                runVips(avif + "[n=-1]", tempOutput.toString());
            } else {
                runVipsPng(avif, tempOutput);
            }
            atomicMove(tempOutput, target);
            makeWorldReadable(target);
            deletePathIfExists(other);
        } catch (IOException e) {
            log.error("static-variant regen I/O failure for {}/{}", subdir, key, e);
            throw new MediaProcessingException("Failed to regenerate static variant");
        } finally {
            deleteQuietly(tempOutput);
        }
        return cdn.getBaseUrl() + "/" + subdir + "/" + key + ext + "?v=" + Instant.now().getEpochSecond();
    }

    private void atomicMove(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void makeWorldReadable(Path file) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("could not set POSIX perms on {}: {}", file, e.getMessage());
        }
    }

    public int repairAllPermissions() {
        Path root = Paths.get(cdn.getStoragePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("CDN storage path is not a directory: {}", root);
            return 0;
        }
        Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rw-r--r--");
        Set<PosixFilePermission> dirPerms = PosixFilePermissions.fromString("rwxr-xr-x");
        int[] repaired = {0};
        try (var stream = Files.walk(root)) {
            stream.forEach(p -> {
                try {
                    Set<PosixFilePermission> current = Files.getPosixFilePermissions(p);
                    Set<PosixFilePermission> wanted = Files.isDirectory(p) ? dirPerms : filePerms;
                    if (!current.equals(wanted)) {
                        Files.setPosixFilePermissions(p, wanted);
                        repaired[0]++;
                    }
                } catch (UnsupportedOperationException | IOException e) {
                    log.debug("could not repair perms on {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("CDN permission repair failed walking {}", root, e);
        }
        log.info("CDN permission repair: {} entries updated under {}", repaired[0], root);
        return repaired[0];
    }

    private Path baseDir(String subdir) {
        return Paths.get(cdn.getStoragePath(), subdir).toAbsolutePath().normalize();
    }

    private Path targetPath(String subdir, String key) {
        return baseDir(subdir).resolve(key + ".avif");
    }

    private Path pngTargetPath(String subdir, String key) {
        return baseDir(subdir).resolve(key + ".png");
    }

    private Path gifTargetPath(String subdir, String key) {
        return baseDir(subdir).resolve(key + ".gif");
    }

    private void downloadTo(String sourceUrl, Path target) throws IOException {
        byte[] body;
        try {
            body = downloadClient.get()
                    .uri(URI.create(sourceUrl))
                    .accept(MediaType.ALL)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(Duration.ofMillis(cdn.getEncodeTimeoutMs()));
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 429) {
                throw new MediaProcessingException(
                        "Upstream rate-limited (429): " + sourceUrl, e);
            }
            if (code >= 400 && code < 500) {
                throw new MediaUnavailableException(
                        "Upstream returned " + code + ": " + sourceUrl, e);
            }
            throw new MediaProcessingException("Failed to download remote image: " + sourceUrl, e);
        } catch (RuntimeException e) {
            throw new MediaProcessingException("Failed to download remote image: " + sourceUrl, e);
        }
        if (body == null || body.length == 0) {
            throw new MediaUnavailableException("Remote image is empty: " + sourceUrl);
        }
        if (body.length > cdn.getMaxUploadBytes()) {
            throw new MediaProcessingException("Remote image exceeds max upload size");
        }
        Files.write(target, body);
    }

    private void runVipsAvif(Path input, Path output, boolean animated) {
        String outputArg = output + "[Q=" + cdn.getAvifQuality()
                + ",compression=av1,effort=" + cdn.getAvifEffort() + "]";
        if (animated) {
            try {
                runVips(input + "[n=-1]", outputArg);
                return;
            } catch (MediaProcessingException e) {
                log.info("animated AVIF encode failed for {}, retrying single-frame", input);
            }
        }
        runVips(input.toString(), outputArg);
    }

    private void runVipsPng(Path input, Path output) {
        runVips(input.toString(), output.toString());
    }

    private void runVips(String inputArg, String outputArg) {
        ProcessBuilder pb = new ProcessBuilder(
                cdn.getVipsBinary(),
                "thumbnail",
                inputArg,
                outputArg,
                String.valueOf(cdn.getMaxDimension()));
        pb.environment().put("VIPS_CONCURRENCY", "1");
        pb.redirectErrorStream(true);
        Process proc = null;
        try {
            proc = pb.start();
            byte[] stdoutBytes = proc.getInputStream().readAllBytes();
            if (!proc.waitFor(cdn.getEncodeTimeoutMs(), TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
                log.error("vips encode timed out after {}ms (input={}, max-dim={})",
                        cdn.getEncodeTimeoutMs(), inputArg, cdn.getMaxDimension());
                throw new MediaProcessingException("Image encoding timed out");
            }
            int exit = proc.exitValue();
            if (exit != 0) {
                log.error("vips exited with {} encoding {} -> {}: {}",
                        exit, inputArg, outputArg, new String(stdoutBytes));
                throw new MediaProcessingException("Image could not be encoded");
            }
        } catch (IOException e) {
            log.error("vips invocation failed (binary={})", cdn.getVipsBinary(), e);
            throw new MediaProcessingException("Image encoder is unavailable");
        } catch (InterruptedException e) {
            if (proc != null) proc.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new MediaProcessingException("Image encoding interrupted");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("file", "is required");
        }
        if (file.getSize() > cdn.getMaxUploadBytes()) {
            throw new ValidationException("file", "exceeds max upload size of " + cdn.getMaxUploadBytes() + " bytes");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime.toLowerCase())) {
            throw new ValidationException("file", "unsupported media type: " + mime);
        }
    }

    private String suffixFor(MultipartFile file) {
        return suffixFromName(file.getOriginalFilename());
    }

    private String suffixFromUrl(String url) {
        int q = url.indexOf('?');
        return suffixFromName(q < 0 ? url : url.substring(0, q));
    }

    private String suffixFromName(String name) {
        if (name == null) return ".bin";
        int dot = name.lastIndexOf('.');
        int slash = name.lastIndexOf('/');
        if (dot < 0 || dot < slash || dot == name.length() - 1) return ".bin";
        String ext = name.substring(dot).toLowerCase();
        return ext.length() <= 6 ? ext : ".bin";
    }

    private void deleteQuietly(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    @FunctionalInterface
    private interface InputPopulator {
        void populate(Path target) throws IOException;
    }
}
