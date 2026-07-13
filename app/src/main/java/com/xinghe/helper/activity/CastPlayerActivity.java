package com.xinghe.helper.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xinghe.helper.R;
import com.xinghe.helper.cast.CastState;

import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

public class CastPlayerActivity extends AppCompatActivity {

    private static final String TAG = "CastPlayerActivity";
    private static final int SEEK_STEP = 10000; // 快进快退步长 10秒
    private static final int MAX_RETRY = 3;     // 最大重试次数

    private SurfaceView surfaceView;
    private ImageView imageView;
    private TextView statusText;
    private ProgressBar bufferingProgress;
    private View controlBar;
    private TextView btnPlayPause;
    private TextView btnStop;
    private TextView btnForward;
    private TextView btnBackward;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;

    private MediaPlayer mediaPlayer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private String currentUrl = "";
    private String currentMimeType = "";
    private int retryCount = 0;
    private boolean isPrepared = false;
    private boolean userPaused = false;
    private boolean controlBarVisible = false;
    private final Runnable hideControlBarRunnable = this::hideControlBar;

    private final CastState.StateListener playerListener = new CastState.StateListener() {
        @Override
        public void onPlay(String url, String mimeType) {
            mainHandler.post(() -> playMedia(url, mimeType));
        }

        @Override
        public void onPause() {
            mainHandler.post(() -> pausePlay());
        }

        @Override
        public void onStop() {
            mainHandler.post(() -> stopPlay());
        }

        @Override
        public void onSeek(long position) {
            mainHandler.post(() -> seekTo((int) position));
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
            // 交给系统音量管理即可
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cast_player);

        surfaceView = findViewById(R.id.surfaceView);
        imageView = findViewById(R.id.imageView);
        statusText = findViewById(R.id.statusText);
        bufferingProgress = findViewById(R.id.bufferingProgress);
        controlBar = findViewById(R.id.controlBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnStop = findViewById(R.id.btnStop);
        btnForward = findViewById(R.id.btnForward);
        btnBackward = findViewById(R.id.btnBackward);
        seekBar = findViewById(R.id.seekBar);
        currentTimeText = findViewById(R.id.currentTime);
        totalTimeText = findViewById(R.id.totalTime);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (mediaPlayer != null && isPrepared) {
                    mediaPlayer.setDisplay(holder);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.setDisplay(null);
                    } catch (Exception ignored) {}
                }
            }
        });

        CastState.getInstance().addListener(playerListener);

        // 进度更新
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.postDelayed(updateRunnable, 500);

        // 按钮事件
        btnPlayPause.setOnClickListener(v -> {
            if (mediaPlayer != null && isPrepared) {
                if (mediaPlayer.isPlaying()) {
                    pausePlay();
                    CastState.getInstance().pause();
                } else {
                    resumePlay();
                    CastState.getInstance().setPlaying(true);
                }
            }
        });

        btnStop.setOnClickListener(v -> {
            stopPlay();
            CastState.getInstance().stop();
        });

        btnForward.setOnClickListener(v -> {
            if (mediaPlayer != null && isPrepared) {
                int pos = mediaPlayer.getCurrentPosition();
                int dur = mediaPlayer.getDuration();
                int target = Math.min(pos + SEEK_STEP, dur);
                seekTo(target);
                CastState.getInstance().setPosition(target);
            }
        });

        btnBackward.setOnClickListener(v -> {
            if (mediaPlayer != null && isPrepared) {
                int pos = mediaPlayer.getCurrentPosition();
                int target = Math.max(pos - SEEK_STEP, 0);
                seekTo(target);
                CastState.getInstance().setPosition(target);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && isPrepared) {
                    int dur = mediaPlayer.getDuration();
                    int target = dur * progress / 100;
                    currentTimeText.setText(formatTime(target));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null && isPrepared) {
                    int dur = mediaPlayer.getDuration();
                    int target = dur * seekBar.getProgress() / 100;
                    seekTo(target);
                    CastState.getInstance().setPosition(target);
                }
            }
        });

        // 如果已有投屏内容，直接播放
        CastState state = CastState.getInstance();
        if (state.getCurrentUrl() != null && !state.getCurrentUrl().isEmpty()) {
            playMedia(state.getCurrentUrl(), state.getCurrentMimeType());
        }
    }

    private void playMedia(String url, String mimeType) {
        if (url == null || url.isEmpty()) return;

        // 同一个URL且已准备好，不重复播放
        if (url.equals(currentUrl) && isPrepared) {
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

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, Uri.parse(url));
            mediaPlayer.setSurface(surfaceView.getHolder().getSurface());
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setScreenOnWhilePlaying(true);

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "onPrepared");
                isPrepared = true;
                showBuffering(false);
                showControlBar();
                int dur = mp.getDuration();
                totalTimeText.setText(formatTime(dur));
                CastState.getInstance().setDuration(dur);
                if (!userPaused) {
                    mp.start();
                    CastState.getInstance().setPlaying(true);
                    btnPlayPause.setText("暂停");
                }
            });

            mediaPlayer.setOnBufferingUpdateListener((mp, percent) -> {
                // 系统自动处理缓冲，这里仅做日志
                Log.d(TAG, "buffering: " + percent + "%");
            });

            mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        showBuffering(true);
                        return true;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        showBuffering(false);
                        return true;
                }
                return false;
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "onCompletion");
                CastState.getInstance().setPlaying(false);
                btnPlayPause.setText("重播");
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "onError what=" + what + " extra=" + extra);
                showBuffering(false);
                handlePlaybackError(what, extra);
                return true;
            });

            // 异步准备，不阻塞主线程
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "playVideo error", e);
            showBuffering(false);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("播放失败：" + e.getMessage());
        }
    }

    private void handlePlaybackError(int what, int extra) {
        // MEDIA_ERROR_IO(1) / MEDIA_ERROR_MALFORMED(-1007) / MEDIA_ERROR_TIMED_OUT(-110) 等可重试
        if (retryCount < MAX_RETRY) {
            retryCount++;
            Log.w(TAG, "重试播放 " + retryCount + "/" + MAX_RETRY);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("网络异常，重试中(" + retryCount + "/" + MAX_RETRY + ")...");
            mainHandler.postDelayed(() -> {
                if (currentUrl != null && !currentUrl.isEmpty()) {
                    String url = currentUrl;
                    currentUrl = ""; // 清空以便重新播放
                    playVideo(url);
                }
            }, 2000L * retryCount); // 指数退避
        } else {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("播放失败，请检查网络或视频源");
            Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void playImage(String url) {
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
        if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            userPaused = true;
            btnPlayPause.setText("播放");
        }
    }

    private void resumePlay() {
        if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            userPaused = false;
            btnPlayPause.setText("暂停");
        }
    }

    private void stopPlay() {
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
        isPrepared = false;
        userPaused = false;
    }

    private void seekTo(int position) {
        if (mediaPlayer != null && isPrepared) {
            try {
                mediaPlayer.seekTo(position);
            } catch (Exception e) {
                Log.e(TAG, "seekTo error", e);
            }
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {}
            try {
                mediaPlayer.reset();
            } catch (Exception ignored) {}
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        isPrepared = false;
        userPaused = false;
    }

    private void updateProgress() {
        if (mediaPlayer != null && isPrepared) {
            int pos = mediaPlayer.getCurrentPosition();
            int dur = mediaPlayer.getDuration();
            if (dur > 0) {
                seekBar.setProgress(pos * 100 / dur);
            }
            currentTimeText.setText(formatTime(pos));
            CastState.getInstance().setPosition(pos);
        }
    }

    private void showBuffering(boolean show) {
        bufferingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showControlBar() {
        controlBarVisible = true;
        controlBar.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideControlBarRunnable);
        mainHandler.postDelayed(hideControlBarRunnable, 5000);
    }

    private void hideControlBar() {
        controlBarVisible = false;
        controlBar.setVisibility(View.GONE);
    }

    private String formatTime(int ms) {
        if (ms <= 0) return "00:00";
        int totalSec = ms / 1000;
        int h = totalSec / 3600;
        int m = (totalSec % 3600) / 60;
        int s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (controlBarVisible) {
                    // 焦点在控制栏时，不拦截
                    return super.onKeyDown(keyCode, event);
                }
                if (mediaPlayer != null && isPrepared) {
                    if (mediaPlayer.isPlaying()) {
                        pausePlay();
                        CastState.getInstance().pause();
                    } else {
                        resumePlay();
                        CastState.getInstance().setPlaying(true);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!controlBarVisible) {
                    showControlBar();
                    btnBackward.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!controlBarVisible) {
                    showControlBar();
                    btnForward.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (!controlBarVisible && mediaPlayer != null && isPrepared) {
                    showControlBar();
                    btnPlayPause.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (mediaPlayer != null && isPrepared) {
                    if (mediaPlayer.isPlaying()) {
                        pausePlay();
                        CastState.getInstance().pause();
                    } else {
                        resumePlay();
                        CastState.getInstance().setPlaying(true);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (mediaPlayer != null && isPrepared) {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    seekTo(Math.min(pos + SEEK_STEP, dur));
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (mediaPlayer != null && isPrepared) {
                    int pos = mediaPlayer.getCurrentPosition();
                    seekTo(Math.max(pos - SEEK_STEP, 0));
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(updateRunnable);
        mainHandler.removeCallbacks(hideControlBarRunnable);
        releasePlayer();
        CastState.getInstance().removeListener(playerListener);
    }
}
