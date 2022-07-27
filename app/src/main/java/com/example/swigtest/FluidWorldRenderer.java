package com.example.swigtest;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.google.fpl.liquidfun.Body;
import com.google.fpl.liquidfun.BodyDef;
import com.google.fpl.liquidfun.BodyType;
import com.google.fpl.liquidfun.CircleShape;
import com.google.fpl.liquidfun.ParticleFlag;
import com.google.fpl.liquidfun.ParticleGroup;
import com.google.fpl.liquidfun.ParticleGroupDef;
import com.google.fpl.liquidfun.ParticleGroupFlag;
import com.google.fpl.liquidfun.ParticleSystem;
import com.google.fpl.liquidfun.ParticleSystemDef;
import com.google.fpl.liquidfun.PolygonShape;
import com.google.fpl.liquidfun.Vec2;
import com.google.fpl.liquidfun.World;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

/*
 *
 */
public class FluidWorldRenderer implements GLSurfaceView.Renderer, View.OnTouchListener {

    /* LiquidFun ロード */
    static {
        System.loadLibrary("liquidfun");
        System.loadLibrary("liquidfun_jni");
    }

    /* 物理関連 */
    private World mWorld = null;

    //パーティクル
    private ParticleSystem mParticleSystem;
    private ParticleData mParticleData;
    private RegenerationState mRegenerationState;
    private long mSetParticleFlg;
    private float mSetParticleRadius;
    private int mSetParticleLifetime;
    ParticleTouchData mParticleTouchData = new ParticleTouchData( -1, -1, ParticalTouchStatus.Extern, 0xFFFF, 0xFFFF );

    //静的物体
    private HashMap<Long, BodyData> mMapBodyData = new HashMap<Long, BodyData>();
    private HashMap<Long, BodyData> mMapTouchBodyData = new HashMap<Long, BodyData>();
    private long mBodyDataId = 1;

    private TouchObjectData mTouchObjectData = null;
    private boolean mTouchFlg = false;
    private int mShapeTranceCount = 0;

    private static final int SHAPE_TRANCE_VALUE = 1000;

    //フリック物体
    private FlickObjectData mFlickObjectData = null;
    private HashMap<Long, BodyData> mMapFlickBodyData = new HashMap<Long, BodyData>();
    private FlickControlFragment.FlickControl mFrickCtrlType;

    //バレット
    private boolean mCannonCtrl = false;
    private HashMap<Long, BodyData> mMapCannonData = new HashMap<Long, BodyData>();
    private ArrayList<Long> mBulletDeleteList = new ArrayList<Long>();
    private int mCannonCreateCycle;

    /* 位置管理 */
    private float[] mPositionMax;
    private float[] mPositionMid;
    private float[] mPositionMin;

    //Break生成位置の範囲
    private Vec2 rangeCreateBreakMin;
    private Vec2 rangeCreateBreakMax;
    private int rangeCreateBreakX;
    private int rangeCreateBreakY;

    private boolean touchActionDown;
    private Vec2 touchMovePos = new Vec2();
    private Vec2 touchDownPos = new Vec2();

    //menu本体ビューの端位置
    private float menuContentsTop;
    private float menuContentsLeft;
    private float menuContentsRight;
    private float menuContentsBottom;

    //menu初期ビューの端位置
    private float menuInitTop;
    private float menuInitLeft;
    private float menuInitRight;
    private float menuInitBottom;

    //menuアニメーション時間
    private int menuUpAniDuration;
    private int menuDownAniDuration;

    //変換後
    private float menuContentsPosY;
    private float menuInitPosY;

    private float menuPosX;
    private float menuPosY;
    private float menuWidth;
    private float menuHeight;


    private boolean mGetMenuCorner = false;

    //アニメーションと一致するmenuの移動速度
    private Vec2 mMenuUpVelocity;
    private Vec2 mMenuDownVelocity;
    private Vec2 mMenuVelocity;

    //menu移動制御
    private MenuMoveControl mMenuMove = MenuMoveControl.NOTHING;

    /* OpenGL */
    private MainGlView mMainGlView;
    private Bitmap mUserSelectBmp;
    private GLInitStatus glInitStatus = GLInitStatus.PreInit;

    private HashMap<Integer, Integer> mMapResIdToTextureId = new HashMap<Integer, Integer>();

    PlistDataManager mPlistManage;

    //描画対象のバッファ
    private ArrayList<Integer> drawGroupParIndex = new ArrayList<Integer>();    //頂点座標配列
    private ArrayList<Vec2> drawGroupUv = new ArrayList<Vec2>();                 //UV座標配列
    private int drawVerNum;                                                     //描画対象の頂点数

    /* 定数 */
    private static final float TIME_STEP = 1 / 60f; // 60 fps
    private static final int VELOCITY_ITERATIONS = 6;       //
    private static final int POSITION_ITERATIONS = 2;       //
    private static final int PARTICLE_ITERATIONS = 1;       //粒子シミュレーション 小さいほどやわらかくなる。5→固いゼリー

    private static final int FLICK_BOX_SIZE = 2;
    private static final int FLICK_BOX_HALF_SIZE = FLICK_BOX_SIZE / 2;
    private static final int FLICK_BOX_DOUBLE_SIZE = FLICK_BOX_SIZE * 2;

    private static final int CANNON_BULLET_SIZE = 1;
    private static final int CANNON_BULLET_HALF_SIZE = FLICK_BOX_SIZE / 2;
    private static final int CANNON_BULLET_DOUBLE_SIZE = FLICK_BOX_SIZE * 2;

    /* その他制御 */
    private boolean mMode = false;                     //createなら、trueに変える
    private MenuActivity.PictureButton mUserSelectHardness;
    Random mRundom;

    /* テスト用 */
    private boolean testflg = false;
    private int test_seq = 0;
    private Body testfrickbody = null;

    private float menuPosXtest;
    private float menuPosYtest;

    private Body menuBody;
    private Body touchBody;
    private Body overlapBody;

    //OpenGL 描画開始シーケンス
    enum GLInitStatus {
        PreInit,       //初期化前
        FinInit,       //初期化完了
        Drawable       //Draw開始
    }

    //パーティクル タッチ状態
    enum ParticalTouchStatus {
        //None,          //未タッチ
        Extern,        //粒子外
        Intern,        //粒子内
        Border,        //境界粒子
        Trace          //追随
    }

    //生成する物体の種別
    enum CreateObjectType {
        PLACEMENT,     //配置型
        FLICK,         //フリック型
        BULLET,        //大砲(弾)型
    }

    //パーティクル再生成シーケンス
    enum RegenerationState {
        DELETE,     //削除
        CREATE,     //生成
        OVERLAP,    //重複物体あり
        END,        //終了
    }

    //パーティクル再生成シーケンス
    enum MenuMoveControl {
        NOTHING,    //処理なし
        UP,         //表示（上方向）
        DOWN,       //非表示（下方向）
        WAIT,       //停止待ち
        STOP        //停止
    }

    //物体の種類
    enum BodyKind {
        STATIC,       //静的
        FLICK,        //フリック
        MOVE,         //移動
        TOUCH,        //タッチ
        OVERLAP       //重複
    }

    /*
     * コンストラクタ
     */
    public FluidWorldRenderer(MainGlView mainGlView, Bitmap bmp, MenuActivity.PictureButton select, ArrayList<Vec2> touchList) {
        mMainGlView = mainGlView;
        mUserSelectBmp = bmp;
        mUserSelectHardness = select;

        //!リファクタリング
        //bmp未指定の場合、Createモードとみなす
        if( bmp == null ){
            mMode = true;
        }

        //!リファクタリング
        if(select == MenuActivity.PictureButton.CreateDraw){
            mUserSelectHardness = MenuActivity.PictureButton.Soft;
            select = MenuActivity.PictureButton.Soft;
        }

        //選択された固さ(パーティクルの質)毎の設定
        if( select == MenuActivity.PictureButton.Soft ){
            mSetParticleFlg = ParticleFlag.elasticParticle;
            mSetParticleRadius = 0.2f;
            mSetParticleLifetime = 0;

        }else if( select == MenuActivity.PictureButton.Hard ){
            mSetParticleFlg = ParticleFlag.elasticParticle;
            mSetParticleRadius = 0.5f;
            mSetParticleLifetime = 0;

        }else if( select == MenuActivity.PictureButton.VeryHard ){
            mSetParticleFlg = ParticleFlag.elasticParticle;
            mSetParticleRadius = 1.0f;
            mSetParticleLifetime = 0;

        }else if( select == MenuActivity.PictureButton.Break ){
            mSetParticleFlg = ParticleFlag.waterParticle;
            mSetParticleRadius = 0.2f;
            mSetParticleLifetime = 6;
        }

        //物理世界生成
        mWorld = new World(0, -10);
        //ランダム生成
        mRundom = new Random();
        //plist管理クラス
        mPlistManage = new PlistDataManager();
    }

    /*
     *
     */
    private void addBodyData(Body body, float[] buffer, float[] uv, int drawMode, int textureId) {
        long id = mBodyDataId++;
        BodyData data = new BodyData(id, body, buffer, uv, drawMode, textureId);
        mMapBodyData.put(id, data);
    }

    /*
     *
     */
    private void addTouchBodyData(Body body, float[] buffer, float[] uv, int drawMode, int textureId) {
        long id = mBodyDataId++;
        BodyData data = new BodyData(id, body, buffer, uv, drawMode, textureId);
        mMapTouchBodyData.put(id, data);
    }


    /*
     *
     */
    private void addFlickBodyData(Body body, float[] buffer, float[] uv, int drawMode, int textureId) {
        long id = mBodyDataId++;
        BodyData data = new BodyData(id, body, buffer, uv, drawMode, textureId);
        mMapFlickBodyData.put(id, data);
    }

    /*
     *
     */
    private void addBulletBodyData(Body body, float[] buffer, float[] uv, int drawMode, int textureId) {
        long id = mBodyDataId++;
        BodyData data = new BodyData(id, body, buffer, uv, drawMode, textureId);
        mMapCannonData.put(id, data);
    }

    /*
     *
     */
    private void addParticleData(GL10 gl, ParticleSystem ps, ParticleGroup pg, float particleRadius, ArrayList<ArrayList<Integer>> row, ArrayList<Integer> border, int textureId) {
        mParticleData = new ParticleData(0, ps, pg, particleRadius, row, border, textureId);

        //Createのみ
        if(mMode){
            int texture;
            ArrayList<Integer> list = new ArrayList<Integer>();

            //遷移テクスチャ
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_0);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_1);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_2_1);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_2_2);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_2_3);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_3);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_4_1);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_4_2);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_4_3);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_4_4);
            list.add(texture);
            texture = makeTextureSoftCreate(gl, R.drawable.create_test_4_5);
            list.add(texture);

            //テクスチャを追加
            mParticleData.setTextureIdList(list);
        }

    }

    /*
     *
     */
    public Body addCircle(GL10 gl, float r, float x, float y, float angle, BodyType type, float density, int resId, CreateObjectType object) {
        // Box2d用
        BodyDef bodyDef = new BodyDef();
        bodyDef.setType(type);
        bodyDef.setPosition(x, y);
        bodyDef.setAngle(angle);
        Body body = mWorld.createBody(bodyDef);
        CircleShape shape = new CircleShape();
        shape.setRadius(r);
        body.createFixture(shape, density);

        // OpenGL用
        float vertices[] = new float[32*2];
        float uv[] = new float[32*2];
        for(int i = 0; i < 32; ++i){
            float a = ((float)Math.PI * 2.0f * i)/32;
            vertices[i*2]   = r * (float)Math.sin(a);
            vertices[i*2+1] = r * (float)Math.cos(a);

            uv[i*2]   = ((float)Math.sin(a) + 1.0f)/2f;
            uv[i*2+1] = (-1 * (float)Math.cos(a) + 1.0f)/2f;
        }

        //テクスチャ生成
        int textureId = makeTexture(gl, resId);

        switch (object){
            case PLACEMENT:
                addBodyData(body, vertices, uv, GL10.GL_TRIANGLE_FAN, textureId);
                break;
            case FLICK:
                addFlickBodyData(body, vertices, uv, GL10.GL_TRIANGLE_FAN, textureId);
                break;
            case BULLET:
                addBulletBodyData(body, vertices, uv, GL10.GL_TRIANGLE_FAN, textureId);
                break;
        }

        return body;
    }

    /*
     *
     */
    public Body addBox(GL10 gl,float hx, float hy, float x, float y, float angle, BodyType type, float density, int resId, BodyKind kind) {
        // Box2d用
        BodyDef bodyDef = new BodyDef();
        bodyDef.setType(type);
        bodyDef.setPosition(x, y);
        Body body = mWorld.createBody(bodyDef);
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(hx, hy, 0, 0, angle);    //para 3/4：ボックスの中心
        body.createFixture(shape, density);

        // OpenGL用
        float vertices[] = {
                - hx, + hy,
                - hx, - hy,
                + hx, + hy,
                + hx, - hy,
        };
        //FloatBuffer buffer = makeFloatBuffer(vertices);

        //画像の四隅
        float[] uv={
                0.0f,0.0f,//左上  ←UV座標の原点
                0.0f,1.0f,//左下
                1.0f,0.0f,//右上
                1.0f,1.0f,//右下
        };
        //FloatBuffer uvBuffer = makeFloatBuffer(uv);a

        //テクスチャ生成
        int textureId = makeTexture(gl, resId);

        //種別に応じて、保存先を切り分け
        if(kind == BodyKind.FLICK){
            addFlickBodyData(body, vertices, uv, GL10.GL_TRIANGLE_STRIP, textureId);

        }else if(kind == BodyKind.STATIC){
            addBodyData(body, vertices, uv, GL10.GL_TRIANGLE_STRIP, textureId);

        }else if(kind == BodyKind.MOVE){
            body.setGravityScale(0);
            menuBody = body;

            //debug
            bodyDef.setPosition(x-1, y);

            addBodyData(body, vertices, uv, GL10.GL_TRIANGLE_STRIP, textureId);

        }else if(kind == BodyKind.TOUCH){
            body.setGravityScale(0);
            touchBody = body;

            //debug
            bodyDef.setPosition(x-1, y);

            addTouchBodyData(body, vertices, uv, GL10.GL_TRIANGLE_STRIP, textureId);

        }else if(kind == BodyKind.OVERLAP){
            overlapBody = body;

        }

        return body;
    }


    /*
     *
     */
    public void addSoftBody(GL10 gl, float hx, float hy, float cx, float cy, float particleRadius,int resId) {
        ParticleSystem ps = mParticleSystem;

        Log.i("P Count", "before ps.getParticleCount=" + ps.getParticleCount());

        ParticleGroupDef pgd = new ParticleGroupDef();
        setParticleGroupDef(pgd, hx, hy, cx, cy);
        ParticleGroup pg = ps.createParticleGroup(pgd);
        //mPs.setMaxParticleCount(pg.getParticleCount());

        Log.i("P Count", "after pg.getParticleCount=" + pg.getParticleCount());
        Log.i("P Count", "after ps.getParticleCount=" + ps.getParticleCount());

        for(int i = 0; i < mParticleSystem.getParticleCount(); i++){
            //Log.i("test", "i=" + i + "\tposX=\t" + mPs.getParticlePositionX(i) + "\tposY=\t" + mPs.getParticlePositionY(i));
        }

        //粒子を行毎に配列で保持
        ArrayList<ArrayList<Integer>> row = new ArrayList<ArrayList<Integer>>();
        setParticleGroupRow(ps, pg, row);

        //生成した粒子の半径
        float diameter = particleRadius * 2;

        //横幅の一番長い行の粒子数を取得
        /*
        int col_max = 0;
        for( ArrayList<Integer> line: row ){
            if( line.size() > col_max ){
                col_max = line.size();
            }
        }
        */

        //UV座標用。座標間の間隔(UV座標の値は、この値の倍数となる)
        //float uv_offset_x = 1.0f / (col_max - 1);
        //float uv_offset_y = 1.0f / (row.size() - 1);

        //OpenGLに渡すために、三角形グルーピングバッファを生成
        //まず、下辺を基準に(下辺が底辺となるように)グルーピング
        int row_last_index = row.size() - 1;                         //ループ数=行数 - 1
        for( int row_index = 0; row_index < row_last_index; row_index++ ){

            //下辺と上辺（ある行とその上の行）
            ArrayList<Integer> bottom_line = row.get(row_index);
            ArrayList<Integer> upper_line = row.get(row_index + 1);

            //行の先頭に格納されている「パーティクルシステム側のIndex」
            int bottom_top_ref_index = bottom_line.get(0);
            int upper_top_ref_index = upper_line.get(0);

            //行の最後のIndex（こっちは、）
            int bottom_buf_end_index = bottom_line.size() - 1;
            int upper_buf_end_index = upper_line.size() - 1;
            for(int bottom_offset = 0; bottom_offset < bottom_buf_end_index; bottom_offset++){

                //参照するIndex（パーティクルシステム側のIndex）
                int ref_index = bottom_top_ref_index + bottom_offset;

                float bottom_left_x = ps.getParticlePositionX(ref_index);
                float bottom_right_x = ps.getParticlePositionX(ref_index + 1);

                //粒子が隣り合っていないなら、グルーピングしない(描画対象外)
                if( (bottom_right_x - bottom_left_x) > diameter ){
                    continue;
                }

                //上辺側に、三角形の頂点たりうる粒子があるかチェック(左からチェック)
                int upper_offset;
                float upper_x;
                int belongs_col = -1;
                int ref_upper_index = 0;
                for( upper_offset = 0; upper_offset <= upper_buf_end_index; upper_offset++){

                    //参照するIndex（パーティクルシステム側のIndex）
                    ref_upper_index = upper_top_ref_index + upper_offset;

                    upper_x = ps.getParticlePositionX(ref_upper_index);

                    //下辺の左側の頂点の直上にあるかチェック
                    if( upper_x == bottom_left_x ){
                        belongs_col = bottom_offset;
                        break;
                    }

                    //下辺の右側の頂点の直上にあるかチェック
                    if( upper_x == bottom_right_x ){
                        belongs_col = bottom_offset + 1;
                        break;
                    }
                }

                //頂点に適した粒子がないなら、グルーピングしない(描画対象外)
                if( belongs_col == -1 ){
                    continue;
                }

                //3頂点をバッファに格納
                drawGroupParIndex.add(ref_index);        //底辺-左
                drawGroupParIndex.add(ref_index + 1);    //底辺-右
                drawGroupParIndex.add(ref_upper_index);  //頂点
            }
        }

        //今度は、上側の行が底辺になるようにグルーピング
        for( int row_index = row_last_index; row_index > 0; row_index-- ){

            //下辺と上辺（ある行とその下の行）
            ArrayList<Integer> upper_line = row.get(row_index);
            ArrayList<Integer> bottom_line = row.get(row_index - 1);

            //List最後尾のIndex
            int upper_buf_end_index = upper_line.size() - 1;
            int bottom_buf_end_index = bottom_line.size() - 1;

            //行の終端に位置するパーティクルIndex
            int upper_end_ref_index = upper_line.get(upper_buf_end_index);
            int bottom_end_ref_index = bottom_line.get(bottom_buf_end_index);

            //行の右からチェックしていく
            for(int upper_offset = 0; upper_offset < upper_buf_end_index; upper_offset++){

                //参照するIndex（パーティクルシステム側のIndex）右からみていくため、減算していく。
                int ref_index = upper_end_ref_index - upper_offset;

                float upper_right_x = ps.getParticlePositionX(ref_index);
                float upper_left_x = ps.getParticlePositionX(ref_index - 1);

                //粒子が隣り合っていないなら、グルーピングしない(描画対象外)
                if( (upper_right_x - upper_left_x) > diameter ){
                    continue;
                }

                //下辺側に、三角形の頂点たりうる粒子があるかチェック(右からチェック)
                int bottom_offset;
                float bottom_x;
                int belongs_col = -1;
                int ref_bottom_index = 0;
                for( bottom_offset = 0; bottom_offset <= bottom_buf_end_index; bottom_offset++){

                    //参照するIndex（パーティクルシステム側のIndex）
                    ref_bottom_index = bottom_end_ref_index - bottom_offset;

                    bottom_x = ps.getParticlePositionX(ref_bottom_index);

                    //下辺の右側の頂点の直上にあるかチェック
                    if( bottom_x == upper_right_x ){
                        belongs_col = upper_buf_end_index - upper_offset;
                        break;
                    }

                    //下辺の左側の頂点の直上にあるかチェック
                    if( bottom_x == upper_left_x ){
                        belongs_col = upper_buf_end_index - (upper_offset - 1);
                        break;
                    }
                }

                //頂点に適した粒子がないなら、グルーピングしない(描画対象外)
                if( belongs_col == -1 ){
                    continue;
                }

                //3頂点をバッファに格納
                drawGroupParIndex.add(ref_index);
                drawGroupParIndex.add(ref_index - 1);
                drawGroupParIndex.add(ref_bottom_index);

                if( ref_index >= 126 && ref_index <=128 ){
                    Log.i("test", "drawGroupParIndex=\t" + ref_index);
                    Log.i("test", "drawGroupParIndex=\t" + (ref_index - 1));
                    Log.i("test", "drawGroupParIndex=\t" + ref_bottom_index);
                    Log.i("test", "------------------");
                }
            }
        }

        //頂点数を保持
        drawVerNum = drawGroupParIndex.size();

        //パーティクルグループ内の粒子で最小位置と最大位置を取得する
        float minParticleX = 0xFFFF;
        float maxParticleX = -(0xFFFF);
        float minParticleY = 0xFFFF;
        float maxParticleY = -(0xFFFF);
        int num = ps.getParticleCount();
        for( int i = 0; i < num; i++ ){
            //X座標
            float pos = ps.getParticlePositionX(i);
            minParticleX = ( pos < minParticleX ? pos: minParticleX );
            maxParticleX = ( pos > maxParticleX ? pos: maxParticleX );

            //Y座標
            pos = ps.getParticlePositionY(i);
            minParticleY = ( pos < minParticleY ? pos: minParticleY );
            maxParticleY = ( pos > maxParticleY ? pos: maxParticleY );
        }

        //最大幅と高さを算出
        float particleMaxWidth = Math.abs(maxParticleX - minParticleX);
        float particleMaxHeight = Math.abs(maxParticleY - minParticleY);

        //UV座標データ
        float minUvX      = mPlistManage.getUvMinX();
        float maxUvY      = mPlistManage.getUvMaxY();
        float UvMaxWidth  = mPlistManage.getUvWidth();
        float UvMaxHeight = mPlistManage.getUvHeight();

        //UV座標を計算し、バッファに保持する
        for(int i: drawGroupParIndex){
            float x = ps.getParticlePositionX(i);
            float y = ps.getParticlePositionY(i);

            float vecx = minUvX + ( (( x - minParticleX ) / particleMaxWidth)  * UvMaxWidth );
            float vecy = maxUvY - ( (( y - minParticleY ) / particleMaxHeight) * UvMaxHeight );

            drawGroupUv.add( new Vec2(vecx, vecy) );
        }

        //dbg
        for( int value: drawGroupParIndex ){
            //Log.i("test", "drawGroupParIndex=\t" + value);
        }
        for( Vec2 value: drawGroupUv ){
            //Log.i("test", "drawGroupUv=\t" + value.getX() + "\t" + value.getY());
        }
        //dbg

        //境界粒子を保持
        ArrayList<Integer> border = new ArrayList<Integer>();
        if(mUserSelectHardness != MenuActivity.PictureButton.Break) {
            ArrayList<Integer> line = new ArrayList<Integer>();

            //行数分ループ
            int line_num = row.size();
            for (int i = 0; i < line_num; i++) {
                line = row.get(i);

                //下辺・上辺
                if ((i == 0) || (i == line_num - 1)) {
                    //すべて境界粒子
                    for (int index : line) {
                        border.add(index);
                    }
                } else {
                    //端が境界粒子
                    border.add(line.get(0));                  //左端
                    border.add(line.get(line.size() - 1));    //右端
                }
            }
        }

        int textureId;

        if(mMode){
            //Create
            textureId = makeTextureSoftCreate(gl, R.drawable.create_test_0);
        }else{
            //Picture
            textureId = makeTextureSoft(gl, resId);
        }

        addParticleData(gl, ps, pg, particleRadius, row, border, textureId);
    }

    /*
     *
     */
    private void creParticleSystem(float particleRadius){
        ParticleSystemDef psd = new ParticleSystemDef();
        psd.setRadius(particleRadius);
        psd.setDampingStrength(0.2f);
        psd.setDensity(0.5f);
        psd.setGravityScale(0.4f);
        //psd.setGravityScale(2.0f);
        psd.setDestroyByAge(true);
        psd.setLifetimeGranularity(0.0001f);
        psd.setMaxCount(729);                     //0以外の値を設定する。
        //psd.setMaxCount(1458);
        //psd.setLifetimeGranularity();
        mParticleSystem = mWorld.createParticleSystem(psd);
    }

    /*
     * パーティクルグループ定義の設定
     * @para パーティクル横幅、パーティクル縦幅、生成位置(x/y)
     */
    private void setParticleGroupDef(ParticleGroupDef pgd, float hx, float hy, float cx, float cy){

        if(true){
            PolygonShape shape = new PolygonShape();
            shape.setAsBox(hx, hy, 0, 0, 0);
            pgd.setShape(shape);
        }else{
            //plistにある座標で図形を生成
            int shapenum = mPlistManage.setPlistBuffer(mMainGlView.getContext(), pgd, PlistDataManager.PLIST_KIND.PLIST_RABBIT);
            if(shapenum == -1){
                //取得エラーなら、終了
                return;
            }
        }

        pgd.setFlags(mSetParticleFlg);
        pgd.setGroupFlags(ParticleGroupFlag.solidParticleGroup);
        pgd.setPosition(cx, cy);
        pgd.setLifetime(mSetParticleLifetime);

        //!リファクタリング
        if( mUserSelectHardness == MenuActivity.PictureButton.Break ){
            //生成位置をランダムに
            int offsetx = mRundom.nextInt(rangeCreateBreakX);
            int offsety = mRundom.nextInt(rangeCreateBreakY);

            pgd.setPosition(rangeCreateBreakMin.getX() + offsetx, rangeCreateBreakMin.getY() + offsety);
        }
    }

    /*
     * 同一行のパーティクルによるバッファ生成
     * @para I:パーティクルシステム
     * @para I:パーティクルグループ
     * @para O:行配列
     */
    private void setParticleGroupRow(ParticleSystem ps, ParticleGroup pg, ArrayList<ArrayList<Integer>> row){
        //行ごとに保持
        float py = 0;
        ArrayList<Integer> line = new ArrayList<Integer>();
        for (int i = pg.getBufferIndex(); i < pg.getParticleCount() - pg.getBufferIndex(); ++i) {
            float y = ps.getParticlePositionY(i);
            if (i==0) {
                py = y;
            }

            //次の行に移ったかチェック
            if ((float)Math.abs(py - y) > 0.01f) {
                //行を新規にする
                row.add(line);
                line = new ArrayList<Integer>();
            }
            line.add(i);
            py = y;
        }
        row.add(line);
    }

    /*
     * 描画のため繰り返し呼ばれる
     * @param gl
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        //初期化完了していれば
        if (glInitStatus == GLInitStatus.FinInit) {
            //メニューのサイズが設定されるまで、処理なし
            if(!mGetMenuCorner && (mMode == false)){
                return;
            }

            //初期配置用の物理体生成
            createPhysicsObject(gl);

            //GL初期化状態を描画可能に更新
            glInitStatus = GLInitStatus.Drawable;

        } else if (glInitStatus == GLInitStatus.PreInit) {
            //初期化コール前なら何もしない(セーフティ)
            return;

        } else {
            //do nothing
        }

        long startTime = System.nanoTime();

        mWorld.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS, PARTICLE_ITERATIONS);

        //背景色を設定
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT
                | GL10.GL_DEPTH_BUFFER_BIT);

        //ビューの変換行列の作成
        gl.glMatrixMode(GL10.GL_MODELVIEW);   //マトリクス(4x4の変換行列)の指定
        gl.glLoadIdentity();                   //初期化

        //パーティクル情報
        ParticleSystem ps = mParticleSystem;
        ParticleData pd = mParticleData;
        ParticleGroup pg = pd.getParticleGroup();

        //パーティクル再生成
        regenerationParticle(gl, ps, pd, pg);

        //パーティクル描画更新
        updateParticleDraw(gl, ps, pd, pg);

        //移動物体の制御
        moveBodyControl(gl);

        //静的物体の描画更新
        //updateStaticBodyDraw(gl);

        //タッチ物体
        updateTouchBodyDraw(gl);

        //フリック物体の描画更新
        updateFlickBodyDraw(gl);

        //フリック物体の生成
        createFlickBody(gl);

        //フリック物体の生成
        createTouchBody(gl);

        //弾の管理
        bulletBodyManage(gl);

        long endTime  = System.nanoTime();
        //Log.d("TIME", "経過時間　" + ((endTime - startTime) / 1000000) + "(ms)");
    }

    /*
     * 物理体の生成
     */
    private void createPhysicsObject(GL10 gl){

        //画面の端の位置を変換(Y座標は上が0)
        mPositionMax = getWorldPoint(mMainGlView.getWidth(), 0, gl);
        mPositionMid = getWorldPoint(mMainGlView.getWidth() / 2, mMainGlView.getHeight() / 2, gl);
        mPositionMin = getWorldPoint(0, mMainGlView.getHeight(), gl);

        //Break生成位置の範囲
        float qtrX = (mPositionMax[0] - mPositionMin[0]) / 4;
        float qtrY = (mPositionMax[1] - mPositionMin[1]) / 4;

        rangeCreateBreakMin = new Vec2( mPositionMid[0] - qtrX, mPositionMid[1] - qtrY );
        rangeCreateBreakMax = new Vec2( mPositionMid[0] + qtrX, mPositionMid[1] + qtrY );

        rangeCreateBreakX = Math.round( rangeCreateBreakMax.getX() - rangeCreateBreakMin.getX() );
        rangeCreateBreakY = Math.round( rangeCreateBreakMax.getY() - rangeCreateBreakMin.getY() );


        /*  メニュー座標の変換処理 */
        //メニュー上部(本体)
        //四隅の座標を変換
        float[] worldMenuPosTopLeft = getWorldPoint(menuContentsLeft, menuContentsTop, gl);           //左上
        float[] worldMenuPosTopRight = getWorldPoint(menuContentsRight, menuContentsTop, gl);         //右上
        float[] worldMenuPosBottomRight = getWorldPoint(menuContentsRight, menuContentsBottom, gl);   //右下
        //大きさ( 半分にすると適切なサイズに調整させるのは、その内調査 )
        //物理体再生時には、物体の横幅・縦幅
        //画面上の位置情報としては、 メニュービューの半分のサイズ
        float width = (worldMenuPosTopRight[0] - worldMenuPosTopLeft[0]) / 2;
        float height = (worldMenuPosTopRight[1] - worldMenuPosBottomRight[1]) / 2;
        //位置
        float posX = worldMenuPosTopLeft[0] + width;            //中心
        float posY = worldMenuPosBottomRight[1] + height;       //中心

        //menu本体の位置
        menuContentsPosY = posY;

        float upper_menu_posY = worldMenuPosTopLeft[1];

        //addBox(gl, width, height, posX, posY, 0, BodyType.staticBody, 10, R.drawable.white, false);

        //メニュー下部(初期)
        //四隅の座標を変換
        worldMenuPosTopLeft = getWorldPoint(menuInitLeft, menuInitTop, gl);
        worldMenuPosTopRight = getWorldPoint(menuInitRight, menuInitTop, gl);
        worldMenuPosBottomRight = getWorldPoint(menuInitRight, menuInitBottom, gl);
        //大きさ( 半分にすると適切なサイズに調整させるのは、その内調査 )
        float width_ini = (worldMenuPosTopRight[0] - worldMenuPosTopLeft[0]) / 2;
        float height_ini = (worldMenuPosTopRight[1] - worldMenuPosBottomRight[1]) / 2;
        //位置
        posX = worldMenuPosTopLeft[0] + width_ini;
        posY = worldMenuPosBottomRight[1] + height_ini;

        //addBox(gl, width_ini, height_ini, posX, posY, 0, BodyType.staticBody, 10, R.drawable.white, false);

        //menu(▲)の位置
        menuInitPosY = posY;

        //menu背後の物体を生成（高さ = 本体 + 初期、位置 = 上部のみ初期位置と重なる形で配置）
        float half_of_total_height = height + height_ini;
        posY = worldMenuPosTopLeft[1] - half_of_total_height;
        addBox(gl, width, half_of_total_height, posX - 0, posY, 0, BodyType.staticBody, 11, R.drawable.white, BodyKind.MOVE);

        //位置情報を保持
        menuPosX = posX;
        menuPosY = posY;
        menuWidth = width;
        menuHeight = half_of_total_height;

        /* 表示時の速度を保持 */
        //上昇
        float millsecond = (float) menuUpAniDuration / 1000f;
        float ratioToSecond = 1.0f / millsecond;
        float speed = height * ratioToSecond * 1.32f;        //@1.32f の理由・妥当性はその内調査。
        mMenuUpVelocity = new Vec2(0, speed);

        //下降
        millsecond = (float) menuDownAniDuration / 1000f;
        ratioToSecond = 1.0f / millsecond;
        speed = height * ratioToSecond * 1.32f;         //@1.32f の理由・妥当性はその内調査。
        mMenuDownVelocity = new Vec2(0, -(speed));

        //Break以外
        if(mUserSelectHardness != MenuActivity.PictureButton.Break){
            addBox(gl, mPositionMax[0], 1, mPositionMid[0], mPositionMax[1], 0, BodyType.staticBody, 10, R.drawable.white, BodyKind.STATIC);   //上の壁
        }

        //システム定義と生成
        creParticleSystem(mSetParticleRadius);
        //パーティクル生成
        addSoftBody(gl, 4, 4, mPositionMid[0], mPositionMid[1], mSetParticleRadius, R.drawable.kitune_tanuki2);

        //壁
        addBox(gl, 1, mPositionMax[1], mPositionMin[0] - 1, mPositionMid[1], 0, BodyType.staticBody, 10, R.drawable.white, BodyKind.STATIC);          //左の壁
        addBox(gl, 1, mPositionMax[1], mPositionMax[0] + 1, mPositionMid[1], 0, BodyType.staticBody, 10, R.drawable.white, BodyKind.STATIC);   //右の壁

        /* 底(メニューの存在を考慮) */

        if(mMode){
            addBox(gl, mPositionMax[0], 1, mPositionMid[0], mPositionMin[1] - 1, 0, BodyType.staticBody, 10, R.drawable.white, BodyKind.STATIC);   //下の壁
        }else{
            //Picture
            //横幅・X座標位置
            float bottom_width = (worldMenuPosTopLeft[0] - mPositionMin[0]) / 2;
            float bottom_posX = mPositionMin[0] + bottom_width - 1;                  //メニュー物体とちょうどの位置だと下がるときうまくいかない時があるため、少し位置を左にする。

            addBox(gl, bottom_width, 1, bottom_posX, mPositionMin[1] - 1, 0, BodyType.staticBody, 10, R.drawable.white, BodyKind.STATIC);   //下の壁
        }



        return;
    }

    /*
     * パーティクルの再生成
     */
    private void regenerationParticle(GL10 gl, ParticleSystem ps, ParticleData pd, ParticleGroup pg) {

        //パーティクル再生成なし
        if (mRegenerationState == RegenerationState.END) {
            //処理なし
            return;

        //パーティクル削除
        }else if (mRegenerationState == RegenerationState.DELETE) {
            Log.i("test", "regeneration delete");

            //パーティクルグループを削除(粒子とグループは次の周期で削除される)
            pg.destroyParticles();
            drawGroupParIndex.clear();
            drawGroupUv.clear();

            //再生成のシーケンスを生成に更新(次の周期で生成するため)
            mRegenerationState = RegenerationState.CREATE;

        } else if (mRegenerationState == RegenerationState.CREATE) {

            //パーティクル生成
            addSoftBody(gl, 4, 4, mPositionMid[0], mPositionMid[1], mSetParticleRadius, R.drawable.kitune_tanuki2);

            //Break以外は再生成時に演出を入れる
            if( mUserSelectHardness != MenuActivity.PictureButton.Break ) {
                //パーティクルと重複する物体を生成(再生成演出用)
                addBox(gl, 4, 4, mPositionMid[0], mPositionMid[1], 0, BodyType.staticBody, 10, R.drawable.white, BodyKind.OVERLAP);
            }

            //再生成シーケンス終了。重複物体を生成した状態。
            mRegenerationState = RegenerationState.OVERLAP;

        } else if (mRegenerationState == RegenerationState.OVERLAP) {

            if( mUserSelectHardness != MenuActivity.PictureButton.Break ) {
                //重複物体はすぐに削除する
                mWorld.destroyBody(overlapBody);
            }

            mRegenerationState = RegenerationState.END;
        } else {
            /* 処理なし */
        }
    }

    /*
     * パーティクルの描画情報の更新(頂点バッファ/UVバッファ)
     */
    private void updateParticleDraw(GL10 gl, ParticleSystem ps, ParticleData pd, ParticleGroup pg) {
        /* 粒子がない場合、何もしない */
        if (pg.getParticleCount() == 0) {
            //Breakなら再生成
            if (mUserSelectHardness == MenuActivity.PictureButton.Break) {
                mRegenerationState = RegenerationState.CREATE;
            }

            return;
        }

        //Create用
        if(mMode){
            updateParticleCreateDraw(gl, ps, pd);
            return;
        }

        /* パーティクルの描画更新 */
        if (mUserSelectHardness == MenuActivity.PictureButton.Break) {
            updateParticleBreakDraw(gl, ps, pd);
        }else{
            updateParticleUnBreakDraw(gl, ps, pd);
        }
    }

    /*
     * パーティクル描画更新（Break）
     */
    private void updateParticleBreakDraw(GL10 gl, ParticleSystem ps, ParticleData pd ) {
        //マトリクス記憶
        gl.glPushMatrix();
        {
            ArrayList<ArrayList<Integer>> row = pd.getRow();
            float radiusReal = pd.getParticleRadiusReal();

            //行数 - 1
            int row_size = row.size();
            for (int i = 0; i < row_size; ++i) {
                ArrayList<Integer> col = row.get(i);
                int col_size = col.size();
                //UV座標指定時の差分
                float dy = 1.0f / (row_size);                 //粒子間のy座標の高さ(割合)
                float dx = 1.0f / (col_size);                 //粒子間のx座標の幅(割合)

                //行の粒子数 - 1
                for (int j = 0; j < col_size; ++j) {
                    //四角形単位で描画
                    //②　  ④
                    //   ●      ←ある粒子の四隅
                    //①　  ③
                    float positionX = ps.getParticlePositionX(col.get(j));
                    float positionY = ps.getParticlePositionY(col.get(j));
                    float vertices[] = {
                            positionX - radiusReal, positionY - radiusReal,                       //①
                            positionX - radiusReal, positionY + radiusReal,                       //②
                            positionX + radiusReal, positionY - radiusReal,                       //③
                            positionX + radiusReal, positionY + radiusReal,                       //④
                    };
                    float[] uv = {
                            j * dx, 1 - i * dy,             //左上  ←間違い？  左下
                            j * dx, 1 - (i + 1) * dy,       //左下             左上
                            (j + 1) * dx, 1 - i * dy,       //右上             右下
                            (j + 1) * dx, 1 - (i + 1) * dy, //右下             右上
                    };
                    FloatBuffer vertexBuffer = makeFloatBuffer(vertices);
                    FloatBuffer uvBuffer = makeFloatBuffer(uv);

                    //テクスチャの指定
                    gl.glActiveTexture(GL10.GL_TEXTURE0);
                    gl.glBindTexture(GL10.GL_TEXTURE_2D, pd.getTextureId());

                    //UVバッファの指定
                    gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, uvBuffer);                 //UV座標を渡す
                    //頂点バッファの指定
                    gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vertexBuffer);              //頂点座標を渡す
                    //描画（連続する3つの頂点で塗り潰す(p1-p2-p3で塗り潰す、p2-p3-p4で塗り潰す…)、頂点数）
                    gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, vertices.length / 2);
                }
            }
        }

        //マトリクスを戻す
        gl.glPopMatrix();
    }

    /*
     * パーティクル描画更新（Break以外）
     */
    private void updateParticleUnBreakDraw(GL10 gl, ParticleSystem ps, ParticleData pd ) {
        //マトリクス記憶
        gl.glPushMatrix();
        {
            //Index毎に現在の位置情報を取得・配列に格納
            float vertices[];
            vertices = new float[drawVerNum * 2];

            int count = 0;
            for( int index: drawGroupParIndex ){
                vertices[count] = ps.getParticlePositionX(index);
                count++;
                vertices[count] = ps.getParticlePositionY(index);
                count++;
            }

            //UV座標配列
            float uv[];
            uv = new float[drawVerNum * 2];

            count = 0;
            for( Vec2 Coordinate: drawGroupUv ){
                uv[count] = Coordinate.getX();
                count++;
                uv[count] = Coordinate.getY();
                count++;
            }

            FloatBuffer vertexBuffer = makeFloatBuffer(vertices);
            FloatBuffer uvBuffer = makeFloatBuffer(uv);

            //テクスチャの指定
            gl.glActiveTexture(GL10.GL_TEXTURE0);
            gl.glBindTexture(GL10.GL_TEXTURE_2D, pd.getTextureId());

            //UVバッファの指定
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, uvBuffer);                 //UV座標を渡す
            //頂点バッファの指定
            gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vertexBuffer);              //頂点座標を渡す
            //描画
            gl.glDrawArrays(GL10.GL_TRIANGLES, 0, drawVerNum);
        }
        //マトリクスを戻す
        gl.glPopMatrix();

        //タッチ判定処理
        particleTouchProcess(gl, ps, pd);
    }

    /*
     * パーティクル描画更新（Break以外）
     */
    private void updateParticleCreateDraw(GL10 gl, ParticleSystem ps, ParticleData pd ) {
        //マトリクス記憶
        gl.glPushMatrix();
        {
            //Index毎に現在の位置情報を取得・配列に格納
            float vertices[];
            vertices = new float[drawVerNum * 2];

            int count = 0;
            for( int index: drawGroupParIndex ){
                vertices[count] = ps.getParticlePositionX(index);
                count++;
                vertices[count] = ps.getParticlePositionY(index);
                count++;
            }

            //UV座標配列
            float uv[];
            uv = new float[drawVerNum * 2];

            count = 0;
            for( Vec2 Coordinate: drawGroupUv ){
                uv[count] = Coordinate.getX();
                count++;
                uv[count] = Coordinate.getY();
                count++;
            }

            FloatBuffer vertexBuffer = makeFloatBuffer(vertices);
            FloatBuffer uvBuffer = makeFloatBuffer(uv);

            int textureId = getDrawProcesstexture(pd);

            //テクスチャの指定
            gl.glActiveTexture(GL10.GL_TEXTURE0);
            gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);

            //UVバッファの指定
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, uvBuffer);                 //UV座標を渡す
            //頂点バッファの指定
            gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vertexBuffer);              //頂点座標を渡す
            //描画
            gl.glDrawArrays(GL10.GL_TRIANGLES, 0, drawVerNum);
        }
        //マトリクスを戻す
        gl.glPopMatrix();

        //タッチ判定処理
        particleTouchProcess(gl, ps, pd);
    }

    private int getDrawProcesstexture(ParticleData pd){

        ArrayList<Integer> list = pd.getTextureIdList();
        int textureId;

        if( mShapeTranceCount < SHAPE_TRANCE_VALUE ){
            textureId = list.get(0);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 2 ){
            textureId = list.get(1);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 3){
            textureId = list.get(2);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 4){
            textureId = list.get(3);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 5){
            textureId = list.get(4);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 6){
            textureId = list.get(5);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 7){
            textureId = list.get(6);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 8){
            textureId = list.get(7);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 9){
            textureId = list.get(8);
        }else if(mShapeTranceCount < SHAPE_TRANCE_VALUE * 10){
            textureId = list.get(9);
        }else{
            textureId = list.get(10);
        }

        //形状変化のカウンタを更新
        if( mShapeTranceCount < SHAPE_TRANCE_VALUE * 10 ){
            mShapeTranceCount++;
        }

        return textureId;
    }

    /*
     * 移動物体の制御
     */
    private void moveBodyControl(GL10 gl) {

        //制御情報に応じて処理実施
        if( mMenuMove == MenuMoveControl.NOTHING )
        {
            //do nothing
        }
        else if(mMenuMove == MenuMoveControl.STOP)
        {
            Log.i("test", "STOP");

            //物体を停止
            menuBody.setType(BodyType.staticBody);

            //微妙なズレの蓄積を防ぐため、初期位置に移動完了したタイミングで、物体を再生成
            if(menuInitPosY > menuBody.getPositionY()){
                mWorld.destroyBody(menuBody);
                addBox(gl, menuWidth, menuHeight, menuPosX - 0, menuPosY, 0, BodyType.staticBody, 11, R.drawable.white, BodyKind.MOVE);
            }

            //移動処理終了
            mMenuMove = MenuMoveControl.NOTHING;

            Log.i("test", "STOP2");
        }
        else if(mMenuMove == MenuMoveControl.WAIT)
        {
            Log.i("test", "WAIT");

            //停止要求がくるまで、速度を維持し続ける
            menuBody.setLinearVelocity(mMenuVelocity);

            Log.i("test", "WAIT2");
        }
        else
        {
            Log.i("test", "MOVE");

            //移動開始
            menuBody.setType(BodyType.dynamicBody);
            menuBody.setLinearVelocity(mMenuVelocity);

            //移動状態更新(停止要求待つ)
            mMenuMove = MenuMoveControl.WAIT;

            Log.i("test", "MOVE2");
        }
    }

    /*
     * 静的物体の描画更新(頂点バッファ/UVバッファ)
     */
    private void updateStaticBodyDraw(GL10 gl) {

        for(Long key: mMapBodyData.keySet()) {
            gl.glPushMatrix();
            {
                BodyData bd = mMapBodyData.get(key);
                gl.glTranslatef(bd.getBody().getPositionX(), bd.getBody().getPositionY(), 0);
                float angle = (float)Math.toDegrees(bd.getBody().getAngle());
                gl.glRotatef(angle , 0, 0, 1);

                //テクスチャの指定
                gl.glActiveTexture(GL10.GL_TEXTURE0);
                gl.glBindTexture(GL10.GL_TEXTURE_2D, bd.getTextureId());

                //UVバッファの指定
                gl.glTexCoordPointer(2,GL10.GL_FLOAT,0, bd.getUvBuffer());              //確保したメモリをOpenGLに渡す

                FloatBuffer buff = bd.getVertexBuffer();
                gl.glVertexPointer(2, GL10.GL_FLOAT, 0, buff);
                gl.glDrawArrays(bd.getDrawMode(), 0, bd.getVertexLen());

            }
            gl.glPopMatrix();
        }
    }

    /*
     * タッチ物体の描画更新(頂点バッファ/UVバッファ)
     */
    private void updateTouchBodyDraw(GL10 gl) {

        //create以外なら、何もしない
        if( !mMode ){
            return;
        }

        for(Long key: mMapTouchBodyData.keySet()) {
            BodyData bd = mMapTouchBodyData.get(key);

            /*
            gl.glPushMatrix();
            {
                gl.glTranslatef(bd.getBody().getPositionX(), bd.getBody().getPositionY(), 0);
                float angle = (float)Math.toDegrees(bd.getBody().getAngle());
                gl.glRotatef(angle , 0, 0, 1);

                //テクスチャの指定
                gl.glActiveTexture(GL10.GL_TEXTURE0);
                gl.glBindTexture(GL10.GL_TEXTURE_2D, bd.getTextureId());

                //UVバッファの指定
                gl.glTexCoordPointer(2,GL10.GL_FLOAT,0, bd.getUvBuffer());              //確保したメモリをOpenGLに渡す

                FloatBuffer buff = bd.getVertexBuffer();
                gl.glVertexPointer(2, GL10.GL_FLOAT, 0, buff);
                gl.glDrawArrays(bd.getDrawMode(), 0, bd.getVertexLen());
            }
            gl.glPopMatrix();
            */
            //物体の移動
            Body body = bd.getBody();
            float[] touch = getWorldPoint(touchMovePos.getX(), touchMovePos.getY(), gl);
            Vec2 pos = new Vec2(touch[0], touch[1]);
            body.setTransform(pos, 0);
        }

    }

    /*
     * フリック物体の描画更新(頂点バッファ/UVバッファ)
     */
    private void updateFlickBodyDraw(GL10 gl) {

        for(Long key: mMapFlickBodyData.keySet()) {
            BodyData bd = mMapFlickBodyData.get(key);

            gl.glPushMatrix();
            {
                gl.glTranslatef(bd.getBody().getPositionX(), bd.getBody().getPositionY(), 0);
                float angle = (float)Math.toDegrees(bd.getBody().getAngle());
                gl.glRotatef(angle , 0, 0, 1);

                //テクスチャの指定
                gl.glActiveTexture(GL10.GL_TEXTURE0);
                gl.glBindTexture(GL10.GL_TEXTURE_2D, bd.getTextureId());

                //UVバッファの指定
                gl.glTexCoordPointer(2,GL10.GL_FLOAT,0, bd.getUvBuffer());              //確保したメモリをOpenGLに渡す

                FloatBuffer buff = bd.getVertexBuffer();
                gl.glVertexPointer(2, GL10.GL_FLOAT, 0, buff);
                gl.glDrawArrays(bd.getDrawMode(), 0, bd.getVertexLen());
            }
            gl.glPopMatrix();

            //下方向に移動したタイミングで停止する
            Body body = bd.getBody();
            if( body.getLinearVelocity().getY() < -3 ){
                body.setAwake(false);   //BodyType.staticBody ではなく、この設定をすることで、止まっていても衝突したときに移動してくれる
            }

            //タッチされたなら、フリック物体へのタッチ判定
            if(touchActionDown){
                float bodyPosX = body.getPositionX();
                float bodyPosY = body.getPositionY();
                float[] touch = getWorldPoint(touchDownPos.getX(), touchDownPos.getY(), gl);

                //物体(の内部半分の範囲)にタッチ
                if( ( touch[0] < bodyPosX + FLICK_BOX_SIZE ) &&
                        ( touch[0] > bodyPosX - FLICK_BOX_SIZE ) &&
                        ( touch[1] < bodyPosY + FLICK_BOX_SIZE ) &&
                        ( touch[1] > bodyPosY - FLICK_BOX_SIZE ) ){
                    bd.touched = true;

                    //見つからなければ、未タッチ状態にする
                    touchActionDown = false;
                }
            }

            //タッチ中の物体に対する操作
            if( bd.touched ){

                if(mFrickCtrlType == FlickControlFragment.FlickControl.DELETE){
                    //削除受付中なら、削除する

                }else if(mFrickCtrlType == FlickControlFragment.FlickControl.FIX){
                    //停止受付中なら、完全に物理演算を停止させる
                    body.setType(BodyType.staticBody);
                } else{
                    //固定解除 or 要求なしの場合
                    body.setType(BodyType.dynamicBody);

                    //物体の移動
                    float[] touch = getWorldPoint(touchMovePos.getX(), touchMovePos.getY(), gl);
                    Vec2 pos = new Vec2(touch[0], touch[1]);
                    body.setTransform(pos, 0);
                }
            }
        }
    }

    /*
     * フリック物体生成
     */
    private void createFlickBody(GL10 gl) {
        //フリック物体が登録されていた場合
        if(mFlickObjectData != null){
            float pos[] = getWorldPoint(mFlickObjectData.posX, mFlickObjectData.posY, gl);

            //形状
            Body body;
            int addition_force;
            if(mFlickObjectData.shape == FlickFigureFragment.FlickShape.BOX){
                body = addBox(gl, FLICK_BOX_SIZE, FLICK_BOX_SIZE, pos[0], mPositionMin[1] + FLICK_BOX_DOUBLE_SIZE, 0, BodyType.dynamicBody, 10, R.drawable.white, BodyKind.FLICK);
                addition_force = 600;
            } else if (mFlickObjectData.shape == FlickFigureFragment.FlickShape.TRIANGLE) {
                body = addBox(gl, FLICK_BOX_SIZE, FLICK_BOX_SIZE, pos[0], mPositionMin[1] + FLICK_BOX_DOUBLE_SIZE, 0, BodyType.dynamicBody, 10, R.drawable.white, BodyKind.FLICK);
                addition_force = 600;
            } else if (mFlickObjectData.shape == FlickFigureFragment.FlickShape.CIRCLE) {
                body = addCircle(gl, FLICK_BOX_SIZE, pos[0], mPositionMin[1] + FLICK_BOX_DOUBLE_SIZE,  0, BodyType.dynamicBody,  0, R.drawable.white, CreateObjectType.FLICK);
                addition_force = 5;
            } else{
                body = addBox(gl, FLICK_BOX_SIZE, FLICK_BOX_SIZE, pos[0], mPositionMin[1] + FLICK_BOX_DOUBLE_SIZE, 0, BodyType.dynamicBody, 10, R.drawable.white, BodyKind.FLICK);
                addition_force = 600;
            }

            Vec2 force = new Vec2(mFlickObjectData.deltaX * addition_force, mFlickObjectData.deltaY * addition_force);
            body.applyForceToCenter(force,true);
            body.setGravityScale(2.0f);

            mFlickObjectData = null;
        }
    }

    /*
     * タッチ物体生成
     */
    private void createTouchBody(GL10 gl) {
        //タッチ物体が登録されていた場合
        if(mTouchObjectData != null && mTouchFlg == true){
            float pos[] = getWorldPoint(mTouchObjectData.posX, mTouchObjectData.posY, gl);

            //形状
            Body body = addBox(gl, FLICK_BOX_SIZE, FLICK_BOX_SIZE, pos[0], pos[1] + FLICK_BOX_DOUBLE_SIZE, 0, BodyType.staticBody, 10, R.drawable.white, BodyKind.TOUCH);

            mTouchFlg = false;
        }
    }

    /*
     *  弾の管理(生成・削除)
     */
    private void bulletBodyManage(GL10 gl) {
        //大砲が有効なら、弾の生成と発射
        if(mCannonCtrl){

            //弾の生成(個/s)
            mCannonCreateCycle++;
            if((mCannonCreateCycle % 12) == 0){
                //形状
                Body body;
                body = addCircle(gl, CANNON_BULLET_SIZE, mPositionMid[0], mPositionMin[1] + CANNON_BULLET_DOUBLE_SIZE,  0, BodyType.dynamicBody,  0, R.drawable.white, CreateObjectType.BULLET);

                //上方向に発射
                Vec2 force = new Vec2(0, 10000);
                body.applyForceToCenter(force,true);
                body.setGravityScale(2.0f);

                mCannonCreateCycle = 0;
            }

            //発射済みの弾の描画
            for (Long key : mMapCannonData.keySet()) {
                BodyData bd = mMapCannonData.get(key);

                gl.glPushMatrix();
                {
                    gl.glTranslatef(bd.getBody().getPositionX(), bd.getBody().getPositionY(), 0);
                    float angle = (float)Math.toDegrees(bd.getBody().getAngle());
                    gl.glRotatef(angle , 0, 0, 1);

                    //テクスチャの指定
                    gl.glActiveTexture(GL10.GL_TEXTURE0);
                    gl.glBindTexture(GL10.GL_TEXTURE_2D, bd.getTextureId());

                    //UVバッファ
                    gl.glTexCoordPointer(2, GL10.GL_FLOAT,0, bd.getUvBuffer());              //確保したメモリをOpenGLに渡す

                    //頂点バッファ
                    FloatBuffer buff = bd.getVertexBuffer();
                    gl.glVertexPointer(2, GL10.GL_FLOAT, 0, buff);

                    gl.glDrawArrays(bd.getDrawMode(), 0, bd.getVertexLen());

                }
                gl.glPopMatrix();

                //下方向に移動したタイミングで削除
                Body body = bd.getBody();
                Log.i("test", "velo X=" + body.getLinearVelocity().getX() + " : velo Y=" + body.getLinearVelocity().getY());
                if( body.getLinearVelocity().getY() < 0 ||  Math.abs(body.getLinearVelocity().getX()) > 15 ){
                    mBulletDeleteList.add(key);
                }
            }

            //削除対象とした弾を削除
            for(int i = 0; i < mBulletDeleteList.size(); i++){
                long key = mBulletDeleteList.get(i);
                BodyData bd = mMapCannonData.get(key);
                mWorld.destroyBody(bd.getBody());
                bd.getBody().delete();
                mMapCannonData.remove(key);
            }
            mBulletDeleteList.clear();
        }
    }

    /*
     * 画面座標を物理座標に変換する
     */
    private float[] getWorldPoint(float mx, float my, GL10 gl) {

        GL11 gl11 = (GL11)gl;
        int[] bits = new int[16];
        float[] model = new float[16];
        float[] proj = new float[16];
        gl11.glGetIntegerv(gl11.GL_MODELVIEW_MATRIX_FLOAT_AS_INT_BITS_OES, bits, 0);
        for(int i = 0; i < bits.length; i++){
            model[i] = Float.intBitsToFloat(bits[i]);
        }
        gl11.glGetIntegerv(gl11.GL_PROJECTION_MATRIX_FLOAT_AS_INT_BITS_OES, bits, 0);
        for(int i = 0; i < bits.length; i++){
            proj[i] = Float.intBitsToFloat(bits[i]);
        }

        float[] ret = new float[4];
        GLU.gluUnProject(
                mx, (float)mMainGlView.getHeight()-my, 1f,
                model, 0, proj, 0,
                new int[]{0, 0, mMainGlView.getWidth(), mMainGlView.getHeight()}, 0,
                ret, 0);
        float x = (float)(ret[0] / ret[3]);
        float y = (float)(ret[1] / ret[3]);
        //float z = (float)(ret[2] / ret[3]);

        float position[] = new float[2];
        position[0] = x;
        position[1] = y;

        return position;
    }

    /*
     * 主に landscape と portraid の切り替え (縦向き、横向き切り替え) のときに呼ばれる
     * @param gl
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        //参考
        //端末縦：1080/2042
        //端末横：2280/861

        //画面の範囲を指定
        gl.glViewport(0, 0, width, height);

        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        GLU.gluPerspective(gl,                         //画面のパースペクティブを登録し、どの端末でも同じように描画できるよう設定
                60f,                                //縦の視野角を”度”単位で設定
                (float) width / height,           //縦に対する横方向の視野角の倍率
                1f,                                //一番近いZ位置を指定
                50f);                               //一番遠いZ位置を指定

        GLU.gluLookAt(gl,                             //カメラの位置・姿勢を決定する
                0, 15, 50,            // カメラの位置
                0, 15, 0,       // カメラの注視点
                0, 1, 0                 // カメラの上方向
        );
    }

    //レンダークラスの初期化時に呼ばれる
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        //gl.glEnable(GL10.GL_DEPTH_TEST);
        //gl.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);       //背景色を指定して背景を描画    青
        gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);       //背景色を指定して背景を描画
        gl.glEnable(GL10.GL_LIGHTING);                                //ライティングを有効化
        gl.glEnable(GL10.GL_LIGHT0);                                   //光源の指定。GL_LIGHT0 から GL_LIGHT7 番までの8つの光源がある。
        gl.glDepthFunc(GL10.GL_LEQUAL);                                //深度値と深度バッファの震度を比較する関数の指定。GL_LEQUALは入って来る深度値が格納された深度値以下である時に通過
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);                 //頂点座標のバッファをセットしたことをOpenGLに伝える
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);         //テクスチャのマッピング座標のバッファをセットしたことをOpenGLに伝える

        //テクスチャの有効化
        gl.glEnable(GL10.GL_TEXTURE_2D);                              //テクスチャの利用を有効にする
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL10.GL_BLEND);

        //ステータス更新
        glInitStatus = GLInitStatus.FinInit;
    }

    /*
     * テクスチャの生成
     * テクスチャは引数にて指定する。
     */
    private int makeTexture(GL10 gl10, int resId) {
        Integer texId = mMapResIdToTextureId.get(resId);
        if (texId != null) {
            return  texId;
        }

        //リソースIDから、Bitmapオブジェクトを生成
        Resources r = mMainGlView.getContext().getResources();
        Bitmap bmp= BitmapFactory.decodeResource(r, resId);

        //テクスチャのメモリ確保
        int[] textureIds=new int[1];                  //テクスチャは一つ
        gl10.glGenTextures(1, textureIds, 0);   //テクスチャオブジェクトの生成。para2にIDが納められる。

        //テクスチャへのビットマップ指定
        gl10.glActiveTexture(GL10.GL_TEXTURE0);                                     //テクスチャユニットを選択
        gl10.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[0]);                      //テクスチャIDとGL_TEXTURE_2Dをバインド
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bmp, 0);            //バインドされたテクスチャにBitmapをセットする

        //テクスチャのフィルタ指定
        gl10.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl10.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);

        //リソースIDとテクスチャIDを保持
        mMapResIdToTextureId.put(resId, textureIds[0]);
        return textureIds[0];
    }

    /*
     * テクスチャの生成(パーティクル用)
     * テクスチャは、引数ではなく画面遷移時に指定されたBitmapを対象にする。
     */
    private int makeTextureSoft(GL10 gl10, int resId) {
        Integer texId = mMapResIdToTextureId.get(resId);
        if (texId != null) {
            return  texId;
        }

        //テクスチャのメモリ確保
        int[] textureIds=new int[1];                  //テクスチャは一つ
        gl10.glGenTextures(1, textureIds, 0);   //テクスチャオブジェクトの生成。para2にIDが納められる。

        //テクスチャへのビットマップ指定
        gl10.glActiveTexture(GL10.GL_TEXTURE0);                                     //テクスチャユニットを選択
        gl10.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[0]);                      //テクスチャIDとGL_TEXTURE_2Dをバインド
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, mUserSelectBmp, 0);    //バインドされたテクスチャにBitmapをセットする

        //テクスチャのフィルタ指定
        gl10.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl10.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);

        //リソースIDとテクスチャIDを保持
        mMapResIdToTextureId.put(resId, textureIds[0]);
        return textureIds[0];
    }

    /*
     * テクスチャの生成(パーティクル用)
     * テクスチャは、引数ではなく画面遷移時に指定されたBitmapを対象にする。
     */
    private int makeTextureSoftCreate(GL10 gl10, int resId) {
        Integer texId = mMapResIdToTextureId.get(resId);
        if (texId != null) {
            return  texId;
        }

        //リソースIDから、Bitmapオブジェクトを生成
        Resources r = mMainGlView.getContext().getResources();
        Bitmap bmp= BitmapFactory.decodeResource(r, resId);

        //テクスチャのメモリ確保
        int[] textureIds=new int[1];                  //テクスチャは一つ
        gl10.glGenTextures(1, textureIds, 0);   //テクスチャオブジェクトの生成。para2にIDが納められる。

        //テクスチャへのビットマップ指定
        gl10.glActiveTexture(GL10.GL_TEXTURE0);                                     //テクスチャユニットを選択
        gl10.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[0]);                      //テクスチャIDとGL_TEXTURE_2Dをバインド
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bmp, 0);            //バインドされたテクスチャにBitmapをセットする

        //テクスチャのフィルタ指定
        gl10.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl10.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);

        //リソースIDとテクスチャIDを保持
        mMapResIdToTextureId.put(resId, textureIds[0]);
        return textureIds[0];
    }

    /*
     * float配列をFloatBufferに変換
     */
    public static FloatBuffer makeFloatBuffer(float[] array) {
        FloatBuffer fb= ByteBuffer.allocateDirect(array.length * 4).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(array).position(0);
        return fb;
    }



    /*
     * タッチコールバック
     */
    public synchronized boolean onTouch(View v, MotionEvent event) {

        //!リファクタリング。同じ情報を複数で持っているため、一括管理したい。

        switch( event.getAction() ) {
            //タッチ開始
            case MotionEvent.ACTION_DOWN:
                //フリック物体用：タッチ判定
                touchActionDown = true;
                touchDownPos.set(event.getX(), event.getY());
                touchMovePos.set(event.getX(), event.getY());

                //createのみ
                if(mMode){
                    reqEntryTouchObject(event.getX(), event.getY());
                }

                Log.i("test", "touch getX=" + event.getX());
                Log.i("test", "touch getY=" + event.getY());

                break;

            //タッチ解除
            case MotionEvent.ACTION_UP:
                //粒子用：状態更新
                mParticleTouchData.setStatus(ParticalTouchStatus.Extern);
                mParticleTouchData.setBorderIndex(-1);
                mParticleTouchData.setFollowingIndex(-1);
                mParticleTouchData.setTouchPosX(0xFFFF);
                mParticleTouchData.setTouchPosY(0xFFFF);

                //フリック物体用：タッチ状態クリア
                touchActionDown = false;
                flickBodyTouchClear();

                //createのみ
                if(mMode){
                    touchBodyTouchClear();
                    mTouchObjectData = null;
                }

                break;

            //タッチ移動
            case MotionEvent.ACTION_MOVE:
                //粒子用：タッチ中の位置を更新
                mParticleTouchData.setTouchPosX(event.getX());
                mParticleTouchData.setTouchPosY(event.getY());

                //タッチ中の位置を保持
                touchMovePos.set(event.getX(), event.getY());

                break;

            default:
                break;
        }

        return true;
    }

    /*
     * フリック物体：タッチ状態クリア
     */
    private void flickBodyTouchClear() {

        for(Long key: mMapFlickBodyData.keySet()) {
            BodyData bd = mMapFlickBodyData.get(key);

            //一つしかタッチできないため、見つかれば終了
            if( bd.touched ){
                bd.touched = false;
                return;
            }
        }
    }

    /*
     * タッチ物体：タッチ状態クリア
     */
    private void touchBodyTouchClear() {

        //物体を削除
        for(Long key: mMapTouchBodyData.keySet()) {
            BodyData bd = mMapTouchBodyData.get(key);
            mWorld.destroyBody(bd.getBody());
        }

        //mapクリア
        mMapTouchBodyData.clear();
    }

    /*
     * 粒子に対するタッチ状態を確認
     */
    private void particleTouchProcess(GL10 gl, ParticleSystem ps, ParticleData pd){

        if( mParticleTouchData.touchPosX == 0xFFFF){
            //未タッチなら処理なし
            return;
        }

        //判定前は粒子の外側
        ParticalTouchStatus status = ParticalTouchStatus.Extern;

        //タッチ位置が、粒子外部 or 粒子内部 or 境界粒子 かを判定
        ParticleGroup pg = pd.getParticleGroup();
        float radius = pd.getParticleRadius();

        //判定範囲
        float[] touchPos = getWorldPoint(mParticleTouchData.touchPosX, mParticleTouchData.touchPosY, gl);
        float minX = touchPos[0] - radius;
        float maxX = touchPos[0] + radius;
        float minY = touchPos[1] - radius;
        float maxY = touchPos[1] + radius;

        //タッチ位置のworld座標
        mParticleTouchData.touchPosWorldX = touchPos[0];
        mParticleTouchData.touchPosWorldY = touchPos[1];

        //タッチ判定
        int num = pg.getParticleCount();
        int index;
        for (index = 0; index < num; index++) {
            float x = ps.getParticlePositionX(index);
            float y = ps.getParticlePositionY(index);

            //タッチした箇所に粒子があるかを判定
            if( (x >= minX) && (x <= maxX) && (y >= minY) && (y <= maxY) ){
                //Log.i("粒子 x/y", index + ":" + Float.toString(x) + "/" + Float.toString(y) );
                //Log.i("内側", "Index=" + index);

                //タッチ状態→粒子内部
                status = ParticalTouchStatus.Intern;

                //その粒子が境界粒子か判定
                if( pd.getBorder().indexOf(index) != -1){
                    //Log.i("境界", "Index=" + index);

                    //タッチ状態→境界
                    mParticleTouchData.borderIndex = index;
                    status = ParticalTouchStatus.Border;
                    break;
                }
                break;
            }
        }

        //追随判定
        if( ((mParticleTouchData.status == ParticalTouchStatus.Border) || (mParticleTouchData.status == ParticalTouchStatus.Trace))
            && (status == ParticalTouchStatus.Extern)){
            //前回の判定結果が、「境界」or「追随」で、かつ、今回の判定結果が「外側」であれば、状態を追随にする
            status = ParticalTouchStatus.Trace;

            Log.i("状態", "追随確定");

            /* 粒子をタッチ位置に付随させる */
            ps.setParticlePosition(mParticleTouchData.borderIndex, mParticleTouchData.touchPosWorldX + 0.1f, mParticleTouchData.touchPosWorldY+ 0.1f);
        }

        //保持するタッチ状態を更新(次回前回値として参照)
        mParticleTouchData.status = status;
    }

    /*
     * メニュービュー位置情報の設定
     */
    public void reqSetMenuSize(CreateFluidWorldMenuActivity.FluidMenuKind kind, float top, float left, float right, float bottom, int ani_duration ){

        //メニュー本体のビュー位置情報を保持
        if( kind == CreateFluidWorldMenuActivity.FluidMenuKind.CONTENTS){
            menuContentsTop = top;
            menuContentsLeft = left;
            menuContentsRight = right;
            menuContentsBottom = bottom;
            menuUpAniDuration = ani_duration;

        }else if( kind == CreateFluidWorldMenuActivity.FluidMenuKind.INIT){
            menuInitTop = top;
            menuInitLeft = left;
            menuInitRight = right;
            menuInitBottom = bottom;
            menuDownAniDuration = ani_duration;

            //すべて取得したら、フラグを更新
            mGetMenuCorner = true;
        }else{
            /* 対象外 */
        }
    }

    /*
     * menu動作の制御要求
     */
    public void reqMenuMove(MenuMoveControl control){

        if( control == MenuMoveControl.UP){
            //上方向を保持
            mMenuVelocity = mMenuUpVelocity;

            Log.i("test", "reqMenuMove UP");

        }else if( control == MenuMoveControl.DOWN ){
            // 下方向を保持
            mMenuVelocity = mMenuDownVelocity;

            Log.i("test", "reqMenuMove DOWN");

        }else{
            //do nothing
            Log.i("test", "reqMenuMove STOP");
        }

        //制御情報を保持
        mMenuMove = control;
    }

    /*
     * フリック物体の生成登録
     * @memo 物理世界外の操作画面よりコールされる。
     * @para 生成位置(X座標) 単位px
     * @para 生成位置(Y座標) 単位px
     * @para 差分(X座標)
     * @para 差分(Y座標)
     * @para 速度(Y軸)
     * @para 生成する形
     */
    public void reqEntryFlickObject(float posX, float posY, float deltaX, float deltaY, float velocityY, FlickFigureFragment.FlickShape shape){
        //フリックされた物体を登録
        Log.i("test", "flick posX=\t" + posX + "\tposY=\t" + posY);
        Log.i("test", "flick deltaX\t" + deltaX + "\tdeltaY\t" + deltaY);
        mFlickObjectData = new FlickObjectData(posX, posY, deltaX, deltaY, velocityY, shape);
    }

    public void reqEntryTouchObject(float posX, float posY){
        //touchされた物体を登録
        mTouchObjectData = new TouchObjectData(posX, posY);
        mTouchFlg       = true;
    }

    /*
     * フリック物体の制御要求
     */
    public void reqFlickObjectCtrl(FlickControlFragment.FlickControl type){
        //コントロールする種別を保持
        mFrickCtrlType = type;
    }

    /*
     * 大砲の制御要求
     */
    public void reqCannonCtrl(boolean enable){
        //要求を保持
        mCannonCtrl = enable;

        //大砲キャンセル時
        if(!enable){
            //削除漏れに対応
            for( Long key: mMapCannonData.keySet() ){
                BodyData bd = mMapCannonData.get(key);
                mWorld.destroyBody(bd.getBody());
            }
            mMapCannonData.clear();
            mCannonCreateCycle = 0;
        }
    }

    /*
     * パーティクルの再生成要求
     */
    public void reqRegeneration(){
        mRegenerationState = RegenerationState.DELETE;
    }

    /*
     * 重力を画面の向きに合わせて設定するかどうか
     */
    public void reqGravityDirection(boolean direction){

    }

    /*
     * ピン止め
     */
    public void reqSetPin(boolean pin){

    }


}
