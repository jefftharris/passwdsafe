/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.box;

import com.box.androidsdk.content.auth.OAuthActivity;
import com.box.androidsdk.content.auth.OAuthWebView;

/**
 * Overridden OAuthActivity for Box
 */
public class BoxOAuthActivity extends OAuthActivity
{
    @Override
    protected OAuthWebView createOAuthView()
    {
        var view = super.createOAuthView();
        var settings = view.getSettings();
        settings.setDomStorageEnabled(true);
        return view;
    }
}
