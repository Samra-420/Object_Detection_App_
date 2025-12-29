package com.example.object_detection_app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private ImageView imgLogo;
    private TextView tvAppName, tvTagline, tvLoading;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        imgLogo = findViewById(R.id.imgLogo);
        tvAppName = findViewById(R.id.tvAppName);
        tvTagline = findViewById(R.id.tvTagline);
        tvLoading = findViewById(R.id.tvLoading);

        applyAnimations();
        navigateNext();
    }

    private void applyAnimations() {
        Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeIn.setDuration(1200);
        imgLogo.startAnimation(fadeIn);

        Animation slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        slideUp.setDuration(900);
        tvAppName.startAnimation(slideUp);

        Animation slideUpDelayed = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        slideUpDelayed.setDuration(900);
        slideUpDelayed.setStartOffset(200);
        tvTagline.startAnimation(slideUpDelayed);

        Animation blink = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        blink.setDuration(700);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(Animation.INFINITE);
        tvLoading.startAnimation(blink);
    }

    private void navigateNext() {
        handler.postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainMenuActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 2300);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }
}
