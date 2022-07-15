package com.example.swigtest;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.fpl.liquidfun.Vec2;

import java.util.ArrayList;


public class MyApplication extends Application {
    private final String TAG = "APPLICATION";
    private Bitmap obj;
    private MainActivity.PictureButton select;
    private ArrayList<Vec2> touchList = new ArrayList<Vec2>();

    /*
    @Override
    public void onCreate() {
        //Application作成時
        //Log.v(TAG,"--- onCreate() in ---");
    }

    @Override
    public void onTerminate() {
        //Application終了時
        //Log.v(TAG,"--- onTerminate() in ---");
    }
    */

    public void setObj(Bitmap bmp){
        obj = bmp;
    }

    public Bitmap getObj(){
        return obj;
    }

    public void clearObj(){
        obj = null;
    }

    public MainActivity.PictureButton getSelect() {
        return select;
    }

    public void setSelect(MainActivity.PictureButton select) {
        this.select = select;
    }

    public ArrayList<Vec2> getTouchList() {
        return touchList;
    }

    public void setTouchList(ArrayList<Vec2> touchList) {
        this.touchList = touchList;
    }
}


