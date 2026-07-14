package com.xinghe.helper.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.video.VideoSize;
import com.xinghe.helper.R;
import com.xinghe.helper.cast.CastState;

import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

public class CastPlayerActivity extends AppCompatActivity {

    private static final String TAG = "CastPlayerActivity";
    private static final int SEEK_STEP = 10000;
    private static final int MAX_RETRY = 5;
    private static final long BUFFERING_TIMEOUT = 90000;

    private SurfaceView surfaceView;
    private FrameLayout videoContainer;
    private ImageView imageView;
    private TextView statusText;
    private ProgressBar bufferingProgress;
    private View controlBar;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;

    private ExoPlayer exoPlayer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private Runnable bufferingTimeoutRunnable;

    private String currentUrl = "";
    private String currentMimeType = "";
    private int retryCount = 0;
    private boolean userPaused = false;
    private boolean controlBarVisible = false;
    private boolean pendingPlay = false;
    private String pendingUrl = "";
    private String pendingMimeType = "";

    private final Runnable hideControlBarRunnable = this::hideControlBar;

    private final CastState.StateListener playerListener = new CastState.StateListener() {
        @Override
        public void onSetAVTransportURI(String url, String mimeType) {
        }

        @Override
        public void onPlay(String url, String mimeType) {
            mainHandler.post(() -> {
                if (surfaceView != null && surfaceView.getHolder().getSurface().isValid()) {
                    playMedia(url, mimeType);
                } else {
                    pendingPlay = true;
                    pendingUrl = url;
                    pendingMimeType = mimeType;
                }
            });
        }

        @Override
        public void onPause() {
            mainHandler.post(() -> pausePlay());
        }

        @Override
        public void onStop() {
            mainHandler.post(() -> {
                stopPlay();
                finish();
            });
        }

        @Override
        public void onSeek(long position) {
            mainHandler.post(() -> seekTo(position));
        }

        @Override
        public void onVolume(int volume) {
            mainHandler.post(() -> {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC,
                            (int) (volume * max / 100.0f), 0);
                }
            });
        }

        @Override
        public void onMute(boolean mute) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cast_player);

        surfaceView = findViewById(R.id.surfaceView);
        videoContainer = findViewById(R.id.videoContainer);
        imageView = findViewById(R.id.imageView);
        statusText = findViewById(R.id.statusText);
        bufferingProgress = findViewById(R.id.bufferingProgress);
        controlBar = findViewById(R.id.controlBar);
        seekBar = findViewById(R.id.seekBar);
        currentTimeText = findViewById(R.id.currentTime);
        totalTimeText = findViewById(R.id.totalTime);

        videoContainer.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                   oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                if (exoPlayer != null) {
                    VideoSize size = exoPlayer.getVideoSize();
                    if (size.width > 0 && size.height > 0) {
                        updateVideoAspectRatio(size.width, size.height, size.pixelWidthHeightRatio);
                    }
                }
            }
        });

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        CastState.getInstance().addListener(playerListener);

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.postDelayed(updateRunnable, 1000);

        bufferingTimeoutRunnable = () -> {
            if (exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
                Log.w(TAG, "缓冲超时，重试播放");
                handlePlaybackError();
            }
        };

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && exoPlayer != null && exoPlayer.getDuration() > 0) {
                    long dur = exoPlayer.getDuration();
                    long target = dur * progress / 100;
                    currentTimeText.setText(formatTime(target));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (exoPlayer != null && exoPlayer.getDuration() > 0) {
                    long dur = exoPlayer.getDuration();
                    long target = dur * seekBar.getProgress() / 100;
                    seekTo(target);
                    CastState.getInstance().setPosition(target);
                }
            }
        });

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "surfaceCreated");
                if (pendingPlay && pendingUrl != null && !pendingUrl.isEmpty()) {
                    pendingPlay = false;
                    String url = pendingUrl;
                    String mime = pendingMimeType;
                    pendingUrl = "";
                    pendingMimeType = "";
                    playMedia(url, mime);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed");
            }
        });

        CastState state = CastState.getInstance();
        if (state.getCurrentUrl() != null && !state.getCurrentUrl().isEmpty() && state.isPlaying()) {
            if (surfaceView.getHolder().getSurface().isValid()) {
                playMedia(state.getCurrentUrl(), state.getCurrentMimeType());
            } else {
                pendingPlay = true;
                pendingUrl = state.getCurrentUrl();
                pendingMimeType = state.getCurrentMimeType();
            }
        }
    }

    private DataSource.Factory buildDataSourceFactory() {
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(60000)
                .setAllowCrossProtocolRedirects(true);
        return new DefaultDataSource.Factory(this, httpDataSourceFactory);
    }

    private void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) {
            pausePlay();
            CastState.getInstance().pause();
        } else {
            resumePlay();
            CastState.getInstance().setPlaying(true);
        }
    }

    private void playMedia(String url, String mimeType) {
        if (url == null || url.isEmpty()) return;

        if (url.equals(currentUrl) && exoPlayer != null) {
            resumePlay();
            return;
        }

        currentUrl = url;
        currentMimeType = mimeType != null ? mimeType : "video/mp4";
        retryCount = 0;

        if (currentMimeType.startsWith("image/")) {
            playImage(url);
        } else {
            playVideo(url);
        }
    }

    private void playVideo(String url) {
        Log.d(TAG, "playVideo: " + url);
        releasePlayer();

        statusText.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        surfaceView.setVisibility(View.VISIBLE);
        showBuffering(true);
        startBufferingTimeout();

        try {
            LoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            60000,
                            300000,
                            5000,
                            10000
                    )
                    .setTargetBufferBytes(C.LENGTH_UNSET)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            DataSource.Factory dataSourceFactory = buildDataSourceFactory();
            DefaultMediaSourceFactory mediaSourceFactory =
                    new DefaultMediaSourceFactory(dataSourceFactory);

            exoPlayer = new ExoPlayer.Builder(this)
                    .setLoadControl(loadControl)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setWakeMode(C.WAKE_MODE_NETWORK)
                    .setHandleAudioBecomingNoisy(true)
                    .setSeekForwardIncrementMs(SEEK_STEP)
                    .setSeekBackIncrementMs(SEEK_STEP)
                    .build();

            exoPlayer.setVideoSurfaceView(surfaceView);
            surfaceView.setKeepScreenOn(true);

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        updateVideoAspectRatio(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio);
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    switch (playbackState) {
                        case Player.STATE_READY:
                            Log.d(TAG, "STATE_READY");
                            cancelBufferingTimeout();
                            showBuffering(false);
                            showControlBar();
                            long dur = exoPlayer.getDuration();
                            totalTimeText.setText(formatTime(dur));
                            CastState.getInstance().setDuration(dur);
                            if (!userPaused) {
                                exoPlayer.setPlayWhenReady(true);
                                CastState.getInstance().setPlaying(true);
                            }
                            break;
                        case Player.STATE_BUFFERING:
                            Log.d(TAG, "STATE_BUFFERING");
                            showBuffering(true);
                            startBufferingTimeout();
                            break;
                        case Player.STATE_ENDED:
                            Log.d(TAG, "STATE_ENDED");
                            cancelBufferingTimeout();
                            showBuffering(false);
                            CastState.getInstance().setPlaying(false);
                            break;
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "onPlayerError", error);
                    cancelBufferingTimeout();
                    showBuffering(false);
                    handlePlaybackError();
                }
            });

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(!userPaused);

        } catch (Exception e) {
            Log.e(TAG, "playVideo error", e);
            cancelBufferingTimeout();
            showBuffering(false);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("播放失败：" + e.getMessage());
        }
    }

    private void handlePlaybackError() {
        if (currentUrl == null || currentUrl.isEmpty()) {
            return;
        }
        if (retryCount < MAX_RETRY) {
            retryCount++;
            Log.w(TAG, "重试播放 " + retryCount + "/" + MAX_RETRY);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("网络异常，重试中(" + retryCount + "/" + MAX_RETRY + ")...");
            String url = currentUrl;
            currentUrl = "";
            long delay = retryCount == 1 ? 1000 : 2000L * retryCount;
            mainHandler.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    playVideo(url);
                }
            }, delay);
        } else {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("播放失败，请检查网络或视频源");
            Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void playImage(String url) {
        releasePlayer();
        surfaceView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.GONE);
        showBuffering(true);

        new Thread(() -> {
            try {
                InputStream is = new URL(url).openStream();
                final Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                mainHandler.post(() -> {
                    showBuffering(false);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    } else {
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText("图片加载失败");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showBuffering(false);
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText("图片加载失败");
                });
            }
        }).start();
    }

    private void pausePlay() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            userPaused = true;
        }
    }

    private void resumePlay() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
            userPaused = false;
        }
    }

    private void stopPlay() {
        cancelBufferingTimeout();
        mainHandler.removeCallbacksAndMessages(null);
        releasePlayer();
        surfaceView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("等待投屏...");
        controlBar.setVisibility(View.GONE);
        currentTimeText.setText("00:00");
        totalTimeText.setText("00:00");
        seekBar.setProgress(0);
        currentUrl = "";
        currentMimeType = "";
        userPaused = false;
        pendingPlay = false;
        pendingUrl = "";
        pendingMimeType = "";
        retryCount = 0;
    }

    private void seekTo(long position) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(position);
        }
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                exoPlayer.clearMediaItems();
                exoPlayer.setVideoSurfaceView(null);
                exoPlayer.release();
            } catch (Exception ignored) {}
            exoPlayer = null;
        }
        userPaused = false;
        if (surfaceView != null) {
            surfaceView.setKeepScreenOn(false);
        }
    }

    private void startBufferingTimeout() {
        cancelBufferingTimeout();
        mainHandler.postDelayed(bufferingTimeoutRunnable, BUFFERING_TIMEOUT);
    }

    private void cancelBufferingTimeout() {
        mainHandler.removeCallbacks(bufferingTimeoutRunnable);
    }

    private void updateProgress() {
        if (exoPlayer != null && exoPlayer.getDuration() > 0) {
            try {
                long pos = exoPlayer.getCurrentPosition();
                long dur = exoPlayer.getDuration();
                seekBar.setProgress((int) (pos * 100 / dur));
                currentTimeText.setText(formatTime(pos));
                CastState.getInstance().setPosition(pos);
            } catch (Exception ignored) {}
        }
    }

    private void showBuffering(boolean show) {
        bufferingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showControlBar() {
        controlBarVisible = true;
        controlBar.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideControlBarRunnable);
        mainHandler.postDelayed(hideControlBarRunnable, 8000);
    }

    private void hideControlBar() {
        controlBarVisible = false;
        controlBar.setVisibility(View.GONE);
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    private void updateVideoAspectRatio(int videoWidth, int videoHeight, float pixelWidthHeightRatio) {
        if (videoContainer == null) return;
        float videoRatio = videoWidth * pixelWidthHeightRatio / (float) videoHeight;

        int containerWidth = videoContainer.getWidth();
        int containerHeight = videoContainer.getHeight();
        if (containerWidth <= 0 || containerHeight <= 0) return;

        float containerRatio = containerWidth / (float) containerHeight;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surfaceView.getLayoutParams();
        if (videoRatio > containerRatio) {
            params.width = containerWidth;
            params.height = (int) (containerWidth / videoRatio);
        } else {
            params.height = containerHeight;
            params.width = (int) (containerHeight * videoRatio);
        }
        surfaceView.setLayoutParams(params);
        Log.d(TAG, "updateVideoAspectRatio: " + videoWidth + "x" + videoHeight
                + " ratio=" + videoRatio + " container=" + containerWidth + "x" + containerHeight
                + " view=" + params.width + "x" + params.height);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            stopPlay();
            CastState.getInstance().stop();
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (exoPlayer != null) {
                    togglePlayPause();
                    showControlBar();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                showControlBar();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (exoPlayer != null) {
                    togglePlayPause();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (exoPlayer != null) {
                    long pos = exoPlayer.getCurrentPosition();
                    long dur = exoPlayer.getDuration();
                    seekTo(Math.min(pos + SEEK_STEP, dur));
                    showControlBar();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (exoPlayer != null) {
                    long pos = exoPlayer.getCurrentPosition();
                    seekTo(Math.max(pos - SEEK_STEP, 0));
                    showControlBar();
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelBufferingTimeout();
        mainHandler.removeCallbacks(updateRunnable);
        mainHandler.removeCallbacks(hideControlBarRunnable);
        releasePlayer();
        CastState.getInstance().removeListener(playerListener);
    }
}
