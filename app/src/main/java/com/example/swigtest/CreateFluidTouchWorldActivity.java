package com.example.swigtest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.LinearLayout;

/*
 * 流体画面
 *   レイヤー１：流体用の物理世界
 */
public class CreateFluidTouchWorldActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

        //流体画面を生成
        FluidGLSurfaceView glView = new FluidGLSurfaceView(this, null, MenuActivity.PictureButton.Soft, null);

        LinearLayout root = findViewById(R.id.gl_view_root);
        root.addView(glView);
    }
}