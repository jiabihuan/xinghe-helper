package com.xinghe.helper.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.xinghe.helper.R;
import com.xinghe.helper.cast.CastState;

import java.io.InputStream;
import java.net.URL;

public class CastPlayerActivity extends AppCompatActivity {

    private VideoView videoView;
    private ImageView imageView;
    private TextView statusText;
    private Handler mainHandler;
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cast_player);

        videoView = findViewById(R.id.videoView);
        imageView = findViewById(R.id.imageView);
        statusText = findViewById(R.id.statusText);
        mainHandler = new Handler(Looper.getMainLooper());

        videoView.setAudioFocusRequest(AudioManager.AUDIOFOCUS_GAIN);

        CastState.getInstance().setListener(new CastState.StateListener() {
            @Override
            public void onPlay(String url, String mimeType) {
                mainHandler.post(() -> playMedia(url, mimeType));
            }

            @Override
            public void onPause() {
                mainHandler.post(() -> {
                    if (videoView.isPlaying()) {
                        videoView.pause();
                    }
                });
            }

            @Override
            public void onStop() {
                mainHandler.post(() -> {
                    videoView.stopPlayback();
                    videoView.setVisibility(View.GONE);
                    imageView.setVisibility(View.GONE);
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText("等待投屏...");
                });
            }

            @Override
            public void onSeek(long position) {
                mainHandler.post(() -> videoView.seekTo((int) position));
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
                mainHandler.post(() -> {
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (am != null) {
                        am.setStreamMute(AudioManager.STREAM_MUSIC, mute);
                    }
                });
            }
        });

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (videoView.isPlaying()) {
                    CastState.getInstance().setPosition(videoView.getCurrentPosition());
                    CastState.getInstance().setDuration(videoView.getDuration());
                }
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.postDelayed(updateRunnable, 1000);

        if (CastState.getInstance().getCurrentUrl() != null && !CastState.getInstance().getCurrentUrl().isEmpty()) {
            playMedia(CastState.getInstance().getCurrentUrl(), CastState.getInstance().getCurrentMimeType());
        }
    }

    private void playMedia(String url, String mimeType) {
        statusText.setVisibility(View.GONE);
        if (mimeType.startsWith("image/")) {
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            new Thread(() -> {
                try {
                    InputStream is = new URL(url).openStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    if (bitmap != null) {
                        mainHandler.post(() -> imageView.setImageBitmap(bitmap));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText("图片加载失败");
                    });
                }
            }).start();
        } else {
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(Uri.parse(url));
            videoView.setOnPreparedListener(mp -> {
                CastState.getInstance().setDuration(videoView.getDuration());
                videoView.start();
                CastState.getInstance().setPlaying(true);
            });
            videoView.setOnCompletionListener(mp -> {
                CastState.getInstance().setPlaying(false);
                CastState.getInstance().stop();
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText("播放失败");
                return true;
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(updateRunnable);
        CastState.getInstance().setListener(null);
    }
}
