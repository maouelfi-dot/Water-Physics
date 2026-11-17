package com.oceanphysics;

/** "Duck interface": BoatEntityMixin membuat BoatEntity mengimplementasikan ini supaya renderer bisa membaca sudut goyang. */
public interface WaveTilt {
    boolean oceanphysics$hasRig();

    float oceanphysics$rotX(float tickDelta);

    float oceanphysics$rotZ(float tickDelta);
}
