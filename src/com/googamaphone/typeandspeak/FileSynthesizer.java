
package com.googamaphone.typeandspeak;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;

import com.googamaphone.typeandspeak.utils.ReferencedHandler;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.Audio.Media;
import android.provider.MediaStore.MediaColumns;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;

public class FileSynthesizer {
    private static final String UTTERANCE_ID = "synthesize";
    private static final int UTTERANCE_COMPLETED = 1;

    private final ContentValues mContentValues = new ContentValues(10);
    private final HashMap<String, String> mSpeechParams = new HashMap<String, String>();

    private final Context mContext;
    private final TextToSpeech mTts;
    private final String mArtistValue;
    private final String mAlbumValue;

    private ProgressDialog mProgressDialog;
    private FileSynthesizerListener mListener;

    private boolean mCanceled = false;

    public FileSynthesizer(Context context, TextToSpeech tts) {
        mContext = context;
        mTts = tts;

        mArtistValue = mContext.getString(R.string.app_name);
        mAlbumValue = mContext.getString(R.string.album_name);

        mSpeechParams.put(Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
    }

    public void setListener(FileSynthesizerListener listener) {
        mListener = listener;
    }

    @SuppressWarnings("deprecation")
    private void onUtteranceCompleted(String utteranceId) {
        mTts.setOnUtteranceCompletedListener(null);

        if (mCanceled) {
            onWriteCanceled();
        } else {
            onWriteCompleted();
        }
    }

    /**
     * Inserts media information into the database after a successful save
     * operation.
     *
     * @param contentValues The media descriptor values.
     */
    private void onWriteCompleted() {
        final ContentResolver resolver = mContext.getContentResolver();
        final String path = mContentValues.getAsString(MediaColumns.DATA);
        final Uri uriForPath = Media.getContentUriForPath(path);

        resolver.insert(uriForPath, mContentValues);

        // Clears last queue element to avoid deletion on exit.
        mTts.speak("", TextToSpeech.QUEUE_FLUSH, null);

        try {
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        } catch (final IllegalArgumentException e) {
            e.printStackTrace();
        }

        if (mListener != null) {
            mListener.onFileSynthesized(mContentValues);
        }

        mContentValues.clear();
    }

    /**
     * Deletes the partially completed file after a canceled save operation.
     *
     * @param values The media descriptor values.
     */
    private void onWriteCanceled() {
        try {
            final String path = mContentValues.getAsString(MediaColumns.DATA);
            new File(path).delete();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        final String title = mContext.getString(R.string.canceled_title);
        final String message = mContext.getString(R.string.canceled_message);
        final AlertDialog alert = new Builder(mContext).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).create();

        mTts.stop();

        try {
            alert.show();
        } catch (final RuntimeException e) {
            e.printStackTrace();
        }

        mContentValues.clear();
    }

    @SuppressWarnings("deprecation")
    public void writeInput(String text, Locale locale, int pitch, int rate, String filename) {
        mCanceled = false;

        if (filename.toLowerCase().endsWith(".wav")) {
            filename = filename.substring(0, filename.length() - 4);
        }

        filename = filename.trim();

        if (filename.length() <= 0) {
            return;
        }

        final String directory = Environment.getExternalStorageDirectory().getPath()
                + "/typeandspeak";

        final File outdir = new File(directory);
        final File outfile = new File(directory + "/" + filename + ".wav");

        final String message;
        final AlertDialog alert;

        if (outfile.exists()) {
            message = mContext.getString(R.string.exists_message, filename);
            alert = new Builder(mContext).setTitle(R.string.exists_title).setMessage(message)
                    .setPositiveButton(android.R.string.ok, null).create();
        } else if (!outdir.exists() && !outdir.mkdirs()) {
            message = mContext.getString(R.string.no_write_message, filename);
            alert = new Builder(mContext).setTitle(R.string.no_write_title).setMessage(message)
                    .setPositiveButton(android.R.string.ok, null).create();
        } else {
            // Attempt to set the locale.
            if (locale != null) {
                mTts.setLanguage(locale);
            }

            // Populate content values for the media provider.
            mContentValues.clear();
            mContentValues.put(MediaColumns.DISPLAY_NAME, filename);
            mContentValues.put(MediaColumns.TITLE, filename);
            mContentValues.put(AudioColumns.ARTIST, mArtistValue);
            mContentValues.put(AudioColumns.ALBUM, mAlbumValue);
            mContentValues.put(AudioColumns.IS_ALARM, true);
            mContentValues.put(AudioColumns.IS_RINGTONE, true);
            mContentValues.put(AudioColumns.IS_NOTIFICATION, true);
            mContentValues.put(AudioColumns.IS_MUSIC, true);
            mContentValues.put(MediaColumns.MIME_TYPE, "audio/wav");
            mContentValues.put(MediaColumns.DATA, outfile.getAbsolutePath());

            mTts.setPitch(pitch / 50.0f);
            mTts.setSpeechRate(rate / 50.0f);
            mTts.setOnUtteranceCompletedListener(mOnUtteranceCompletedListener);
            mTts.synthesizeToFile(text, mSpeechParams, outfile.getAbsolutePath());

            message = mContext.getString(R.string.saving_message, filename);

            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setCancelable(true);
            mProgressDialog.setTitle(R.string.saving_title);
            mProgressDialog.setMessage(message);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setOnCancelListener(mOnCancelListener);

            alert = mProgressDialog;
        }

        try {
            alert.show();
        } catch (final RuntimeException e) {
            e.printStackTrace();
        }
    }
    
    private SynthesizerHandler mHandler = new SynthesizerHandler(this);

    private final TextToSpeech.OnUtteranceCompletedListener mOnUtteranceCompletedListener = new TextToSpeech.OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            mHandler.obtainMessage(UTTERANCE_COMPLETED, utteranceId).sendToTarget();
        }
    };

    private final DialogInterface.OnCancelListener mOnCancelListener = new OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            mTts.stop();
        }
    };
    
    private static class SynthesizerHandler extends ReferencedHandler<FileSynthesizer> {
        public SynthesizerHandler(FileSynthesizer parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, FileSynthesizer parent) {
            switch (msg.what) {
                case UTTERANCE_COMPLETED:
                    parent.onUtteranceCompleted((String) msg.obj);
                    break;
            }
        }
    }

    public interface FileSynthesizerListener {
        public void onFileSynthesized(ContentValues contentValues);
    }
}
