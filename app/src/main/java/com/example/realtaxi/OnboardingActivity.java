package com.example.realtaxi;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button btnNext;
    private int[] layouts = {R.layout.onboarding_screen1, R.layout.onboarding_screen2, R.layout.onboarding_screen3};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        btnNext = findViewById(R.id.btnNext);

        // Set up the adapter
        OnboardingAdapter adapter = new OnboardingAdapter(this, layouts);
        viewPager.setAdapter(adapter);

        btnNext.setOnClickListener(view -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < layouts.length - 1) {
                // Move to the next screen
                viewPager.setCurrentItem(currentItem + 1);
            } else {
                // Last screen, save flag and navigate to MainActivity
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isFirstLaunch", false); // Mark onboarding as completed
                editor.apply();

                startActivity(new Intent(OnboardingActivity.this, LoginActivity.class));

            }
        });

        // Update button text on page change
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                btnNext.setText(position == layouts.length - 1 ? "Start" : "Next");
            }
        });
    }
}

