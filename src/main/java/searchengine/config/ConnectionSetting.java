package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "connection-settings")
public class ConnectionSetting {

    @Getter @Setter
    public static class Setting{
        private String userAgent;
        private String referrer;
    }

    private List<Setting> settings;
}
