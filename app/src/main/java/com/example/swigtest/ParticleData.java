package com.example.swigtest;

import android.test.SingleLaunchActivityTestCase;

import com.google.fpl.liquidfun.ParticleGroup;
import com.google.fpl.liquidfun.ParticleSystem;

import java.util.ArrayList;

public class ParticleData {
    long id;
    //ParticleSystem particleSystem;
    ParticleGroup particleGroup;
    float particleRadius;           //設定したradius
    float particleRadiusReal;      //実際の初期配置位置から計算したradius
    int textureId;
    ArrayList<Integer> textureIdList;
    ArrayList<ArrayList<Integer>> row;
    ArrayList<Integer> border;          //境界粒子

    public ParticleData(long id, ParticleSystem ps, ParticleGroup pg, float particleRadius, ArrayList<ArrayList<Integer>> row, ArrayList<Integer> border, int textureId) {
        this.id = id;
        this.particleGroup = pg;
        this.textureId = textureId;
        this.particleRadius = particleRadius;
        this.row = row;
        this.border = border;

        this.particleRadiusReal = (ps.getParticlePositionX(1) - ps.getParticlePositionX(0)) / 2;
    }

    public long getId() {
        return this.id;
    }

    public ParticleGroup getParticleGroup() {
        return particleGroup;
    }

    public void setParticleGroup(ParticleGroup particleGroup) {
        this.particleGroup = particleGroup;
    }

    public int getTextureId() { return this.textureId;}

    public float getParticleRadius() { return this.particleRadius;}

    public float getParticleRadiusReal() { return this.particleRadiusReal;}

    public ArrayList<ArrayList<Integer>> getRow() { return this.row;}

    public void setRow(ArrayList<ArrayList<Integer>> row) {
        this.row = row;
    }

    public ArrayList<Integer> getBorder() {
        return border;
    }

    public ArrayList<Integer> getTextureIdList() {
        return textureIdList;
    }

    public void setTextureIdList(ArrayList<Integer> textureIdList) {
        this.textureIdList = textureIdList;
    }
}
