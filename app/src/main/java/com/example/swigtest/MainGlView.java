package com.example.swigtest;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;

import com.google.fpl.liquidfun.Vec2;

import java.util.ArrayList;

public class MainGlView extends GLSurfaceView {

    MainRenderer renderer;

    public MainGlView(Context context, Bitmap bmp, MenuActivity.PictureButton select, ArrayList<Vec2> touchList) {
        super(context);
        this.renderer = new MainRenderer(this, bmp, select, touchList);
        setRenderer(renderer);
        this.setOnTouchListener(this.renderer);
    }

    public MainRenderer getRenderer() {
        return renderer;
    }
}
