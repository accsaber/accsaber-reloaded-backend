package com.accsaber.backend.service.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.accsaber.backend.exception.ValidationException;

final class MapZipSanitizer {

    static final int MAX_ENTRIES = 40;
    static final int MAX_TOTAL_BYTES = 48 * 1024 * 1024;
    static final int MAX_CHART_BYTES = 16 * 1024 * 1024;

    private static final Set<String> CHART_EXTENSIONS = Set.of("dat", "json");
    private static final Set<String> MEDIA_EXTENSIONS = Set.of("egg", "ogg", "wav", "png", "jpg", "jpeg");
    private static final Pattern FLAT_NAME = Pattern.compile("[\\p{L}\\p{N} ._()\\[\\]+,'&!-]{1,120}");
    private static final String INFO = "info.dat";

    private record Entry(String name, byte[] content) {
    }

    private MapZipSanitizer() {
    }

    static byte[] sanitize(byte[] upload) {
        Map<String, Entry> entries = read(upload);
        Entry info = entries.get(INFO);
        if (info == null || !startsLikeJsonObject(info.content())) {
            throw new ValidationException("This is not a Beat Saber map zip. It needs an Info.dat at the top level");
        }
        return write(entries);
    }

    private static Map<String, Entry> read(byte[] upload) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(upload))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                int limit = Math.min(limitFor(entry, name), MAX_TOTAL_BYTES - total);
                if (entries.size() == MAX_ENTRIES) {
                    throw new ValidationException("The zip has more than " + MAX_ENTRIES + " files");
                }
                byte[] content = zip.readNBytes(limit + 1);
                if (content.length > limit) {
                    throw new ValidationException("The map unpacks to more than this check accepts");
                }
                total += content.length;
                if (entries.put(name.toLowerCase(Locale.ROOT), new Entry(name, content)) != null) {
                    throw new ValidationException("The zip has two files with the same name");
                }
            }
        } catch (IOException e) {
            throw new ValidationException("Could not read the zip");
        }
        return entries;
    }

    private static int limitFor(ZipEntry entry, String name) {
        if (entry.isDirectory() || !FLAT_NAME.matcher(name).matches()) {
            throw new ValidationException("Put the map files at the top level of the zip, with no folders");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (CHART_EXTENSIONS.contains(extension)) {
            return MAX_CHART_BYTES;
        }
        if (MEDIA_EXTENSIONS.contains(extension)) {
            return MAX_TOTAL_BYTES;
        }
        throw new ValidationException("The zip has a file that is not part of a map: " + name);
    }

    private static boolean startsLikeJsonObject(byte[] content) {
        for (byte b : content) {
            if (b == '{') {
                return true;
            }
            if (!Character.isWhitespace(b) && b != (byte) 0xEF && b != (byte) 0xBB && b != (byte) 0xBF) {
                return false;
            }
        }
        return false;
    }

    private static byte[] write(Map<String, Entry> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.setLevel(Deflater.BEST_SPEED);
            for (Entry entry : entries.values()) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new ValidationException("Could not read the zip");
        }
        return bytes.toByteArray();
    }
}
