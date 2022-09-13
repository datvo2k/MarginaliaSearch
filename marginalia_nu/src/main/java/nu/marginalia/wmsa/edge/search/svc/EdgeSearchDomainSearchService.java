package nu.marginalia.wmsa.edge.search.svc;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeIdList;
import nu.marginalia.wmsa.edge.model.id.EdgeIdSet;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EdgeSearchDomainSearchService {

    private final EdgeIndexClient indexClient;
    private final EdgeDataStoreDao edgeDataStoreDao;

    @Inject
    public EdgeSearchDomainSearchService(EdgeIndexClient indexClient, EdgeDataStoreDao edgeDataStoreDao) {
        this.indexClient = indexClient;
        this.edgeDataStoreDao = edgeDataStoreDao;
    }

    public List<BrowseResult> getDomainResults(Context ctx, EdgeSearchSpecification specs) {

        List<String> keywords = specs.subqueries.stream()
                .filter(sq -> sq.searchTermsExclude.isEmpty() && sq.searchTermsInclude.size() == 1)
                .map(sq -> sq.searchTermsInclude.get(0))
                .distinct()
                .toList();

        if (keywords.isEmpty())
            return Collections.emptyList();

        List<EdgeDomainSearchSpecification> requests = new ArrayList<>(keywords.size() * specs.buckets.size());

        for (var keyword : keywords) {
            for (var bucket : specs.buckets) {
                requests.add(new EdgeDomainSearchSpecification(bucket, IndexBlock.Link, keyword,
                        1_000_000, 3, 25));
            }
        }

        EdgeIdSet<EdgeUrl> dedup = new EdgeIdSet<>();
        EdgeIdList<EdgeUrl> values = new EdgeIdList<>();

        for (var result : indexClient.queryDomains(ctx, requests)) {
            for (int id : result.getResults().values()) {
                if (dedup.add(id))
                    values.add(id);
            }
        }

        return edgeDataStoreDao.getBrowseResultFromUrlIds(values);
    }


}