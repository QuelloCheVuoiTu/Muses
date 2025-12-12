package questing.exceptions;

public class ChatServiceException extends RuntimeException {
    private final int status;
    private final String body;

    public ChatServiceException(String message, int status, String body) {
        super(message);
        this.status = status;
        this.body = body;
    }

    public ChatServiceException(String message, Throwable cause, int status, String body) {
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
