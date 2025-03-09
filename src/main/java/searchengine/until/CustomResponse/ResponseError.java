package searchengine.until.CustomResponse;

import lombok.Getter;

@Getter
public class ResponseError extends ResponseBoolean {

    private final String error;

    public ResponseError(Boolean result, String error) {
        super(result);
        this.error = error;
    }
}
