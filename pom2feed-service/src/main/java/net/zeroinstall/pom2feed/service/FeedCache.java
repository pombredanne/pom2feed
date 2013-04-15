package net.zeroinstall.pom2feed.service;

import static com.google.common.base.Throwables.propagate;
import com.google.common.cache.*;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.maven.model.building.ModelBuildingException;
import org.xml.sax.SAXException;

/**
 * Caches requests for feeds in a thread-safe manner.
 */
public class FeedCache implements FeedProvider {

    private final LoadingCache<String, String> cache;

    public FeedCache(final FeedProvider backingProvider) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(
                new CacheLoader<String, String>() {
            @Override
            public String load(String key) throws Exception {
                return backingProvider.getFeed(key);
            }
        });
    }

    @Override
    public String getFeed(final String artifactPath) throws IOException, SAXException, ModelBuildingException {
        try {
            return cache.get(artifactPath);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException) {
                throw (IOException) (ex.getCause());
            } else if (ex.getCause() instanceof SAXException) {
                throw (SAXException) (ex.getCause());
            } else if (ex.getCause() instanceof ModelBuildingException) {
                throw (ModelBuildingException) (ex.getCause());
            } else {
                throw propagate(ex);
            }
        }
    }
}
