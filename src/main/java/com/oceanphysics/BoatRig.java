package com.oceanphysics;

/**
 * Badan kaku sederhana untuk goyangan kapal: dua sudut (putar sumbu X dan Z, sumbu dunia) yang mengejar
 * sudut target dari kemiringan permukaan air lewat pegas-peredam (bukan menempel langsung).
 * Hasilnya kapal punya INERSIA: oleng melewati target, memantul balik, lalu tenang.
 * Murni Java (tanpa Minecraft) supaya bisa diuji.
 */
public final class BoatRig {
    public float rotX, rotZ, prevRotX, prevRotZ, velX, velZ;

    /** Satu tick. tx/tz = sudut target (rad); k = kekakuan, c = redaman; max = batas sudut (rad). */
    public void step(float tx, float tz, double k, double c, float max) {
        prevRotX = rotX;
        prevRotZ = rotZ;
        velX += (float) (-k * (rotX - tx) - c * velX);
        velZ += (float) (-k * (rotZ - tz) - c * velZ);
        rotX += velX;
        rotZ += velZ;
        if (rotX > max) { rotX = max; if (velX > 0) velX = 0; }
        else if (rotX < -max) { rotX = -max; if (velX < 0) velX = 0; }
        if (rotZ > max) { rotZ = max; if (velZ > 0) velZ = 0; }
        else if (rotZ < -max) { rotZ = -max; if (velZ < 0) velZ = 0; }
    }

    public float lerpX(float t) { return prevRotX + (rotX - prevRotX) * t; }
    public float lerpZ(float t) { return prevRotZ + (rotZ - prevRotZ) * t; }
}
