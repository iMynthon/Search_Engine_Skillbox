package searchengine.until;

public class ResponseFormat {

    private final String nameResponse;

    public ResponseFormat(String nameResponse, Boolean value) {
        this.nameResponse = nameResponse;
    }

    public ResponseFormat(String nameResponse, String valueOfMessage) {
        this.nameResponse = nameResponse;
    }
}
