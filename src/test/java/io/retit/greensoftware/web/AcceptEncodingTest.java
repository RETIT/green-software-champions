package io.retit.greensoftware.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptEncodingTest {

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "gzip, deflate, br, zstd", "br;q=1.0, GZIP;q=0.8", "*", "*;q=0, gzip", "gzip;q=0.001"})
    void allowed(String header) {
        assertTrue(AcceptEncoding.allowsGzip(header));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "identity", "br", "gzip;q=0", "gzip; q=0.000, *", "*;q=0"})
    void notAllowed(String header) {
        assertFalse(AcceptEncoding.allowsGzip(header));
    }
}
