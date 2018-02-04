package com.test.session.api;

import com.test.session.models.SessionData;
import com.test.session.repository.RedisSessionRepository;

/**
 * This interface allows creation of different expiration strategies for
 * sessions stored in Redis repository. Interfaces exposes several call back
 * methods that will be used by {@link RedisSessionRepository}.
 */
public interface RedisExpirationStrategy {

    /**
     * Called when session has been deleted. Implementation should perform any
     * cleanup related to expiration management.
     *
     * @param session
     */
    void sessionDeleted(SessionData session);

    /**
     * Called when session is has been touched (retrieved to be used or
     * committed). Implementation should store or update expiration management
     * data.
     *
     * @param sessionData
     */
    void sessionTouched(SessionData sessionData);

    /**
     * Called to notify expiry mechanism that session id has changed.
     *
     * @param sessionData
     */
    void sessionIdChange(SessionData sessionData);
}