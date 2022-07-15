package com.example.swigtest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.LinearLayout;

public class CreateActivity extends AppCompatActivity {

    private MainGlView glView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

        //流体画面を生成
        glView = new MainGlView(this, null, MainActivity.PictureButton.Soft, null);

        LinearLayout root = findViewById(R.id.gl_view_root);
        root.addView(glView);
    }
}