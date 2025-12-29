package com.example.object_detection_app;

import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class HowItWorksActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_how_it_works);

        MaterialButton btnBack = findViewById(R.id.btnBack);

        // Fade in animation for button
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(800);
        fadeIn.setStartOffset(300);
        btnBack.startAnimation(fadeIn);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Button press animation
                v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                v.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setDuration(100)
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                finish();
                                            }
                                        });
                            }
                        });
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        // Smooth transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}