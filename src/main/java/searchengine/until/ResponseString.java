package searchengine.until;

import lombok.Getter;

@Getter
public class ResponseString extends ResponseFormat{

    private final String error;

    public ResponseString(Boolean result,String error) {
        super(result);
        this.error = error;
    }
}
