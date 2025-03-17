package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "connection-settings")
@EnableScheduling
public class ConnectionSetting {

    @Getter @Setter
    public static class Setting{
        private String userAgent;
        private String referrer;
    }

    private List<Setting> settings;

    private String currentUserAgent;
    private String currentReferrer;

    @Scheduled(fixedRate = 30000)
    public void updateSettingConnection(){
        Random random = new Random();
        Setting selectSetting = settings.get(random.nextInt(settings.size() - 1));
        this.currentUserAgent = selectSetting.getUserAgent();
        this.currentReferrer = selectSetting.getReferrer();
        log.info("Текущие настройки соединения - User Agent: {} - Referrer: {}", currentUserAgent, currentReferrer);
    }
}
