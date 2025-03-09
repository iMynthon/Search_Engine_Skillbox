package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import searchengine.config.ConnectionSetting;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.until.CustomResponse.ResponseBoolean;
import searchengine.until.SiteCrawler;

import java.util.Collections;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

@MockitoSettings(strictness = Strictness.LENIENT)
class IndexingSiteServiceTest {

    @Mock
    private ForkJoinPool pool;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private PageRepository pageRepository;

    @Mock
    private SitesList sitesList;

    @Mock
    private ConnectionSetting connectionSetting;

    @InjectMocks
    private IndexingSiteService indexingSiteService;

    @BeforeEach
    void setUp() {
        openMocks(this);
    }

    @Test
    void testStartIndexingSite_Success() {
        // Arrange
        SiteConfig siteConfig = new SiteConfig();
        siteConfig.setUrl("https://example.com");

        when(sitesList.getSites()).thenReturn(Collections.singletonList(siteConfig));
        when(pool.isShutdown()).thenReturn(false);

        // Act
        ResponseEntity<ResponseBoolean> response = indexingSiteService.startIndexingSite();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getResult());
        verify(siteRepository, times(1)).save(any(Site.class));
        verify(pageRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testStartIndexingSite_AlreadyRunning() {
        // Arrange
        when(!pool.isShutdown()).thenReturn(true);

        // Act
        ResponseEntity<ResponseBoolean> response = indexingSiteService.startIndexingSite();

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(false, response.getBody().getResult());
        assertEquals("Индексация уже запущена", response.getBody().toString());
    }

    @Test
    void testStartIndexingSite_ExceptionThrown() {
        // Arrange
        SiteConfig siteConfig = new SiteConfig();
        siteConfig.setUrl("https://example.com");

        when(sitesList.getSites()).thenReturn(Collections.singletonList(siteConfig));
        when(pool.isShutdown()).thenReturn(false);
        doThrow(new RuntimeException("Ошибка индексации")).when(pool).invoke(any(SiteCrawler.class));

        // Act
        ResponseEntity<ResponseBoolean> response = indexingSiteService.startIndexingSite();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(false, response.getBody().getResult());
        assertEquals("Ошибка при индексация сайта: Ошибка индексации", response.getBody().toString());
        verify(siteRepository, times(1)).save(any(Site.class));
    }

    @Test
    void testStopIndexing_Success() {
        // Arrange
        when(pool.isShutdown()).thenReturn(false);

        // Act
        ResponseEntity<ResponseBoolean> response = indexingSiteService.stopIndexing();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getResult());
        verify(pool, times(1)).shutdownNow();
    }

    @Test
    void testStopIndexing_AlreadyStopped() {
        // Arrange
        when(pool.isShutdown()).thenReturn(true);

        // Act
        ResponseEntity<ResponseBoolean> response = indexingSiteService.stopIndexing();

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(false, response.getBody().getResult());
        assertEquals("Индексация не запущена", response.getBody().toString());
        verify(pool, never()).shutdownNow();
    }

    @Test
    void testStopIndexing_ExceptionThrown() {
        // Arrange
        when(pool.isShutdown()).thenReturn(false);
        doThrow(new RuntimeException("Ошибка остановки")).when(pool).shutdownNow();

        // Act
        ResponseEntity<ResponseBoolean> response = indexingSiteService.stopIndexing();

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(false, response.getBody().getResult());
        assertEquals("Индексация не запущена", response.getBody().toString());
        verify(pool, times(1)).shutdownNow();
    }
}