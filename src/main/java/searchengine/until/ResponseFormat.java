package searchengine.until;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class ResponseFormat {

    private final Boolean result;

    public ResponseFormat(Boolean result) {
        this.result = result;
    }
}
