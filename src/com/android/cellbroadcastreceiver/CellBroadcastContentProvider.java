/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cellbroadcastreceiver;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.telephony.CellBroadcastMessage;
import android.text.TextUtils;
import android.util.Log;

/**
 * ContentProvider for the database of received cell broadcasts.
 */
public class CellBroadcastContentProvider extends ContentProvider {
    private static final String TAG = "CellBroadcastContentProvider";

    /** URI matcher for ContentProvider queries. */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /** Authority string for content URIs. */
    static final String CB_AUTHORITY = "cellbroadcasts";

    /** Content URI for notifying observers. */
    static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts/");

    /** URI matcher type to get all cell broadcasts. */
    private static final int CB_ALL = 0;

    /** URI matcher type to get a cell broadcast by ID. */
    private static final int CB_ALL_ID = 1;

    /** MIME type for the list of all cell broadcasts. */
    private static final String CB_LIST_TYPE = "vnd.android.cursor.dir/cellbroadcast";

    /** MIME type for an individual cell broadcast. */
    private static final String CB_TYPE = "vnd.android.cursor.item/cellbroadcast";

    /** system property to enable/disable broadcast duplicate detecion.  */
    private static final String CB_DUP_DETECTION = "persist.cb.dup_detection";

    /** Check for system property to enable/disable duplicate detection.  */
    private boolean mUseDupDetection = SystemProperties.getBoolean(CB_DUP_DETECTION, true);

    static {
        sUriMatcher.addURI(CB_AUTHORITY, null, CB_ALL);
        sUriMatcher.addURI(CB_AUTHORITY, "#", CB_ALL_ID);
    }

    /** The database for this content provider. */
    private SQLiteOpenHelper mOpenHelper;

    /**
     * Initialize content provider.
     * @return true if the provider was successfully loaded, false otherwise
     */
    @Override
    public boolean onCreate() {
        mOpenHelper = new CellBroadcastDatabaseHelper(getContext());
        return true;
    }

    /**
     * Return a cursor for the cell broadcast table.
     * @param uri the URI to query.
     * @param projection the list of columns to put into the cursor, or null.
     * @param selection the selection criteria to apply when filtering rows, or null.
     * @param selectionArgs values to replace ?s in selection string.
     * @param sortOrder how the rows in the cursor should be sorted, or null to sort from most
     *  recently received to least recently received.
     * @return a Cursor or null.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(CellBroadcastDatabaseHelper.TABLE_NAME);

        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_ALL:
                // get all broadcasts
                break;

            case CB_ALL_ID:
                // get broadcast by ID
                qb.appendWhere("(_id=" + uri.getPathSegments().get(0) + ')');
                break;

            default:
                Log.e(TAG, "Invalid query: " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        String orderBy;
        if (!TextUtils.isEmpty(sortOrder)) {
            orderBy = sortOrder;
        } else {
            orderBy = Telephony.CellBroadcasts.DEFAULT_SORT_ORDER;
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
        }
        return c;
    }

    /**
     * Return the MIME type of the data at the specified URI.
     * @param uri the URI to query.
     * @return a MIME type string, or null if there is no type.
     */
    @Override
    public String getType(Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_ALL:
                return CB_LIST_TYPE;

            case CB_ALL_ID:
                return CB_TYPE;

            default:
                return null;
        }
    }

    /**
     * Insert a new row. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the content:// URI of the insertion request.
     * @param values a set of column_name/value pairs to add to the database.
     * @return the URI for the newly inserted item.
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert not supported");
    }

    /**
     * Delete one or more rows. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the full URI to query, including a row ID (if a specific record is requested).
     * @param selection an optional restriction to apply to rows when deleting.
     * @return the number of rows affected.
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete not supported");
    }

    /**
     * Update one or more rows. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the URI to query, potentially including the row ID.
     * @param values a Bundle mapping from column names to new column values.
     * @param selection an optional filter to match rows to update.
     * @return the number of rows affected.
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update not supported");
    }

    private static final String QUERY_BY_SERIAL = Telephony.CellBroadcasts.SERIAL_NUMBER + "=?";

    private static final String QUERY_BY_SERIAL_PLMN = QUERY_BY_SERIAL + " AND "
            + Telephony.CellBroadcasts.PLMN + "=?";

    private static final String QUERY_BY_SERIAL_PLMN_LAC = QUERY_BY_SERIAL_PLMN + " AND "
            + Telephony.CellBroadcasts.LAC + "=?";

    private static final String QUERY_BY_SERIAL_PLMN_LAC_CID = QUERY_BY_SERIAL_PLMN_LAC + " AND "
            + Telephony.CellBroadcasts.CID + "=?";

    private static final String[] SELECT_ID_COLUMN = {Telephony.CellBroadcasts._ID};

    /**
     * Internal method to insert a new Cell Broadcast into the database and notify observers.
     * If duplicate detection is disabled by the system property {@link #mUseDupDetection}
     * message will be treated as a new Cell Broadcast.
     * @param message the message to insert
     * @return true if the broadcast is new, false if it's a duplicate broadcast.
     */
    boolean insertNewBroadcast(CellBroadcastMessage message) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        ContentValues cv = message.getContentValues();

        if (mUseDupDetection) {
            // Check for existing alert with same serial number and geo scope
            String serial = cv.getAsString(Telephony.CellBroadcasts.SERIAL_NUMBER);
            String plmn = cv.getAsString(Telephony.CellBroadcasts.PLMN);
            String lac = cv.getAsString(Telephony.CellBroadcasts.LAC);
            String cid = cv.getAsString(Telephony.CellBroadcasts.CID);
            String selection;
            String[] selectionArgs;

            if (plmn != null) {
                if (lac != null) {
                    if (cid != null) {
                        selection = QUERY_BY_SERIAL_PLMN_LAC_CID;
                        selectionArgs = new String[] {serial, plmn, lac, cid};
                    } else {
                        selection = QUERY_BY_SERIAL_PLMN_LAC;
                        selectionArgs = new String[] {serial, plmn, lac};
                    }
                } else {
                    selection = QUERY_BY_SERIAL_PLMN;
                    selectionArgs = new String[] {serial, plmn};
                }
            } else {
                selection = QUERY_BY_SERIAL;
                selectionArgs = new String[] {serial};
            }

            Cursor c = db.query(CellBroadcastDatabaseHelper.TABLE_NAME, SELECT_ID_COLUMN,
                    selection, selectionArgs, null, null, null);

            if (c.getCount() != 0) {
                Log.d(TAG, "ignoring dup broadcast serial=" + serial + " found " + c.getCount());
                return false;
            }
        }

        long rowId = db.insert(CellBroadcastDatabaseHelper.TABLE_NAME, null, cv);
        if (rowId == -1) {
            Log.e(TAG, "failed to insert new broadcast into database");
            // Return true on DB write failure because we still want to notify the user.
            // The CellBroadcastMessage will be passed with the intent, so the message will be
            // displayed in the emergency alert dialog, or the dialog that is displayed when
            // the user selects the notification for a non-emergency broadcast, even if the
            // broadcast could not be written to the database.
        }
        return true;    // broadcast is not a duplicate
    }

    /**
     * Internal method to delete a cell broadcast by row ID and notify observers.
     * @param rowId the row ID of the broadcast to delete
     * @param decrementUnreadCount true to decrement the count of unread alerts
     * @return true if the database was updated, false otherwise
     */
    boolean deleteBroadcast(long rowId, boolean decrementUnreadCount) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int rowCount = db.delete(CellBroadcastDatabaseHelper.TABLE_NAME,
                Telephony.CellBroadcasts._ID + "=?",
                new String[]{Long.toString(rowId)});
        if (rowCount != 0) {
            if (decrementUnreadCount) {
                CellBroadcastReceiverApp.decrementUnreadAlertCount();
            }
            return true;
        } else {
            Log.e(TAG, "failed to delete broadcast at row " + rowId);
            return false;
        }
    }

    /**
     * Internal method to delete all cell broadcasts and notify observers.
     * @return true if the database was updated, false otherwise
     */
    boolean deleteAllBroadcasts() {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int rowCount = db.delete(CellBroadcastDatabaseHelper.TABLE_NAME, null, null);
        if (rowCount != 0) {
            CellBroadcastReceiverApp.resetUnreadAlertCount();
            return true;
        } else {
            Log.e(TAG, "failed to delete all broadcasts");
            return false;
        }
    }

    /**
     * Internal method to mark a broadcast as read and notify observers. The broadcast can be
     * identified by delivery time (for new alerts) or by row ID. The caller is responsible for
     * decrementing the unread non-emergency alert count, if necessary.
     *
     * @param columnName the column name to query (ID or delivery time)
     * @param columnValue the ID or delivery time of the broadcast to mark read
     * @return true if the database was updated, false otherwise
     */
    boolean markBroadcastRead(String columnName, long columnValue) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        ContentValues cv = new ContentValues(1);
        cv.put(Telephony.CellBroadcasts.MESSAGE_READ, 1);

        String whereClause = columnName + "=?";
        String[] whereArgs = new String[]{Long.toString(columnValue)};

        int rowCount = db.update(CellBroadcastDatabaseHelper.TABLE_NAME, cv, whereClause, whereArgs);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to mark broadcast read: " + columnName + " = " + columnValue);
            return false;
        }
    }

    /** Callback for users of AsyncCellBroadcastOperation. */
    interface CellBroadcastOperation {
        /**
         * Perform an operation using the specified provider.
         * @param provider the CellBroadcastContentProvider to use
         * @return true if any rows were changed, false otherwise
         */
        boolean execute(CellBroadcastContentProvider provider);
    }

    /**
     * Async task to call this content provider's internal methods on a background thread.
     * The caller supplies the CellBroadcastOperation object to call for this provider.
     */
    static class AsyncCellBroadcastTask extends AsyncTask<CellBroadcastOperation, Void, Void> {
        /** Reference to this app's content resolver. */
        private ContentResolver mContentResolver;

        AsyncCellBroadcastTask(ContentResolver contentResolver) {
            mContentResolver = contentResolver;
        }

        /**
         * Perform a generic operation on the CellBroadcastContentProvider.
         * @param params the CellBroadcastOperation object to call for this provider
         * @return void
         */
        @Override
        protected Void doInBackground(CellBroadcastOperation... params) {
            ContentProviderClient cpc = mContentResolver.acquireContentProviderClient(
                    CellBroadcastContentProvider.CB_AUTHORITY);
            CellBroadcastContentProvider provider = (CellBroadcastContentProvider)
                    cpc.getLocalContentProvider();

            if (provider != null) {
                try {
                    boolean changed = params[0].execute(provider);
                    if (changed) {
                        Log.d(TAG, "database changed: notifying observers...");
                        mContentResolver.notifyChange(CONTENT_URI, null, false);
                    }
                } finally {
                    cpc.release();
                }
            } else {
                Log.e(TAG, "getLocalContentProvider() returned null");
            }

            mContentResolver = null;    // free reference to content resolver
            return null;
        }
    }
}
