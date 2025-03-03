package searchengine.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class AppError {

    Integer statusCode;

    String exceptionMessage;
}
