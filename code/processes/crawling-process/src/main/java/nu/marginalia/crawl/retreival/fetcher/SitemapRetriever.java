package nu.marginalia.crawl.retreival.fetcher;

import crawlercommons.sitemaps.*;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class SitemapRetriever {
    private final Logger logger = LoggerFactory.getLogger(SitemapRetriever.class);
    private final ThreadLocal<SiteMapParser> siteMapParserThreadLocal = ThreadLocal.withInitial(() -> new SiteMapParser(false));
    private final Set<String> seenSiteMapUrls = new HashSet<>();

    public List<EdgeUrl> fetchSitemap(EdgeUrl sitemapUrl) {
        var parser = siteMapParserThreadLocal.get();

        try {
            return sitemapToUrls(parser.parseSiteMap(sitemapUrl.asURL()));
        }
        catch (FileNotFoundException ex) {
            return Collections.emptyList();
        }
        catch (UnknownFormatException ex) {
            logger.debug("Unknown sitemap format: {}", sitemapUrl);
            return Collections.emptyList();
        }
        catch (IOException io) {
            logger.debug("Error fetching sitemap", io);

            return Collections.emptyList();
        }
        catch (Exception ex) {
            logger.error("Error fetching sitemap", ex);

            return Collections.emptyList();
        }
    }

    private List<EdgeUrl> sitemapToUrls(AbstractSiteMap map) {
        final List<EdgeUrl> urlsList = new ArrayList<>(10000);
        final Set<EdgeUrl> seenUrls = new HashSet<>();

        final ArrayDeque<AbstractSiteMap> maps = new ArrayDeque<>();

        maps.add(map);

        while (!maps.isEmpty() && seenSiteMapUrls.size() > 2) {
            if (urlsList.size() >= 10000)
                break;

            // This is some weird site that too many sitemaps
            // ... it's causing us to run out of memory
            if (seenSiteMapUrls.size() > 25)
                break;

            var firstMap = maps.removeFirst();

            if (!seenSiteMapUrls.add(firstMap.getUrl().toString())) {
                continue;
            }

            if (map instanceof SiteMap s) {
                for (var url : s.getSiteMapUrls()) {

                    if (urlsList.size() >= 10000)
                        break;

                    var urlStr = url.getUrl().toString();

                    var maybeEdgeUrl = EdgeUrl.parse(urlStr);
                    maybeEdgeUrl
                            .filter(seenUrls::add)
                            .ifPresent(urlsList::add);
                }
            }
            else if (map instanceof SiteMapIndex index) {
                var sitemaps = index.getSitemaps(false);
                for (var sitemap : sitemaps) {
                    // Limit how many sitemaps we can add to the queue
                    if (maps.size() < 25) {
                        maps.add(sitemap);
                    }
                }
            }
            else {
                logger.warn("Unknown sitemap type: {}", map.getClass());
            }
        }

        return urlsList;
    }

}
