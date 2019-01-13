/*
 * Copyright (©) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.DefaultClientConfig;
import com.microsoft.graph.logger.ILogger;
import com.microsoft.graph.logger.LoggerLevel;

/**
 * OneDrive API client configuration extension
 */
public final class ClientConfig extends DefaultClientConfig
{
    private final IAuthenticationProvider itsAuthProvider;
    private final ILogger itsLogger;

    /**
     * Constructor
     */
    public ClientConfig(IAuthenticationProvider authProvider,
                        LoggerLevel logLevel)
    {
        itsAuthProvider = authProvider;
        itsLogger = new ClientLogger();
        itsLogger.setLoggingLevel(logLevel);
        super.getLogger();
    }

    @Override
    public IAuthenticationProvider getAuthenticationProvider()
    {
        return itsAuthProvider;
    }

    @Override
    public ILogger getLogger()
    {
        return itsLogger;
    }
}
