package io.github.gyai.projects.ability;

import java.util.Objects;
import java.util.UUID;

/** Bukkit-free world-space basis captured when a cue is authored. */
public record AnchorFrame(UUID worldId, String dimension, double x, double y, double z,
                          double forwardX, double forwardY, double forwardZ,
                          double upX, double upY, double upZ) {
    public AnchorFrame {
        Objects.requireNonNull(worldId); Objects.requireNonNull(dimension);
        if (dimension.isBlank() || dimension.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 128
                || !finite(x,y,z,forwardX,forwardY,forwardZ,upX,upY,upZ)
                || Math.abs(x) > 30_000_000 || Math.abs(y) > 30_000_000 || Math.abs(z) > 30_000_000
                || length(forwardX,forwardY,forwardZ) == 0 || length(upX,upY,upZ) == 0
                || Math.abs(dot(forwardX,forwardY,forwardZ,upX,upY,upZ)) / length(forwardX,forwardY,forwardZ) / length(upX,upY,upZ) > .9999)
            throw new IllegalArgumentException("Invalid anchor frame");
    }
    public AnchorFrame normalized() { double n = length(forwardX,forwardY,forwardZ), u = length(upX,upY,upZ); return new AnchorFrame(worldId, dimension,x,y,z,forwardX/n,forwardY/n,forwardZ/n,upX/u,upY/u,upZ/u); }
    private static boolean finite(double... v) { for(double n:v) if(!Double.isFinite(n)) return false; return true; }
    private static double length(double x,double y,double z) { return Math.sqrt(x*x+y*y+z*z); }
    private static double dot(double a,double b,double c,double x,double y,double z) { return a*x+b*y+c*z; }
}
