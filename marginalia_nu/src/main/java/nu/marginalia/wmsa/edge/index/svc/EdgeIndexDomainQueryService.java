package nu.marginalia.wmsa.edge.index.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Histogram;
import nu.marginalia.util.btree.BTreeQueryBuffer;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import nu.marginalia.wmsa.edge.index.svc.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.svc.query.ResultDomainDeduplicator;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeIdList;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchResults;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.OptionalInt;

import static nu.marginalia.wmsa.edge.index.EdgeIndexService.DYNAMIC_BUCKET_LENGTH;
import static spark.Spark.halt;

@Singleton
public class EdgeIndexDomainQueryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Histogram wmsa_edge_index_domain_query_time = Histogram.build().name("wmsa_edge_index_domain_query_time").linearBuckets(25/1000., 25/1000., 15).help("-").register();

    private final Gson gson = GsonFactory.get();

    private final SearchIndexes indexes;

    @Inject
    public EdgeIndexDomainQueryService(SearchIndexes indexes) {
        this.indexes = indexes;
    }

    public Object searchDomain(Request request, Response response) {
        if (indexes.getLexiconReader() == null) {
            logger.warn("Dictionary reader not yet initialized");
            halt(HttpStatus.SC_SERVICE_UNAVAILABLE, "Come back in a few minutes");
        }

        String json = request.body();
        EdgeDomainSearchSpecification specsSet = gson.fromJson(json, EdgeDomainSearchSpecification.class);

        try {
            return wmsa_edge_index_domain_query_time.time(() -> queryDomain(specsSet));
        }
        catch (HaltException ex) {
            logger.warn("Halt", ex);
            throw ex;
        }
        catch (Exception ex) {
            logger.info("Error during domain search {}({}) (query: {})", ex.getClass().getSimpleName(), ex.getMessage(), json);
            logger.info("Error", ex);
            Spark.halt(500, "Error");
            return null;
        }
    }

    public EdgeDomainSearchResults queryDomain(EdgeDomainSearchSpecification specsSet) {

        final OptionalInt wordId = lookUpWord(specsSet.keyword);
        final EdgeIdList<EdgeUrl> urlIds = new EdgeIdList<>();

        final IndexSearchBudget budget = new IndexSearchBudget(50);

        if (wordId.isEmpty()) {
            return new EdgeDomainSearchResults(specsSet.keyword, urlIds);
        }

        BTreeQueryBuffer buffer = new BTreeQueryBuffer(512);

        for (int bucket = 0; budget.hasTimeLeft() && bucket < DYNAMIC_BUCKET_LENGTH+1; bucket++) {

            final ResultDomainDeduplicator localFilter = new ResultDomainDeduplicator(1);
            var query = indexes.getBucket(bucket).getDomainQuery(wordId.getAsInt(), localFilter);

            while (query.hasMore() && urlIds.size() < specsSet.maxResults) {
                query.getMoreResults(buffer);

                for (int i = 0; i < buffer.end && urlIds.size() < specsSet.maxResults; i++) {
                    long result = buffer.data[i];
                    if (localFilter.test(result)) {
                        urlIds.add((int) (result & 0xFFFF_FFFFL));
                    }
                }
            }
        }

        return new EdgeDomainSearchResults(specsSet.keyword, urlIds);
    }

    private OptionalInt lookUpWord(String s) {
        int ret = indexes.getLexiconReader().get(s);
        if (ret == DictionaryHashMap.NO_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ret);
    }

}