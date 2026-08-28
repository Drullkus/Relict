package us.drullk.relict.companion;

/**
 * A structured job failure -- the message ends up verbatim in the response JSON's {@code message} field,
 * so every throw site should read like something a caller could act on (never a bare stack trace).
 */
final class CompanionException extends RuntimeException {

    CompanionException(String message) {
        super(message);
    }

    CompanionException(String message, Throwable cause) {
        super(message, cause);
    }
}
