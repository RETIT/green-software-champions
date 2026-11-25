package io.retit.greensoftware.sync.provider;

/**
 * Thrown by a {@link CarbonProvider} when carbon data could not be fetched completely,
 * so that callers never mistake partial data for the real total.
 */
public class CarbonDataUnavailableException extends RuntimeException {

    public CarbonDataUnavailableException(String message) {
        super(message);
    }

    public CarbonDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
