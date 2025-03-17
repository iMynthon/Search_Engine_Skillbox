package searchengine.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ResponseError extends ResponseBoolean{

   private final Exception exception;

}
