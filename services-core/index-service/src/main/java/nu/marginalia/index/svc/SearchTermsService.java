package nu.marginalia.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.dict.OffHeapDictionaryHashMap;
import nu.marginalia.index.client.model.query.EdgeSearchSubquery;
import nu.marginalia.index.index.SearchIndexSearchTerms;
import nu.marginalia.lexicon.KeywordLexiconReadOnlyView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalInt;

@Singleton
public class SearchTermsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final KeywordLexiconReadOnlyView lexicon;

    @Inject
    public SearchTermsService(KeywordLexiconReadOnlyView lexicon) {
        this.lexicon = lexicon;
    }

    public SearchIndexSearchTerms getSearchTerms(EdgeSearchSubquery request) {
        final IntList excludes = new IntArrayList();
        final IntList includes = new IntArrayList();
        final IntList priority = new IntArrayList();

        for (var include : request.searchTermsInclude) {
            var word = lookUpWord(include);
            if (word.isEmpty()) {
                logger.info("Unknown search term: " + include);
                return new SearchIndexSearchTerms();
            }
            includes.add(word.getAsInt());
        }

        for (var advice : request.searchTermsAdvice) {
            var word = lookUpWord(advice);
            if (word.isEmpty()) {
                logger.info("Unknown search term: " + advice);
                return new SearchIndexSearchTerms();
            }
            includes.add(word.getAsInt());
        }

        for (var exclude : request.searchTermsExclude) {
            lookUpWord(exclude).ifPresent(excludes::add);
        }
        for (var exclude : request.searchTermsPriority) {
            lookUpWord(exclude).ifPresent(priority::add);
        }

        return new SearchIndexSearchTerms(includes, excludes, priority);
    }


    public OptionalInt lookUpWord(String s) {
        int ret = lexicon.get(s);
        if (ret == OffHeapDictionaryHashMap.NO_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ret);
    }
}