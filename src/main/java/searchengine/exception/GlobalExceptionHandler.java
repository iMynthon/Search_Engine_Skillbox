package searchengine.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IndexingSitesException.class)
    public AppError catchIndexingException(IndexingSitesException ie) {
        log.error(ie.getMessage(), ie);
        return new AppError(false, ie.getMessage());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ResourcesNotFoundException.class)
    public AppError catchResourceNotFoundException(ResourcesNotFoundException re) {
        log.error(re.getMessage(), re);
        return new AppError(false, re.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public AppError catchAllExceptions(Exception e) {
        log.error("Непредвиденная ошибка: {}", e.getMessage(), e);
        return new AppError(false, "Внутренняя ошибка сервера");
    }

}
