package com.test.session.api;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

import com.test.session.models.SessionData;

/**
 * Implementation of session storage. If repository stores session remotely, the
 * values stored in session should be {@link Serializable} or convertible into
 * binary representation using a {@link SerializerDeserializerService}
 * implementation.
 */
public interface SessionRepository {

    /**
     * Retrieves global session data from repository.
     *
     * @param id
     *            the session id
     * @return retrieves global session data from repository or
     *         <code>null</code> if data wasn't found in repository.
     */
    SessionData getSessionData(String id);

    /**
     * Stores global session data into repository
     *
     * @param sessionData
     *            the session information
     */
    void storeSessionData(SessionData sessionData);

    /**
     * Get all attribute keys stored in the session.
     *
     * @param sessionData
     *            the session information
     * @return all attribute keys stored in the session
     */
    Set<String> getAllKeys(SessionData sessionData);

    /**
     * Returns value of the attribute or <code>null</code> if attribute or
     * session have not been found
     *
     * @param sessionData
     *            the session information
     * @param attribute
     *            the key of the attribute to retrieve
     * @return value of the attribute or <code>null</code> if attribute or
     *         session have not been found
     */
    Object getSessionAttribute(SessionData sessionData, String attribute);

    /**
     * Removes session from the repository.
     *
     * @param sessionData
     *            the session information
     */
    void remove(SessionData sessionData);

    /**
     * Prepares session repository for removal of session. May perform lock on
     * the session. Returns <code>true</code> if caller can proceed with session
     * removal.
     *
     * @param session
     *            the session information
     * @return <code>true</code> if caller can proceed with session removal
     */
    boolean prepareRemove(SessionData session);

    /**
     * Called to clean up resources after the request has been completed and the
     * session will no longer be used in the current thread. Can be used to
     * clean-up thread local store information, close connections etc.
     */
    void requestFinished();

    /**
     * Sets the value of the attribute for the session in the repository.
     *
     * @param sessionData
     *            the session information
     * @param name
     *            attribute name
     * @param value
     *            attribute value. If session is stored remotely, attribute
     *            should be {@link Serializable}.
     */
    void setSessionAttribute(SessionData sessionData, String name, Object value);

    /**
     * The attribute for the session will be removed from the repository.
     *
     * @param sessionData
     *            the session information
     * @param name
     *            attribute name
     */
    void removeSessionAttribute(SessionData sessionData, String name);

    /**
     * Starts transaction on remote session. Transactions are used during
     * session commit. This mechanism allows execution of all operations related
     * to commit in atomic manner, if such mechanism exists in underlying
     * repository. See {@link CommitTransaction} for more information.
     *
     * @param sessionData
     *            the session information
     * @return transaction linked to session.
     */
    CommitTransaction startCommit(SessionData sessionData);

    /**
     * Returns session ids stored in repository and owned by current application
     * node. May throw {@link UnsupportedOperationException} if repository
     * doesn't offer this functionality.
     *
     * @return a collection of session ids
     */
    Collection<String> getOwnedSessionIds();

    /**
     * Called by session manager to notify repository that session id has
     * changed. It is up to repository to implement needed logic. It can rename
     * keys immediately, or it can have some other strategy.
     *
     * @param sessionData
     *            the session information
     */
    void sessionIdChange(SessionData sessionData);

    /**
     * The commit transaction on the session. All operations invoked on instance
     * will be executed when the {@link #commit()} method is called. Atomicity
     * of the transaction depends on underlying system.
     */
    static interface CommitTransaction {

        /**
         * The passed attribute will be added (or changed) to the repository.
         *
         * @param key attribute key
         * @param value attribute value. If session is stored remotely, attribute should be {@link Serializable}.
         *        value == null is equivalent of removing the attribute
         *        value != null is equivalent to add/edit
         */
        void changeAttribute(String key, Object value);

        /**
         * Executes all {@link #addAttribute(String, Object)} and
         * {@link #removeAttribute(String)} requests and performs all necessary
         * housekeeping when committing the session to the repository.
         * <p>
         * If atomic operations are not supported by repository, the
         * implementation should simply execute operations from transaction.
         */
        void commit();
    }
}
