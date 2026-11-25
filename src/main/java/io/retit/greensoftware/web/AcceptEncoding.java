package io.retit.greensoftware.web;

import java.util.Locale;

/** Parses the request header of the same name. */
final class AcceptEncoding {

    private AcceptEncoding() {
    }

    /** Whether the header, e.g. {@code gzip, deflate;q=0.5}, allows a response with gzip. */
    static boolean allowsGzip(String header) {
        if (header == null) {
            return false;
        }
        boolean allowsAny = false;
        for (String element : header.toLowerCase(Locale.ROOT).split(",")) {
            String[] parts = element.split(";");
            String coding = parts[0].trim();
            boolean allowed = true;
            for (int i = 1; i < parts.length; i++) {
                // Only a weight of zero rules a coding out
                allowed &= !parts[i].replace(" ", "").matches("q=0(\\.0*)?");
            }
            if (coding.equals("gzip")) {
                return allowed;
            }
            if (coding.equals("*")) {
                allowsAny = allowed;
            }
        }
        return allowsAny;
    }
}
