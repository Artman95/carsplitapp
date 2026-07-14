package com.artman.navisplit;

/** One tiling scenario: two apps side by side with a left-window width percent. */
public class Scenario {
    public long id;
    public String name, left, right;
    public int leftPercent;

    public Scenario(long id, String name, String left, String right, int leftPercent) {
        this.id = id; this.name = name; this.left = left; this.right = right;
        this.leftPercent = leftPercent;
    }
}
