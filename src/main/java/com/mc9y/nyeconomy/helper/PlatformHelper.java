package com.mc9y.nyeconomy.helper;

/**
 * @author Blank038
 */
public class PlatformHelper {

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
