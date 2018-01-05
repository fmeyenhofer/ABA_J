package gui;

import bdv.util.*;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.view.Views;
import sc.fiji.io.Nrrd_Reader;

import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Felix Meyenhofer
 */
public class InteractiveAlignmentUi<T extends RealType<T>> {

    private final RandomAccessibleInterval<T> secImg;
    private final RandomAccessibleInterval<T> refImg;

    public InteractiveAlignmentUi(RandomAccessibleInterval<T> secImg, RandomAccessibleInterval<T> refImg) {
        this.secImg = secImg;
        this.refImg = refImg;
    }

    public void createAndShow() {
        JFrame window = new JFrame("Interactive Section Alignment");

        BdvOptions options = Bdv.options();
        options.axisOrder(AxisOrder.XYZ);



        final BdvHandlePanel bdvPanel = new BdvHandlePanel(window, options);


        window.add(bdvPanel.getViewerPanel(), BorderLayout.CENTER);
        window.setBounds( 50, 50, 512, 512 );
        window.setVisible(true);

        final BdvSource ref = BdvFunctions.show(refImg,
                "Avg. Template",
                Bdv.options().addTo(bdvPanel));
        ref.setColor(new ARGBType(0x00FF00));
        ref.setActive(true);
        ref.setDisplayRange(0, 400);

        final BdvSource exp = BdvFunctions.show(secImg,
                "New Section",
                Bdv.options().addTo(bdvPanel));
        exp.setColor(new ARGBType(0xFF0000));

    }

    public static void main(String[] args) {
        String refPath = "/Users/turf/switchdrive/SJMCS/data/aba/mouse-ccf-3/reference-volumes/average_template_25.nrrd";
        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif";

        File refFile = new File(refPath);
        Nrrd_Reader nrrd = new Nrrd_Reader();
        ImagePlus imp = nrrd.load(refFile.getParent(), refFile.getName());
        RandomAccessibleInterval rai1 = ImageJFunctions.wrap(imp);
        RandomAccessibleInterval refImg = Views.permute(rai1, 0, 2);

        long zmax = refImg.dimension(refImg.numDimensions() - 1) - 1;
//        Views.
//        final ImgPlus refImgp = new ImgPlus((Img) per, "avg. template");

        imp = IJ.openImage(secPath);
        RandomAccessibleInterval rai2 = ImageJFunctions.wrap(imp);
        RandomAccessibleInterval secImg = Views.addDimension(rai2, 0, zmax);
//        ExtendedRandomAccessibleInterval ext = Views.extendZero(rai);
//        RandomAccessibleInterval raie = Views.interval()
//        final ImgPlus secImgp = new ImgPlus((Img)rai, "new section");

        InteractiveAlignmentUi ui = new InteractiveAlignmentUi(secImg, refImg);
        ui.createAndShow();
    }
}
