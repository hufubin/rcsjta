/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
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
 ******************************************************************************/

package com.orangelabs.rcs.ri.sharing.image;

import com.gsma.services.rcs.RcsGenericException;
import com.gsma.services.rcs.RcsService.Direction;
import com.gsma.services.rcs.RcsServiceException;
import com.gsma.services.rcs.RcsServiceNotAvailableException;
import com.gsma.services.rcs.contact.ContactId;
import com.gsma.services.rcs.sharing.image.ImageSharing.ReasonCode;
import com.gsma.services.rcs.sharing.image.ImageSharing.State;
import com.gsma.services.rcs.sharing.image.ImageSharingListener;
import com.gsma.services.rcs.sharing.image.ImageSharingLog;
import com.gsma.services.rcs.sharing.image.ImageSharingService;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.orangelabs.rcs.api.connection.ConnectionManager.RcsServiceName;
import com.orangelabs.rcs.api.connection.utils.ExceptionUtil;
import com.orangelabs.rcs.api.connection.utils.RcsFragmentActivity;
import com.orangelabs.rcs.ri.R;
import com.orangelabs.rcs.ri.RiApplication;
import com.orangelabs.rcs.ri.utils.LogUtils;
import com.orangelabs.rcs.ri.utils.RcsContactUtil;
import com.orangelabs.rcs.ri.utils.Utils;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

/**
 * List image sharings from the content provider
 * 
 * @author Jean-Marc AUFFRET
 * @author Philippe LEMORDANT
 */
public class ImageSharingList extends RcsFragmentActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    // @formatter:off
    private static final String[] PROJECTION = new String[] {
        ImageSharingLog.BASECOLUMN_ID,
        ImageSharingLog.SHARING_ID,
        ImageSharingLog.CONTACT,
        ImageSharingLog.TRANSFERRED,
        ImageSharingLog.FILE,
        ImageSharingLog.FILENAME,
        ImageSharingLog.FILESIZE,
        ImageSharingLog.STATE,
        ImageSharingLog.REASON_CODE,
        ImageSharingLog.DIRECTION,
        ImageSharingLog.TIMESTAMP
    };
    // @formatter:on

    private static final String SORT_ORDER = ImageSharingLog.TIMESTAMP + " DESC";

    private ListView mListView;

    private ImageSharingService mImageSharingService;

    private ImageSharingListAdapter mAdapter;

    private boolean mImageSharingListenerSet = false;

    private Handler mHandler = new Handler();

    private static final String LOGTAG = LogUtils.getTag(ImageSharingList.class.getSimpleName());

    /**
     * The loader's unique ID. Loader IDs are specific to the Activity in which they reside.
     */
    private static final int LOADER_ID = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Set layout */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.image_sharing_list);

        mImageSharingService = getImageSharingApi();

        mListView = (ListView) findViewById(android.R.id.list);
        TextView emptyView = (TextView) findViewById(android.R.id.empty);
        mListView.setEmptyView(emptyView);
        registerForContextMenu(mListView);

        mAdapter = new ImageSharingListAdapter(this);
        mListView.setAdapter(mAdapter);
        /*
         * Initialize the Loader with id '1' and callbacks 'mCallbacks'.
         */
        getSupportLoaderManager().initLoader(LOADER_ID, null, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mImageSharingService == null || !mImageSharingListenerSet) {
            return;
        }
        try {
            mImageSharingService.removeEventListener(mImageSharingListener);
        } catch (RcsServiceException e) {
            Log.w(LOGTAG, ExceptionUtil.getFullStackTrace(e));
        }
    }

    /**
     * List adapter
     */
    private class ImageSharingListAdapter extends CursorAdapter {

        private LayoutInflater mInflater;

        /**
         * Constructor
         * 
         * @param context Context
         */
        public ImageSharingListAdapter(Context context) {
            super(context, null, 0);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final View view = mInflater.inflate(R.layout.image_sharing_list_item, parent, false);
            view.setTag(new ImageSharingItemCache(view, cursor));
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final ImageSharingItemCache holder = (ImageSharingItemCache) view.getTag();
            String number = cursor.getString(holder.columnNumber);

            String displayName = RcsContactUtil.getInstance(context).getDisplayName(number);
            holder.numberText.setText(getString(R.string.label_contact, displayName));

            String filename = cursor.getString(holder.columnFilename);
            holder.filenameText.setText(getString(R.string.label_filename, filename));

            Long filesize = cursor.getLong(holder.columnFilesize);
            holder.filesizeText.setText(getString(R.string.label_filesize, filesize));

            State state = State.valueOf(cursor.getInt(holder.columnState));
            holder.stateText.setText(getString(R.string.label_session_state,
                    RiApplication.sImageSharingStates[state.toInt()]));

            ReasonCode reason = ReasonCode.valueOf(cursor.getInt(holder.columnReason));
            if (ReasonCode.UNSPECIFIED == reason) {
                holder.reasonText.setVisibility(View.GONE);
            } else {
                holder.reasonText.setVisibility(View.VISIBLE);
                holder.reasonText.setText(getString(R.string.label_session_reason,
                        RiApplication.sImageSharingReasonCodes[reason.toInt()]));
            }

            Direction direction = Direction.valueOf(cursor.getInt(holder.columnDirection));
            holder.directionText.setText(getString(R.string.label_direction,
                    RiApplication.getDirection(direction)));

            Long timestamp = cursor.getLong(holder.columnTimestamp);
            holder.timestamptext.setText(getString(R.string.label_session_date,
                    decodeDate(timestamp)));
        }
    }

    /**
     * Image sharing item in cache
     */
    private class ImageSharingItemCache {
        int columnFilename;

        int columnFilesize;

        int columnDirection;

        int columnState;

        int columnReason;

        int columnTimestamp;

        int columnNumber;

        TextView numberText;

        TextView filenameText;

        TextView filesizeText;

        TextView stateText;

        TextView reasonText;

        TextView directionText;

        TextView timestamptext;

        public ImageSharingItemCache(View view, Cursor cursor) {
            columnNumber = cursor.getColumnIndexOrThrow(ImageSharingLog.CONTACT);
            columnFilename = cursor.getColumnIndexOrThrow(ImageSharingLog.FILENAME);
            columnFilesize = cursor.getColumnIndexOrThrow(ImageSharingLog.FILESIZE);
            columnState = cursor.getColumnIndexOrThrow(ImageSharingLog.STATE);
            columnReason = cursor.getColumnIndexOrThrow(ImageSharingLog.REASON_CODE);
            columnDirection = cursor.getColumnIndexOrThrow(ImageSharingLog.DIRECTION);
            columnTimestamp = cursor.getColumnIndexOrThrow(ImageSharingLog.TIMESTAMP);
            numberText = (TextView) view.findViewById(R.id.number);
            filenameText = (TextView) view.findViewById(R.id.filename);
            filesizeText = (TextView) view.findViewById(R.id.filesize);
            stateText = (TextView) view.findViewById(R.id.state);
            reasonText = (TextView) view.findViewById(R.id.reason);
            directionText = (TextView) view.findViewById(R.id.direction);
            timestamptext = (TextView) view.findViewById(R.id.date);
        }
    }

    private String decodeDate(long date) {
        return DateFormat.getInstance().format(new Date(date));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = new MenuInflater(getApplicationContext());
        inflater.inflate(R.menu.menu_log, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_clear_log:
                /* Delete all image sharings */
                if (!isServiceConnected(RcsServiceName.IMAGE_SHARING)) {
                    showMessage(R.string.label_service_not_available);
                    break;
                }
                if (LogUtils.isActive) {
                    Log.d(LOGTAG, "delete all image sharing sessions");
                }
                try {
                    if (!mImageSharingListenerSet) {
                        mImageSharingService.addEventListener(mImageSharingListener);
                        mImageSharingListenerSet = true;
                    }
                    mImageSharingService.deleteImageSharings();
                } catch (RcsServiceException e) {
                    showExceptionThenExit(e);
                }
                break;
        }
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_log_ish_item, menu);
        menu.findItem(R.id.menu_sharing_display).setVisible(false);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        Cursor cursor = (Cursor) mAdapter.getItem(info.position);
        /* Check if message can be played */
        Direction dir = Direction.valueOf(cursor.getInt(cursor
                .getColumnIndexOrThrow(ImageSharingLog.DIRECTION)));
        if (Direction.INCOMING == dir) {
            Long transferred = cursor.getLong(cursor.getColumnIndexOrThrow(ImageSharingLog.TRANSFERRED));
            Long size = cursor.getLong(cursor.getColumnIndexOrThrow(ImageSharingLog.FILESIZE));
            if (size.equals(transferred)) {
                // Incoming file transfer must be complete to be playable
                menu.findItem(R.id.menu_sharing_display).setVisible(true);
            }
        } else {
            menu.findItem(R.id.menu_sharing_display).setVisible(true);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        /* Get selected item */
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        Cursor cursor = (Cursor) (mListView.getAdapter()).getItem(info.position);
        String sharingId = cursor.getString(cursor
                .getColumnIndexOrThrow(ImageSharingLog.SHARING_ID));
        if (LogUtils.isActive) {
            Log.d(LOGTAG, "onContextItemSelected sharing ID=".concat(sharingId));
        }
        try {
            switch (item.getItemId()) {
                case R.id.menu_sharing_delete:
                    /* Delete messages for contact */
                    if (LogUtils.isActive) {
                        Log.d(LOGTAG, "Delete image sharing ID=".concat(sharingId));
                    }
                    if (!mImageSharingListenerSet) {
                        mImageSharingService.addEventListener(mImageSharingListener);
                        mImageSharingListenerSet = true;
                    }
                    mImageSharingService.deleteImageSharing(sharingId);
                    return true;

                case R.id.menu_sharing_display:
                    String file = cursor.getString(cursor
                            .getColumnIndexOrThrow(ImageSharingLog.FILE));
                    Utils.showPicture(this, Uri.parse(file));
                    return true;

                default:
                    return super.onContextItemSelected(item);
            }
        } catch (RcsServiceNotAvailableException e) {
            showMessage(R.string.label_service_not_available);
            return true;

        } catch (RcsGenericException e) {
            showExceptionThenExit(e);
            return true;
        }
    }

    private ImageSharingListener mImageSharingListener = new ImageSharingListener() {

        @Override
        public void onStateChanged(ContactId contact, String sharingId, State state,
                ReasonCode reasonCode) {
        }

        @Override
        public void onProgressUpdate(ContactId contact, String sharingId, long currentSize,
                long totalSize) {
        }

        @Override
        public void onDeleted(ContactId contact, Set<String> sharingIds) {
            if (LogUtils.isActive) {
                Log.d(LOGTAG,
                        "onDeleted contact=" + contact + " for sharing IDs="
                                + Arrays.toString(sharingIds.toArray()));
            }
            mHandler.post(new Runnable() {
                public void run() {
                    Utils.displayLongToast(ImageSharingList.this,
                            getString(R.string.label_delete_sharing_success));
                }
            });
        }
    };

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        /* Create a new CursorLoader with the following query parameters. */
        return new CursorLoader(this, ImageSharingLog.CONTENT_URI, PROJECTION, null, null,
                SORT_ORDER);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        /* A switch-case is useful when dealing with multiple Loaders/IDs */
        switch (loader.getId()) {
            case LOADER_ID:
                /*
                 * The asynchronous load is complete and the data is now available for use. Only now
                 * can we associate the queried Cursor with the CursorAdapter.
                 */
                mAdapter.swapCursor(cursor);
                break;
        }
        /* The listview now displays the queried data. */
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        /*
         * For whatever reason, the Loader's data is now unavailable. Remove any references to the
         * old data by replacing it with a null Cursor.
         */
        mAdapter.swapCursor(null);
    }
}
