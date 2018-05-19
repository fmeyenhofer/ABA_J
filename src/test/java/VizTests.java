import de.mpicbg.jug.clearvolume.gui.GenericClearVolumeGui;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import org.apache.commons.lang.ArrayUtils;


import javax.swing.*;
import java.io.IOException;

/**
 * TODO: rotations of sections and volume
 * TODO: launch options of the clear volume viewer (remove controls, do presettings - gamma, intensity)
 * TODO: the slice has rendering holes in it. is there a way to prevent it?
 * 
 * @author Felix Meyenhofer
 */
public class VizTests {

    private ImgPlus< DoubleType > imgPlus;
    private JFrame frame;
    private GenericClearVolumeGui< DoubleType > panelGui;


    public void show(ImgPlus<DoubleType> imgPlus) {
//        imgPlus = ( ImgPlus< T > ) datasetView.getData().getImgPlus();
        frame = new JFrame( "ClearVolume Tutorial 1" );
        frame.setBounds( 50, 50, 1024, 768 );
        panelGui = new GenericClearVolumeGui<DoubleType>( imgPlus );
        frame.add( panelGui );
        frame.setVisible( true );
    }

    public static void main(String[] args) throws IOException {
//        int sectionDim = 0;
        long sectionNum = 270;
        double planeVal = 30000;
        String volfile = "/Users/turf/switchdrive/SJMCS/data/devel/disp/average_template_25.tif";
        String slicefile = "/Users/turf/switchdrive/SJMCS/data/devel/disp/resampled_268364491-290.tif";

        // Spin up ij
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // Load images
        final Dataset sectionDataset = ij.scifio().datasetIO().open(slicefile);
        RandomAccessibleInterval<DoubleType> sectionRai = (RandomAccessibleInterval<DoubleType>) sectionDataset.getImgPlus();
        final Dataset volumeDataset = ij.scifio().datasetIO().open(volfile);
        RandomAccessibleInterval<DoubleType> volumeRai = (RandomAccessibleInterval<DoubleType>) volumeDataset.getImgPlus();


//        final ImageDisplay display = ( ImageDisplay ) ij.display().createDisplay( dataset );
//        final DatasetView dsv = ij.imageDisplay().getActiveDatasetView( display );
//        ij.ui().showUI();
//        ij.ui().show(sectionDataset);
//        ij.ui().show(volumeDataset);

        long[] dim2d = new long[sectionDataset.numDimensions()];
        // figure out which dimension in the volume has to be fixed
        for (int d = 0; d < sectionDataset.numDimensions(); d ++) {
            dim2d[d] = sectionDataset.dimension(d);
        }

        // Fetch dimensions and deduce the dimensions for the outputs
        long[] dim3d = new long[volumeDataset.numDimensions()];
        long[] ub3d = new long[volumeDataset.numDimensions()];
        int sectionDim = -1;
        int dd = 0;
        for (int d = 0; d < volumeDataset.numDimensions(); d++) {
            dim3d[d] = volumeDataset.dimension(d);
            ub3d[d] = volumeDataset.dimension(d) - 1;
            if (ArrayUtils.contains(dim2d, volumeDataset.dimension(d)) && dd < 1) {
                if (sectionDataset.dimension(dd++) != volumeDataset.dimension(d)) {
                    sectionRai = Views.permute(sectionRai, 0, 1);
                    dim2d[0] = sectionDataset.dimension(1);
                    dim2d[1] = sectionDataset.dimension(0);
                }
            } else {
                if (dd == 0) {
                    sectionDim = d;
                } else {
                    System.out.println("There is more than 1 dimension in the section image not covered by the reference volume.");
                }

            }
        }

        long[] dim4d = ArrayUtils.addAll(dim3d, new long[]{2});
//        long[] dim2d = ArrayUtils.subarray(dim3d, 0, 1);
        long[] dim2dc = ArrayUtils.addAll(dim2d, new long[]{2});
//        long[] ub2d = ArrayUtils.subarray(ub3d, 0, 2);
        
        // Create a new image for the 2D section overlay
        Img<DoubleType> overlay = ArrayImgs.doubles(dim2dc);

        // access bounds
        long[] lb2d1 = new long[]{0, 0, 0};
        long[] ub2d1 = new long[]{dim2d[0]-1, dim2d[1]-1, 0};
        long[] lb2d2 = new long[]{0, 0, 1};
        long[] ub2d2 = new long[]{dim2d[0]-1, dim2d[1]-1, 1};

        // copy the reference section
        Cursor<DoubleType> overlayCh1Cursor = Views.flatIterable(Views.interval(overlay, lb2d1, ub2d1)).cursor();
        Cursor<DoubleType> sectionCursor = Views.flatIterable(sectionRai).cursor();
        while (sectionCursor.hasNext()) {
            overlayCh1Cursor.fwd();
            sectionCursor.fwd();
            overlayCh1Cursor.get().set(sectionCursor.get());
        }

        // copy the registered section
        Cursor<DoubleType> referenceCursor = Views.flatIterable(Views.hyperSlice(volumeRai, sectionDim, sectionNum)).cursor();
        Cursor<DoubleType> overlayCh2Cursor = Views.flatIterable(Views.interval(overlay, lb2d2, ub2d2)).cursor();
        while (referenceCursor.hasNext()) {
            referenceCursor.fwd();
            overlayCh2Cursor.fwd();
            overlayCh2Cursor.get().set(referenceCursor.get());
        }

        ImgPlus<DoubleType> overlayImgPlus = new ImgPlus<>(overlay, "reference (red) + registered (green)",
                new AxisType[]{Axes.X, Axes.Y, Axes.CHANNEL});

//        IntervalView<DoubleType> sectionInterval = Views.interval(section, lb2d1, ub2d1);
//
//        IterableInterval<DoubleType> refView = Views.hyperSlice();
////                (RandomAccessibleInterval<DoubleType>)volds.getImgPlus(), planeDim, planeNum);
//        Cursor<DoubleType> refSliceCur = Views.flatIterable(refslice).cursor();
//        Cursor<DoubleType> refViewCur = Views.flatIterable((RandomAccessibleInterval<DoubleType>)refView).cursor();
//        while (refSliceCur.hasNext()) {
//            refSliceCur.next().set(refViewCur.next().get());
//        }


        // Create a new volume with two channels
        Img<DoubleType> slice = ArrayImgs.doubles(dim4d);

        // access bounds
        long[] lb4d1 = new long[]{0, 0, 0, 0};
        long[] ub4d1 = ArrayUtils.addAll(ub3d, new long[]{0});
        long[] lb4d2 = new long[]{0, 0, 0, 1};
        long[] ub4d2 = ArrayUtils.addAll(ub3d, new long[]{1});


        // copy the reference volume pixels
        Cursor<DoubleType> sliceCh1Cursor = Views.flatIterable(Views.interval(slice, lb4d1, ub4d1)).cursor();
        Cursor<DoubleType> volumeCursor = Views.flatIterable(volumeRai).cursor();
        while (volumeCursor.hasNext()) {
            volumeCursor.fwd();
            sliceCh1Cursor.fwd();
            sliceCh1Cursor.get().set(volumeCursor.get());
        }

        // paint a plane
//        IntervalView<DoubleType> view3 = Views.interval(slice, lb4d2, ub4d2);
//        IntervalView<DoubleType> slice =
        Cursor<DoubleType> sliceCh2Cursor = Views.hyperSlice(
                Views.interval(slice, lb4d2, ub4d2),
                sectionDim, sectionNum).cursor();
        while (sliceCh2Cursor.hasNext()) {
            sliceCh2Cursor.next().set(planeVal);
        }

        //
        ImgPlus<DoubleType> imgp = new ImgPlus<DoubleType>(slice, "brain + slice",
                new AxisType[]{Axes.X, Axes.Y, Axes.Z, Axes.CHANNEL});

        new VizTests().show(imgp);
        ij.ui().show(overlayImgPlus);
//        ImgPlus imgp = dataset.getImgPlus();
//        brainViewer.show((ImgPlus<RealType>)imgp);
    }
}
