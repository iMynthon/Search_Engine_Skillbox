package searchengine.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor(force = true)
public class ResponseBoolean {

    private final Boolean result;

    public ResponseBoolean(Boolean result) {
        this.result = result;
    }
}
