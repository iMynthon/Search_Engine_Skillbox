package searchengine.until.CustomResponse;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ResponseBoolean {

    private final Boolean result;

    public ResponseBoolean(Boolean result) {
        this.result = result;
    }
}
