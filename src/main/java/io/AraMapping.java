package io;

import rest.Atlas;
import img.VolumeSection;

import bdv.img.TpsTransformWrapper;
import net.imglib2.realtransform.AffineTransform3D;

import java.io.File;
import java.io.Serializable;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Class regrouping the objects needed for the mapping, for serialization.
 *
 * @author Felix Meyenhofer
 */
public class AraMapping implements Serializable {
    /** plane along which the section image is cut */
    private  Atlas.PlaneOfSection planeOfSection;

    /** resolution of the template the section is registered against **/
    private Atlas.VoxelResolution templateResolution;

    /** Thin plate splines (2D) */
    private TpsTransformWrapper t_tps;
    private TpsTransformWrapper t_tpsi;

    /** Mapping of the section image in the global coordinates */
    private double[] t_s;

    /** Mapping of reference volume to global coordinates (could contain a manual transformation) */
    private double[] t_r;

    /** Plane through the reference volume (in the global space: section--t_s-->global<--t_r--reference) */
    private VolumeSection volumeSection;


    public AraMapping(Atlas.PlaneOfSection planeOfSection,
                      Atlas.VoxelResolution templateResolution,
                      VolumeSection volumeSection,
                      AffineTransform3D ts,
                      AffineTransform3D tr,
                      TpsTransformWrapper tps,
                      TpsTransformWrapper tpsi) {
        setPlaneOfSection(planeOfSection);
        setTemplateResolution(templateResolution);
        setVolumeSection(volumeSection);
        setT_tps(tps);
        setT_tpsi(tpsi);
        setAffineTs(ts);
        setAffineTr(tr);
    }

    public void save(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(this);
        oos.close();
        fos.close();
    }

    public static AraMapping load(File file) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        Object obj = ois.readObject();
        if (obj instanceof AraMapping) {
            return (AraMapping) obj;
        } else {
            throw new IOException("Wrong object " + obj.getClass().getName() + ".");
        }
    }

    private void setAffineTr(AffineTransform3D tr) {
        this.t_r = new double[12];
        tr.toArray(this.t_r);
    }

    public AffineTransform3D getAffineTr() {
        AffineTransform3D affine = new AffineTransform3D();
        affine.set(this.t_r);
        return affine;
    }

    private void setAffineTs(AffineTransform3D ts) {
        this.t_s = new double[12];
        ts.toArray(this.t_s);
    }

    public AffineTransform3D getAffineTs() {
        AffineTransform3D affine = new AffineTransform3D();
        affine.set(this.t_s);
        return affine;
    }

    public Atlas.PlaneOfSection getPlaneOfSection() {
        return planeOfSection;
    }

    private void setPlaneOfSection(Atlas.PlaneOfSection planeOfSection) {
        this.planeOfSection = planeOfSection;
    }

    public Atlas.VoxelResolution getTemplateResolution() {
        return templateResolution;
    }

    private void setTemplateResolution(Atlas.VoxelResolution templateResolution) {
        this.templateResolution = templateResolution;
    }

    public TpsTransformWrapper getT_tps() {
        return t_tps;
    }

    private void setT_tps(TpsTransformWrapper t_tps) {
        this.t_tps = t_tps;
    }

    public TpsTransformWrapper getT_tpsi() {
        return t_tpsi;
    }

    private void setT_tpsi(TpsTransformWrapper t_tpsi) {
        this.t_tpsi = t_tpsi;
    }

    public VolumeSection getVolumeSection() {
        return volumeSection;
    }

    private void setVolumeSection(VolumeSection volumeSection) {
        this.volumeSection = volumeSection;
    }
}
