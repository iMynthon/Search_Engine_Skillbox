package searchengine.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter @Getter
@AllArgsConstructor
public class ResponseSearch extends ResponseBoolean{

    private boolean result;

    private int count;

    private List<ResultSearchRequest> data;
}
