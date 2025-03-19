package searchengine.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
public class ResponseError extends ResponseBoolean {

   private final Exception exception;

}
