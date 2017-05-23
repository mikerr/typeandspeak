
package com.googamaphone.typeandspeak;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import android.annotation.TargetApi;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.Audio.Media;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.googamaphone.PinnedDialog;
import com.googamaphone.PinnedDialogManager;

public class LibraryActivity extends ListActivity {
    private static final String[] FROM = new String[] {
            AudioColumns.TITLE, AudioColumns.DATE_ADDED
    };

    private static final int[] TO = new int[] {
            R.id.title, R.id.date
    };

    private static final int PINNED_ACTIONS = 1;
    private static final int PINNED_CONFIRM_DELETE = 2;

    private static final String KEY_POSITION = "position";

    private CursorAdapter mCursorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.library);

        if (Build.VERSION.SDK_INT > 11) {
            new SetupActionBar().run();
        }

        requestCursor();
    }

    private void requestCursor() {
        final LoadMediaFromAlbum loadMediaTask = new LoadMediaFromAlbum(this) {
            @Override
            protected void onPostExecute(Cursor result) {
                mCursorAdapter = new LibraryCursorAdapter(LibraryActivity.this, result,
                        R.layout.library_item, FROM, TO);

                getListView().setAdapter(mCursorAdapter);
            }
        };

        loadMediaTask.execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                final Intent intent = new Intent(this, TypeAndSpeak.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(11)
    class SetupActionBar implements Runnable {
        @Override
        public void run() {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private final PinnedDialogManager mPinnedDialogManager = new PinnedDialogManager() {
        @Override
        protected PinnedDialog onCreatePinnedDialog(int id) {
            switch (id) {
                case PINNED_ACTIONS: {
                    final PinnedDialog dialog = new PinnedDialog(LibraryActivity.this)
                            .setContentView(R.layout.pinned_actions);
                    dialog.findViewById(R.id.delete).setOnClickListener(mOnClickListener);
                    dialog.findViewById(R.id.ringtone).setOnClickListener(mOnClickListener);
                    dialog.findViewById(R.id.share).setOnClickListener(mOnClickListener);
                    return dialog;
                }
                case PINNED_CONFIRM_DELETE: {
                    final PinnedDialog dialog = new PinnedDialog(LibraryActivity.this)
                            .setContentView(R.layout.pinned_confirm_delete);
                    dialog.findViewById(R.id.confirm_delete).setOnClickListener(mOnClickListener);
                    dialog.findViewById(R.id.cancel_delete).setOnClickListener(mOnClickListener);
                    return dialog;
                }
            }

            return super.onCreatePinnedDialog(id);
        }

        @Override
        protected void onPreparePinnedDialog(int id, PinnedDialog dialog, Bundle arguments) {
            switch (id) {
                case PINNED_ACTIONS: {
                    final int position = arguments.getInt(KEY_POSITION);
                    dialog.findViewById(R.id.delete).setTag(R.id.tag_position, position);
                    dialog.findViewById(R.id.ringtone).setTag(R.id.tag_position, position);
                    dialog.findViewById(R.id.share).setTag(R.id.tag_position, position);
                    break;
                }
                case PINNED_CONFIRM_DELETE: {
                    new Exception().printStackTrace();
                    final int position = arguments.getInt(KEY_POSITION);
                    dialog.findViewById(R.id.confirm_delete).setTag(R.id.tag_position, position);
                    dialog.findViewById(R.id.cancel_delete).setTag(R.id.tag_position, position);

                    final Cursor cursor = mCursorAdapter.getCursor();
                    cursor.moveToPosition(position);

                    final int titleIndex = cursor.getColumnIndex(AudioColumns.TITLE);
                    final String title = cursor.getString(titleIndex);

                    ((TextView) dialog.findViewById(R.id.message)).setText(getString(
                            R.string.confirm_delete_message, title));
                    break;
                }
                default: {
                    super.onPreparePinnedDialog(id, dialog, arguments);
                }
            }
        }
    };

    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final int position = (Integer) v.getTag(R.id.tag_position);

            switch (v.getId()) {
                case R.id.more_button: {
                    final Bundle arguments = new Bundle();
                    arguments.putInt(KEY_POSITION, position);
                    mPinnedDialogManager.showPinnedDialog(PINNED_ACTIONS, v, arguments);
                    break;
                }
                case R.id.delete: {
                    final View pinnedView = mPinnedDialogManager.getPinnedView(PINNED_ACTIONS);

                    mPinnedDialogManager.dismissPinnedDialog(PINNED_ACTIONS);

                    final Bundle arguments = new Bundle();
                    arguments.putInt(KEY_POSITION, position);
                    mPinnedDialogManager.showPinnedDialog(PINNED_CONFIRM_DELETE, pinnedView,
                            arguments);
                    break;
                }
                case R.id.ringtone: {
                    startActivity(new Intent("android.settings.SOUND_SETTINGS"));
                    break;
                }
                case R.id.confirm_delete: {
                    mPinnedDialogManager.dismissPinnedDialog(PINNED_CONFIRM_DELETE);

                    final Cursor cursor = mCursorAdapter.getCursor();
                    cursor.moveToPosition(position);

                    final int dataIndex = cursor.getColumnIndex(AudioColumns.DATA);
                    final String dataPath = cursor.getString(dataIndex);

                    if (new File(dataPath).delete()) {
                        final int idIndex = cursor.getColumnIndex(AudioColumns._ID);
                        final long id = cursor.getLong(idIndex);
                        final Uri uriForPath = Media.getContentUriForPath(dataPath);

                        getContentResolver().delete(uriForPath, AudioColumns._ID + "=?", new String[] {
                            "" + id
                        });
                    }

                    requestCursor();
                    break;
                }
                case R.id.cancel_delete: {
                    mPinnedDialogManager.dismissPinnedDialog(PINNED_CONFIRM_DELETE);
                    break;
                }
                case R.id.share: {
                    mPinnedDialogManager.dismissPinnedDialog(PINNED_ACTIONS);

                    final Cursor cursor = mCursorAdapter.getCursor();
                    cursor.moveToPosition(position);

                    final int columnIndex = cursor.getColumnIndex(AudioColumns.DATA);
                    final String dataPath = cursor.getString(columnIndex);
                    final Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(dataPath));
                    shareIntent.setType("audio/wav");

                    final Intent chooserIntent = Intent.createChooser(shareIntent,
                            getString(R.string.share_to));
                    startActivity(chooserIntent);
                    break;
                }
            }
        }
    };

    private final OnItemClickListener mOnMoreClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
            switch (view.getId()) {
                case R.id.more_button: {
                    final Bundle arguments = new Bundle();
                    arguments.putInt(KEY_POSITION, position);
                    mPinnedDialogManager.showPinnedDialog(PINNED_ACTIONS, view, arguments);
                    break;
                }
                case R.id.central_block: {
                    final Cursor cursor = mCursorAdapter.getCursor();
                    final int dataIndex = cursor.getColumnIndex(AudioColumns.DATA);

                    cursor.moveToPosition(position);

                    final String data = cursor.getString(dataIndex);
                    final PlaybackDialog playback = new PlaybackDialog(LibraryActivity.this, true);

                    try {
                        playback.setFile(data);
                        playback.show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    };

    private class LibraryCursorAdapter extends CursorAdapter {
        private final int mLayoutId;
        private final String[] mFrom;
        private final int[] mTo;

        public LibraryCursorAdapter(Context context, Cursor c, int layoutId, String[] from, int[] to) {
            super(context, c, false);

            if (from.length != to.length) {
                throw new IllegalArgumentException("From and to arrays must be of equal length.");
            }

            mLayoutId = layoutId;
            mFrom = from;
            mTo = to;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final int position = cursor.getPosition();
            final long id = getItemId(position);

            for (int i = 0; i < mFrom.length; i++) {
                final int columnIndex = cursor.getColumnIndex(mFrom[i]);
                final String value = cursor.getString(columnIndex);
                final TextView textView = (TextView) view.findViewById(mTo[i]);

                if (MediaStore.Audio.Media.DATE_ADDED.equals(mFrom[i])) {
                    final long longValue = Long.parseLong(value);
                    final Date date = new Date(longValue * 1000);
                    final java.text.DateFormat dateFormat = DateFormat.getMediumDateFormat(context);
                    final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
                    final CharSequence formatted = context.getString(R.string.date_at_time,
                            dateFormat.format(date), timeFormat.format(date));

                    textView.setText(formatted);
                } else {
                    textView.setText(value);
                }
            }

            view.findViewById(R.id.central_block).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mOnMoreClickListener.onItemClick(getListView(), v, position, id);
                }
            });

            view.findViewById(R.id.more_button).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mOnMoreClickListener.onItemClick(getListView(), v, position, id);
                }
            });
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);

            return inflater.inflate(mLayoutId, null);
        }

    }

    private static class LoadMediaFromAlbum extends AsyncTask<Void, Void, Cursor> {
        private final Context mContext;

        public LoadMediaFromAlbum(Context context) {
            mContext = context;
        }

        public Cursor doInBackground(Void... arg) {
            final String album = mContext.getString(R.string.album_name);
            final ContentResolver resolver = mContext.getContentResolver();
            final String directory = Environment.getExternalStorageDirectory().getPath()
                    + "/typeandspeak";

            final String[] projection = new String[] {
                    BaseColumns._ID, AudioColumns.TITLE, AudioColumns.DATA, AudioColumns.DATE_ADDED
            };
            final String selection = AudioColumns.ALBUM + "=? OR " + AudioColumns.DATA + " LIKE ?";
            final String[] args = new String[] {
                album, directory + "/%"
            };

            return resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                    selection, args, AudioColumns.DATE_ADDED + " DESC");
        }
    }
}
