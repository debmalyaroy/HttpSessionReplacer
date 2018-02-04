package com.test.session.servlet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SessionRepository;
import com.test.session.api.SessionRepository.CommitTransaction;
import com.test.session.configuration.SessionConfiguration;
import com.test.session.models.SessionData;

/**
 * Session that can be stored in repository. The class provides services for
 * retrieving, removing and adding attributes.
 */
class RepositoryBackedSession {
    protected static final Logger LOGGER = LoggerFactory.getLogger(RepositoryBackedSession.class);

    // SessionData describing this session
    protected final SessionData sessionData;

    // Task responsible for committing the session
    private final Runnable committer = getCommitterThread();

    // Counter of number of concurrent requests accessing this session
    private final AtomicInteger concurrentUses;

    // Set to true if this session concurrent counter has been increased
    private final AtomicBoolean lockedForUse = new AtomicBoolean();

    // True if session is replicated on non primitive get
    private final boolean replicateOnGet;
    private final String configuredRepositoryFactory;
    private final SessionRepository repository;

    // True if session is no longer valid
    private boolean invalid;

    // True if session should be invalidated at commit
    private boolean invalidateOnCommit;
    private boolean committed;
    protected boolean dirty;
    private boolean removeFromCache;

    protected RepositoryBackedSession(SessionData sessionData, boolean replicateOnGet, String configuredRepositoryFactory) {
        this.sessionData = sessionData;
        concurrentUses = new AtomicInteger();
        this.replicateOnGet = replicateOnGet; // setAndNonPrimitiveGet = true, Set = false
        this.configuredRepositoryFactory = configuredRepositoryFactory;
        this.repository = getRepository();
    }

    protected RepositoryBackedSession(RepositoryBackedSession linked) {
        sessionData = linked.sessionData;
        concurrentUses = linked.concurrentUses;
        replicateOnGet = linked.replicateOnGet;
        configuredRepositoryFactory = linked.configuredRepositoryFactory;
        this.repository = getRepository();
    }

    public boolean isValid() {
        return !invalid;
    }

    public boolean isExpired() {
        int maxInactiveInterval = sessionData.getMaxInactiveInterval();

        if (maxInactiveInterval <= 0) {
            return false;
        }

        return (sessionData.getLastAccessedTime() + TimeUnit.SECONDS.toMillis(maxInactiveInterval)) < System.currentTimeMillis();
    }

    public boolean isDirty() {
        return dirty;
    }

    public SessionData getSessionData() {
        return sessionData;
    }

    public void sessionIdChange() {
        repository.sessionIdChange(sessionData);
    }

    public SessionData getSessionDataById() {
        String sessionId = sessionData.getId();
        return StringUtils.isBlank(sessionId) ? null : repository.getSessionData(sessionId);
    }

    public void requestFinished() {
        repository.requestFinished();
    }

    public void remove() {
        repository.remove(sessionData);
    }

    public void storeSessionData() {
        repository.storeSessionData(sessionData);
    }

    public synchronized void commit() {
        if (!invalid) {
            committer.run();
        }
    }

    public boolean checkUsedAndLock() {
        boolean used = !committed || dirty;

        if (used && lockedForUse.compareAndSet(false, true)) {
            concurrentUses.incrementAndGet();
        }

        return used;
    }

    public boolean canRemoveFromCache() {
        return removeFromCache;
    }

    public void doInvalidate(boolean expired) {
        boolean canRemove = false;

        try {
            if (!invalid) {
                canRemove = invalidateOrNotify(expired);
            }
        } finally {
            if (!invalidateOnCommit) {
                finishInvalidation(canRemove);
            }
        }
    }

    protected boolean replicateOnGet(Object obj) {
        return replicateOnGet && !isImmutableType(obj);
    }

    protected Set<String> getAllRepositoryKeys() {
        Set<String> set = sessionData.getRepositoryKeys();

        if (set == null) {
            set = repository.getAllKeys(sessionData);
            sessionData.setRepositoryKeys(set);
        }

        return set;
    }

    protected Attribute retrieveAttribute(String key, Attribute attribute) {
        // If the attribute is non-cachable or null and possibly in remote
        // repository, then we need to retrieve it
        Attribute attr = attribute;

        if (attr != null && !sessionData.isNonCacheable(key)) {
            return attr;
        } else if (attr == null && !sessionData.isMaybeInRepository(key)) {
            return null;
        }

        Object value = repository.getSessionAttribute(sessionData, key);

        if (attr == null) {
            attr = new Attribute(value);
            attrs.put(key, attr);
        } else {
            attr.value = value;
        }

        return attr;
    }

    protected void assertValid() {
        if (invalid) {
            throw new IllegalStateException("Session with id " + sessionData.getId() + " is invalid. Operation is not allowed. For information session data is " + sessionData);
        }
    }

    protected void removeAttributeFromRepository(String name) {
        if (sessionData.isNonCacheable(name)) {
            repository.removeSessionAttribute(sessionData, name);
        }
    }

    protected void setSessionAttribute(String key, Object value) {
        repository.setSessionAttribute(sessionData, key, value);
    }

    private boolean invalidateOrNotify(boolean expired) {
        boolean canRemove = repository.prepareRemove(getSessionData());

        if (canRemove) {
            if (expired && (concurrentUses.get() > 0)) {
                invalidateOnCommit = true;
            } else {
                invalidateOnCommit = false;
                wipeInvalidSession();
            }
        } else {
            if (expired) {
                LOGGER.warn("Conflict on removing session: {}", sessionData.getId());
            } else {
                LOGGER.info("Conflict on removing session during exipre management: {}", sessionData.getId());
            }
        }

        return canRemove;
    }

    private void wipeInvalidSession() {
        loadAllAttributes();
        attrs.clear();
    }

    private void finishInvalidation(boolean canRemove) {
        invalid = true;
        if (canRemove) {
            repository.remove(sessionData);
        }
    }

    private void loadAllAttributes() {
        getAllRepositoryKeys().forEach(key -> {
            if (attrs.get(key) == null) {
                retrieveAttribute(key, null);
            }
        });
    }

    private void setCommitted() {
        synchronized (this) {
            // No other thread is using this session
            if (concurrentUses.get() <= 0) {
                removeFromCache = true;
            }

            committed = true;
            dirty = false;
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private SessionRepository getRepository() {
        String filterName = "(service.pid=" + configuredRepositoryFactory + ")";
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();

        try {
            ServiceReference[] allServiceReferences = bundleContext.getServiceReferences(SessionRepository.class.getName(), filterName);

            if (ArrayUtils.isNotEmpty(allServiceReferences)) {
                return (SessionRepository) bundleContext.getService(allServiceReferences[0]);
            }
        } catch (InvalidSyntaxException e) {
            LOGGER.debug("Error while getting session repository: {}", e);
        }

        return null;
    }

    /**
    * This thread implements logic that commits session to
    * {@link SessionRepository}. The logic allows atomic commit if repository
    * supports it. The algorithm is as follows:
    * <p>
    * Call {@link SessionRepository#startCommit(SessionData)} to initiate
    * commit transaction. Verify if session is in used and is locked. Only such
    * sessions need to be committed. Unlock the session. If the session is not
    * last concurrent session request, then we don't need to reset internal
    * attribute flags indicating that they become same as the ones in
    * repository. If the session is the last concurrent use (i.e. no other
    * requests access session concurrently), or if
    * {@link SessionConfiguration#COMMIT_ON_ALL_CONCURRENT} was set to
    * <code>true</code>, the changed and deleted session attributes are indeed
    * updated in repository. Add changed and/or deleted attributes, notify
    * listeners that the session is being stored in repository.
    * </p>
    */
    private Runnable getCommitterThread() {
        return () -> {
            if (checkUsedAndLock()) {
                // Unlock the session and reduce the counter
                boolean lastSession = lockedForUse.compareAndSet(true, false) && concurrentUses.decrementAndGet() == 0;

                if (lastSession && invalidateOnCommit) {
                    invalidationOnCommit();
                } else {
                    storeToRepository((lastSession || forceCommit), !lastSession);
                }

                setCommitted();
                LOGGER.debug("Committed session: {}", sessionData);
            } else {
                LOGGER.debug("Nothing to commit for session: {}", sessionData);
            }  
        };
    }

    private void storeToRepository(boolean commitAttributes, boolean keepChangedFlag) {
        CommitTransaction transaction = repository.startCommit(sessionData);
        LOGGER.debug("Committing session: {}", sessionData);

        if (commitAttributes) {
            attrs.entrySet().stream()
                .filter(entry -> !sessionData.isNonCacheable(entry.getKey()))
                .filter(entry -> entry.getValue().changed)
                .forEach(entry -> {
                    Attribute attr = entry.getValue();
                    attr.changed = keepChangedFlag;

                    transaction.changeAttribute(entry.getKey(), (attr.deleted ? null : attr.value));
                });
        }

        transaction.commit();
    }

    private void invalidationOnCommit() {
        try {
            wipeInvalidSession();
        } finally {
            finishInvalidation(true);
        }
    }

    private static boolean isImmutableType(Object obj) {
        return obj instanceof Number || obj instanceof Character || obj instanceof String || obj instanceof Boolean || obj instanceof Enum;
    }
}
