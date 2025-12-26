package com.example.object_detection_app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    // UI elements
    private ImageView imgLogo;
    private TextView tvAppName;
    private TextView tvTagline;
    private TextView tvLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize views - USE CORRECT IDs FROM YOUR XML
        imgLogo = findViewById(R.id.imgLogo);
        tvAppName = findViewById(R.id.tvAppName);
        tvTagline = findViewById(R.id.tvTagline);
        tvLoading = findViewById(R.id.tvLoading);

        // Apply animations
        applyAnimations();

        // Start main activity after delay
        navigateToMainActivity();
    }

    private void applyAnimations() {
        // Fade in animation for logo
        Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeIn.setDuration(1500);
        imgLogo.startAnimation(fadeIn);

        // Slide up animation for app name
        Animation slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        slideUp.setDuration(1000);
        tvAppName.startAnimation(slideUp);

        // Slide up animation for tagline (with delay)
        Animation slideUpDelayed = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        slideUpDelayed.setDuration(1000);
        slideUpDelayed.setStartOffset(300); // 300ms delay
        tvTagline.startAnimation(slideUpDelayed);

        // Blinking animation for loading text
        Animation blink = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        blink.setDuration(800);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(Animation.INFINITE);
        tvLoading.startAnimation(blink);
    }

    private void navigateToMainActivity() {
        new Handler().postDelayed(() -> {
            // Start MainActivity
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);

            // Apply fade out transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

            // Finish splash activity
            finish();
        }, 2500); // 2.5 seconds
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clear animations when activity is paused
        imgLogo.clearAnimation();
        tvAppName.clearAnimation();
        tvTagline.clearAnimation();
        tvLoading.clearAnimation();
    }
}