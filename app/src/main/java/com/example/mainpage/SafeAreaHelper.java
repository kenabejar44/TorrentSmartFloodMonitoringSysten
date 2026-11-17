package com.example.mainpage;

import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SafeAreaHelper {

    /**
     * Enables edge-to-edge and applies insets to a top view (toolbar) and optional bottom view.
     * This is the recommended way to handle notches and status bars consistently.
     */
    public static void apply(AppCompatActivity activity, View topView, View bottomView) {
        EdgeToEdge.enable(activity);

        if (topView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        v.getPaddingLeft(),
                        systemBars.top,
                        v.getPaddingRight(),
                        v.getPaddingBottom()
                );
                return insets;
            });
        }

        if (bottomView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        systemBars.bottom
                );
                return insets;
            });
        }
    }

    /**
     * Applies system bar padding (top and/or bottom) to a specific view using an insets listener.
     */
    public static void applySystemBarPadding(View view, boolean applyTop, boolean applyBottom) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int left = v.getPaddingLeft();
            int top = applyTop ? systemBars.top : v.getPaddingTop();
            int right = v.getPaddingRight();
            int bottom = applyBottom ? systemBars.bottom : v.getPaddingBottom();
            v.setPadding(left, top, right, bottom);
            return insets;
        });
    }

    /**
     * Applies insets to the root view directly. Useful for full-screen activities like Login.
     */
    public static void applyToRoot(AppCompatActivity activity) {
        EdgeToEdge.enable(activity);
        View root = activity.findViewById(android.R.id.content);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
    }
}
