/*
 * Copyright (©) 2020 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.jefftharris.passwdsafe.db.BackupFile;
import com.jefftharris.passwdsafe.db.BackupFilesDao;
import com.jefftharris.passwdsafe.db.PasswdSafeDb;

import java.util.List;

/**
 * View model for backup files
 */
public class BackupFilesModel extends AndroidViewModel
{
    private final BackupFilesDao itsBackupFilesDao;
    private final LiveData<List<BackupFile>> itsBackupFiles;

    /**
     * Constructor
     */
    public BackupFilesModel(Application app)
    {
        super(app);
        itsBackupFilesDao = PasswdSafeDb.get(app).accessBackupFiles();
        itsBackupFiles = itsBackupFilesDao.loadBackupFiles();
    }

    /**
     * Get the backup files
     */
    public LiveData<List<BackupFile>> getBackupFiles()
    {
        return itsBackupFiles;
    }

    /**
     * Delete all of the backup files
     */
    public void deleteAll()
    {
        itsBackupFilesDao.deleteAll(getApplication());
    }
}
