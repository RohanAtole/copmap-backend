package in.copmap.exception;

import lombok.Getter;

@Getter
public class CopMapException extends RuntimeException {
    private final String code;

    public CopMapException(String code, String message) {
        super(message);
        this.code = code;
    }
}
