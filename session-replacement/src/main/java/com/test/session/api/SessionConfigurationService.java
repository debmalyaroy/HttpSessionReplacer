package com.test.session.api;

import com.test.session.models.SessionConstants;

public interface SessionConfigurationService extends SessionConstants {
    boolean isDisableSessionManagement();

    int getMaxInactiveInterval();

    boolean isSticky();

    String getNamespace();

    boolean isTimestampSufix();

    String getNode();

    String getSessionIdName();

    int getSessionIdLength();

    boolean isDelegateWriter();

    String getRepositoryFactory();

    String getSessionTracking();

    boolean isReplicationTrigger();

    String getSessionIdProvider();

    boolean isUsingEncryption();

    String getEncryptionKey();

    String getCookieContextPath();

    boolean isSecureCookie();

    boolean isHttpOnly();
}
