package com.accsaber.backend.service.media;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.exception.ValidationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/avif", "image/gif");

    private final CdnProperties cdn;
    private final WebClient downloadClient = WebClient.builder().build();

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
        Path target = targetPath(subdir, key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("failed to delete CDN file {}", target, e);
        }
    }

    private String encodeAndPublish(String subdir, String key, String inputSuffix, InputPopulator populator) {
        Path baseDir = baseDir(subdir);
        Path target = baseDir.resolve(key + ".avif");
        Path tempInput = null;
        Path tempOutput = null;
        try {
            Files.createDirectories(baseDir);
            tempInput = Files.createTempFile("cdn-in-", inputSuffix);
            tempOutput = Files.createTempFile("cdn-out-", ".avif");
            populator.populate(tempInput);

            runVips(tempInput, tempOutput);
            atomicMove(tempOutput, target);
        } catch (IOException e) {
            log.error("CDN store I/O failure for {}/{}", subdir, key, e);
            throw new MediaProcessingException("Failed to store image");
        } finally {
            deleteQuietly(tempInput);
            deleteQuietly(tempOutput);
        }
        return cdn.getBaseUrl() + "/" + subdir + "/" + key + ".avif?v=" + Instant.now().getEpochSecond();
    }

    private void atomicMove(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path baseDir(String subdir) {
        return Paths.get(cdn.getStoragePath(), subdir).toAbsolutePath().normalize();
    }

    private Path targetPath(String subdir, String key) {
        return baseDir(subdir).resolve(key + ".avif");
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
        } catch (RuntimeException e) {
            throw new MediaProcessingException("Failed to download remote image: " + sourceUrl, e);
        }
        if (body == null || body.length == 0) {
            throw new MediaProcessingException("Remote image is empty: " + sourceUrl);
        }
        if (body.length > cdn.getMaxUploadBytes()) {
            throw new MediaProcessingException("Remote image exceeds max upload size");
        }
        Files.write(target, body);
    }

    private void runVips(Path input, Path output) {
        String outputArg = output + "[Q=" + cdn.getAvifQuality()
                + ",compression=av1,effort=" + cdn.getAvifEffort() + "]";
        ProcessBuilder pb = new ProcessBuilder(
                cdn.getVipsBinary(),
                "thumbnail",
                input.toString(),
                outputArg,
                String.valueOf(cdn.getMaxDimension()));
        pb.redirectErrorStream(true);
        Process proc = null;
        try {
            proc = pb.start();
            byte[] stdoutBytes = proc.getInputStream().readAllBytes();
            if (!proc.waitFor(cdn.getEncodeTimeoutMs(), TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
                log.error("vips encode timed out after {}ms (input={}, max-dim={})",
                        cdn.getEncodeTimeoutMs(), input, cdn.getMaxDimension());
                throw new MediaProcessingException("Image encoding timed out");
            }
            int exit = proc.exitValue();
            if (exit != 0) {
                log.error("vips exited with {} encoding {} -> {}: {}",
                        exit, input, output, new String(stdoutBytes));
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
