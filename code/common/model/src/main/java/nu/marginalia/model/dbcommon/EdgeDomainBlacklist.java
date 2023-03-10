package nu.marginalia.model.dbcommon;

import com.google.inject.ImplementedBy;
import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.id.EdgeId;

@ImplementedBy(EdgeDomainBlacklistImpl.class)
public interface EdgeDomainBlacklist {
    boolean isBlacklisted(int domainId);
    default boolean isBlacklisted(EdgeId<EdgeDomain> domainId) {
        return isBlacklisted(domainId.id());
    }
    default TIntHashSet getSpamDomains() {
        return new TIntHashSet();
    }
}