package searchengine.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Setter @Getter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseSearch extends ResponseBoolean implements Serializable {

    private Boolean result;

    private Integer count;

    private List<ResultSearchRequest> data;
}
