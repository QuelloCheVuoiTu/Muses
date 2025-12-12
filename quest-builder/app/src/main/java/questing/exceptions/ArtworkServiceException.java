package questing.exceptions;

/**
 * Runtime exception used by MuseumService to propagate upstream HTTP errors
 * and client failures in a structured way.
 */
public class ArtworkServiceException extends RuntimeException {
    private final int status;
    private final String body;

    public ArtworkServiceException(String message, int status, String body) {
        super(message);
        this.status = status;
        this.body = body;
    }

    public ArtworkServiceException(String message, Throwable cause, int status, String body) {
        super(message, cause);
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}
