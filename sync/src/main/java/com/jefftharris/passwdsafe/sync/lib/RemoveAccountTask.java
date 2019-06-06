/*
 * Copyright (©) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.jefftharris.passwdsafe.sync.R;

/**
 * Async task to remove an account
 */
public class RemoveAccountTask extends AccountUpdateTask
{
    /**
     * Constructor
     */
    public RemoveAccountTask(Uri providerUri, Context ctx)
    {
        super(providerUri, ctx.getString(R.string.removing_account));
    }

    @Override
    protected void doAccountUpdate(ContentResolver cr)
    {
        if (itsAccountUri != null) {
            cr.delete(itsAccountUri, null, null);
        }
    }
}
