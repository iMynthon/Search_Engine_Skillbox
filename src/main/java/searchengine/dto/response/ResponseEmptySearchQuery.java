package searchengine.dto.response;

import lombok.Getter;

@Getter
public class ResponseEmptySearchQuery extends ResponseBoolean{

    private final String error;

    public ResponseEmptySearchQuery(Boolean result, String error) {
        super(result);
        this.error = error;
    }
}
