/*
 * Copyright (©) 2012 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.file;

import android.os.Parcel;

import com.jefftharris.passwdsafe.file.PasswdExpiryFilter;
import com.jefftharris.passwdsafe.file.PasswdRecordFilter;
import com.jefftharris.passwdsafe.lib.Utils;

import org.junit.Test;

import java.util.Date;
import java.util.regex.Pattern;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * Tests for the PasswdRecordFilter class
 */
@SuppressWarnings("unused")
public class PasswdRecordFilterTest
{
    /** Test parcelling a query filter */
    @Test
    public void testQueryParcel()
    {
        Pattern pattern = Pattern.compile("foobar", Pattern.CASE_INSENSITIVE);
        PasswdRecordFilter filter =
            new PasswdRecordFilter(pattern, PasswdRecordFilter.OPTS_DEFAULT);
        doParcelTest(filter, "foobar");
    }


    /** Test parcelling an expiration filter */
    @Test
    public void testExpiryParcel()
    {
        PasswdRecordFilter filter =
            new PasswdRecordFilter(PasswdExpiryFilter.TODAY, null,
                                   PasswdRecordFilter.OPTS_NO_ALIAS);
        doParcelTest(filter, "Password expires today");
    }


    /** Test parcelling a custom expiration filter */
    @Test
    public void testExpiryCustomParcel()
    {
        Date now = new Date();
        PasswdRecordFilter filter =
            new PasswdRecordFilter(PasswdExpiryFilter.CUSTOM,
                                   now,
                                   PasswdRecordFilter.OPTS_NO_SHORTCUT);
        doParcelTest(filter, "Password expires before " +
                                 Utils.formatDate(now.getTime(),
                                                  getTargetContext(),
                                                  true, true, false));
    }


    /** Test a parceled filter */
    private void doParcelTest(PasswdRecordFilter filter,
                              String expectedToString)
    {
        PasswdRecordFilter filter2 = recreateFilter(filter);
        assertNotSame(filter, filter2);
        assertEquals(filter, filter2);

        assertEquals(expectedToString, filter.toString(getTargetContext()));
        assertEquals(expectedToString, filter2.toString(getTargetContext()));
    }


    /** Recreate the filter from a parcel */
    private PasswdRecordFilter recreateFilter(PasswdRecordFilter filter)
    {
        Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(filter, 0);
        parcel.setDataPosition(0);
        PasswdRecordFilter newFilter =
                parcel.readParcelable(getClass().getClassLoader());
        parcel.recycle();
        return newFilter;
    }
}
