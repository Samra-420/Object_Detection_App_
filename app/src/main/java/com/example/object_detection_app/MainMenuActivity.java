package com.example.object_detection_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class MainMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        MaterialCardView cardStart = findViewById(R.id.cardStart);
        MaterialCardView cardHow = findViewById(R.id.cardHow);
        MaterialCardView cardSettings = findViewById(R.id.cardSettings);

        // Add fade-in animation to header
        Animation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        findViewById(R.id.headerLayout).startAnimation(fadeIn);

        // Add sequential animations to cards
        animateCardWithDelay(cardStart, 0);
        animateCardWithDelay(cardHow, 200);
        animateCardWithDelay(cardSettings, 400);

        // Click listeners with animations
        cardStart.setOnClickListener(v -> {
            animateCardClick(v, CameraActivity.class);
        });

        cardHow.setOnClickListener(v -> {
            animateCardClick(v, HowItWorksActivity.class);
        });

        cardSettings.setOnClickListener(v -> {
            animateCardClick(v, SettingsActivity.class);
        });
    }

    private void animateCardWithDelay(View card, int delay) {
        card.setAlpha(0f);
        card.setScaleX(0.8f);
        card.setScaleY(0.8f);

        card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(500)
                .start();
    }

    private void animateCardClick(View v, Class<?> activityClass) {
        // Scale down animation
        v.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    // Scale back up
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction(() -> {
                                // Navigate to activity
                                Intent intent = new Intent(MainMenuActivity.this, activityClass);
                                startActivity(intent);
                                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                            });
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Optional: Add small bounce animation when returning to menu
        MaterialCardView cardStart = findViewById(R.id.cardStart);
        cardStart.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(200)
                .withEndAction(() -> {
                    cardStart.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start();
                });
    }
}