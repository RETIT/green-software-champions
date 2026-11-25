package io.retit.greensoftware.web.render;

import jakarta.ws.rs.core.EntityTag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

/**
 * A page as it is sent.
 *
 * @param etag     of {@code html}, a hash of it
 * @param gzipEtag of {@code gzip}, as it is another representation of the page
 */
public record RenderedPage(byte[] html, EntityTag etag, byte[] gzip, EntityTag gzipEtag) {

    private static final int ETAG_BYTES = 16;

    static RenderedPage of(String html) {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String hash = hash(bytes);
        return new RenderedPage(bytes, new EntityTag(hash), gzip(bytes), new EntityTag(hash + "-gzip"));
    }

    private static String hash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(Arrays.copyOf(digest, ETAG_BYTES));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform has SHA-256", e);
        }
    }

    private static byte[] gzip(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
