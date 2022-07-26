package com.example.swigtest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;

import com.google.fpl.liquidfun.Vec2;

import java.util.ArrayList;

/*
 * 流体画面
 *   レイヤー２：操作メニュー用画面
 */
public class CreateFluidWorldMenuActivity extends AppCompatActivity {

    private MainGlView glView;
    private MyApplication app;

    //アニメーション時間(ms)
    private static final int MENU_UP_ANIMATION_DURATION = 400;
    private static final int MENU_DOWN_ANIMATION_DURATION = 200;

    //パーティクル画面下部のメニュー
    enum FluidMenuKind{
        CONTENTS,
        INIT
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        app = (MyApplication)this.getApplication();
        Bitmap bitmap                     = app.getObj();
        MenuActivity.PictureButton select = app.getSelect();

        ArrayList<Vec2> touchList = null;
        if(select == MenuActivity.PictureButton.CreateDraw){
            touchList = app.getTouchList();
        }
        //流体画面を生成
        glView = new MainGlView(this, bitmap, select,  touchList );

        setContentView(R.layout.activity_fluid_design);
        LinearLayout root = findViewById(R.id.gl_view_root);
        root.addView(glView);

        /*
        LinearLayout menu = findViewById(R.id.bottom_menu_contents);
        menu.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        LinearLayout menu = findViewById(R.id.bottom_menu_contents);
                    }
                }
         );
         */

        //画面下部のメニュー
        findViewById(R.id.bottom_menu_init).setOnClickListener(new menuIniListerner());


        findViewById(R.id.other_picture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra("Return", "Gallery");
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        findViewById(R.id.regeneration).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainRenderer render = glView.getRenderer();
                render.reqRegeneration();
            }
        });

        findViewById(R.id.pin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainRenderer render = glView.getRenderer();
                render.reqSetPin(true);
            }
        });

        findViewById(R.id.gravity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainRenderer render = glView.getRenderer();
                render.reqGravityDirection(true);
            }
        });

        findViewById(R.id.return_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra("Return", "Menu");
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        //break指定時、タッチ用物体を生成
        if( select == MenuActivity.PictureButton.Break ){
            glView.getRenderer().reqEntryFlickObject(100, 100, 0, 100, 10, FlickFigureFragment.FlickShape.BOX);
        }
    }

    /**
     * メニュー(初期：▲)のリスナー
     */
    private class menuIniListerner implements View.OnClickListener {

        //リスナー受付(初期値は受付OK)
        public boolean enable = true;

        @Override
        public void onClick(View view) {

            //リスナー受付中でなければ、なにもしない
            if(!this.enable){
                return;
            }

            //リスナーの処理を終えるまでは、受付不可にする
            this.enable = false;

            //メニュー本体
            LinearLayout menu_contents = findViewById(R.id.bottom_menu_contents);
            LinearLayout menu_exp = findViewById(R.id.root_explanation);

            //表示非表示を制御
            if(menu_contents.getVisibility() != View.VISIBLE){
                //メニュー本体処理
                this.slide_up(menu_contents);
                glView.getRenderer().reqMenuMove(MainRenderer.MenuMoveControl.UP);

                //説明文のアニメーション表示
                menu_exp.setVisibility(View.VISIBLE);
                Animation animation_in = AnimationUtils.loadAnimation(menu_exp.getContext(), R.anim.slide_right);
                menu_exp.startAnimation(animation_in);

            }else{
                //メニュー本体処理
                this.slide_down(menu_contents);
                glView.getRenderer().reqMenuMove(MainRenderer.MenuMoveControl.DOWN);

                //説明文のアニメーション非表示
                menu_exp.setVisibility(View.INVISIBLE);
                Animation animation_in = AnimationUtils.loadAnimation(view.getContext(), R.anim.slide_left);
                menu_exp.startAnimation(animation_in);
            }
        }

        //アニメーション(スライドアップ)
        private void slide_up( final View v ){
            v.setVisibility(View.VISIBLE);
            TranslateAnimation animate = new TranslateAnimation(
                    0,
                    0,
                    v.getHeight(),
                    0);
            animate.setDuration(MENU_UP_ANIMATION_DURATION);
            animate.setFillAfter(true);
            animate.setInterpolator(new LinearInterpolator());
            animate.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    v.setAlpha(1);
                }
                @Override
                public void onAnimationEnd(final Animation animation) {
                    //menuアニメーション停止通知
                    glView.getRenderer().reqMenuMove(MainRenderer.MenuMoveControl.STOP);

                    //アニメーションが終了すれば、受付可能にする
                    enable = true;
                }
                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            v.startAnimation(animate);
        }

        //アニメーション(スライドダウン)
        private void slide_down( final View v ){
            v.setVisibility(View.INVISIBLE);
            TranslateAnimation animate = new TranslateAnimation(
                    0,
                    0,
                    0,
                    v.getHeight());
            animate.setDuration(MENU_DOWN_ANIMATION_DURATION);
            animate.setFillAfter(true);
            animate.setInterpolator(new LinearInterpolator());
            animate.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    /* do nothing */
                }
                @Override
                public void onAnimationEnd(final Animation animation) {
                    //初期menuと本体が重なった位置にくる。後ろに色があるのが見えてしまうため、透明にしとく
                    v.setAlpha(0);

                    //menuアニメーション停止通知
                    glView.getRenderer().reqMenuMove(MainRenderer.MenuMoveControl.STOP);

                    //アニメーションが終了すれば、受付可能にする
                    enable = true;
                }
                @Override
                public void onAnimationRepeat(Animation animation) {
                    /* do nothing */
                }
            });
            v.startAnimation(animate);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        Rect menu_corners = new Rect();
        Rect corners = new Rect();
        Point globalOffset = new Point();

        /* メニューの位置・サイズを渡す( @@四隅の位置取得の方法はその内調査(主にoffset)@@ ) */
        //メニュー上部
        LinearLayout menu_top = findViewById(R.id.bottom_menu_contents);
        menu_top.getGlobalVisibleRect(menu_corners);

        findViewById(R.id.container).getGlobalVisibleRect(corners, globalOffset);
        menu_corners.offset(-globalOffset.x, -globalOffset.y);

        MainRenderer render = glView.getRenderer();
        render.reqSetMenuSize(FluidMenuKind.CONTENTS, menu_corners.top, menu_corners.left, menu_corners.right, menu_corners.bottom, MENU_UP_ANIMATION_DURATION);

        //メニュー下部
        LinearLayout menu_bottom = findViewById(R.id.bottom_menu_init);
        menu_bottom.getGlobalVisibleRect(menu_corners);
        menu_corners.offset(-globalOffset.x, -globalOffset.y);

        render.reqSetMenuSize(FluidMenuKind.INIT, menu_corners.top, menu_corners.left, menu_corners.right, menu_corners.bottom, MENU_DOWN_ANIMATION_DURATION);

        //横幅を取得後、メニュー本体は隠す
        menu_top.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onResume(){
        super.onResume();
        glView.onResume();
    }

    @Override
    protected void onPause(){
        super.onPause();
        glView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        app.clearObj();
    }

}
