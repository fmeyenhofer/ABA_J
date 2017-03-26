import net.imagej.ImageJ;

import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;

import net.imagej.ops.Ops;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.morphology.Erosion;
import net.imglib2.algorithm.morphology.StructuringElements;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegionCursor;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.BooleanType;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Felix Meyenhofer
 */
public class Test {


    public static void main( final String[] args )
    {

        final ImageJ ij = new ImageJ();


        BitType bt1 = new BitType(false);
        BitType bt2 = new BitType(false);
        BitType bt3 = new BitType(true);

        bt1.or(bt2);
        bt3.or(new BitType(false));


        try
        {
            // Open the image
//            String imgPath = "/Users/meyenhof/Desktop/AllenJ-Test/ome/10x/151229_Fos_IS-Cy3_ROI02-small.ome.tif";
            String imgPath = "/Users/turf/switchdrive/SJMCS_Thesis/test-data/20150530_cfos_tomato_fullBrain_11 - 2015-05-31 20.33.06-Cy3_roi02.ndpi - Series 2.tif";
            ImgOpener opener = new ImgOpener(ij.getContext());
            final Img< FloatType > img = opener.openImg(imgPath, new ArrayImgFactory<>(), new FloatType());
            ImageJFunctions.show(img, "input");


            // Smooth
            double[] sigma = new double[img.numDimensions()];
            for (int d = 0; d < img.numDimensions(); ++d) {
                sigma[d] = 3.0;
            }

            RandomAccessible<FloatType> view = Views.extendMirrorSingle(img);
            Gauss3.gauss(sigma, view, img);
            ImageJFunctions.show(img, "smoothing");

            // Threshold
            Img<FloatType> fil = ImgView.wrap(img, new ArrayImgFactory<>());
            Img<BitType> bw = ij.op().create().img(fil, new BitType());
            ij.op().threshold().huang(bw, fil);
//            ImageJFunctions.show(bw, "mask");


//            Img<RandomAccessible<IntegerType>> lbl = ij.op().create().img(bw, new UnsignedByteType());
//            ImageJFunctions.con

//            RandomAccessible<BitType> bwra = Views.iterable(bw);
//            Img<UnsignedByteType> lbl = new ArrayImgFactory<UnsignedByteType>().create(bw, new UnsignedByteType());
//            RandomAccess<UnsignedByteType> lblra = lbl.randomAccess();
//            List<Shape> se = StructuringElements.diamond(3, 2);
//            RandomAccess<BitType> bwra = bw.randomAccess();

            // Label the mask image
            Img<IntType> lblimg = new ArrayImgFactory<IntType>().create(bw, new IntType());
            int nlbl = ConnectedComponents.labelAllConnectedComponents(bw, lblimg, ConnectedComponents.StructuringElement.FOUR_CONNECTED);
            ImageJFunctions.show(lblimg, "labelled regions");

            // Get the area of all the labelled regions
            HashMap<Integer, Integer> areas = new HashMap<>(nlbl);
            for (int l = 1; l <= nlbl; l++) {
                areas.put(l, 0);
            }

            Cursor<IntType> cur1 = lblimg.cursor();
            while (cur1.hasNext()) {
                cur1.fwd();
                int value = cur1.get().getInteger();
                if (value > 0) {
                    int count = areas.get(value) + 1;
                    areas.put(value, count);
                }
            }

            // Get the biggest ROI
            Map.Entry<Integer, Integer> maxEntry = null;
            for (Map.Entry<Integer, Integer> entry : areas.entrySet()) {
//                System.out.println("Region " + entry.getKey() + ": " + entry.getValue());
                if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                    maxEntry = entry;
                }
            }

            assert maxEntry != null;

            // Keep only the biggest region to create a mask
            Img<BitType> mask = new ArrayImgFactory<BitType>().create(lblimg, new BitType());
//            Img<NativeType<BoolType>> mask = new ArrayImgFactory<NativeType<BoolType>>().create(lblimg, new <NativeType<BoolType>>);

            cur1 = lblimg.cursor();
            Cursor<BitType> cur2 = mask.cursor();
            while (cur1.hasNext()) {
                cur1.fwd();
                cur2.fwd();
                int label = cur1.get().getInteger();
                Boolean value = (label == maxEntry.getKey());
                cur2.get().set(value);
            }

            ImageJFunctions.show(mask, "biggest ROI");

            // Fill the holes
            Img<BitType> fill = ij.op().create().img(mask, new BitType());
//            Img<BitType> inv = ij.op().create().img(fill, new BitType());
//            ij.op().image().invert(inv, mask);
//            ImageJFunctions.show(fill, "initialized fill");

//            ij.op().morphology().fillHoles(fill, mask, ConnectedComponents.StructuringElement.FOUR_CONNECTED);
            
//
//            Cursor<BitType> fc = fill.cursor();
//            Cursor<BitType> mc = mask.cursor();
//
//            long[] dim = new long[mask.numDimensions()];
//            mask.dimensions(dim);
//
//            while (fc.hasNext()) {
//                fc.next();
//                mc.next();
//
//                boolean border = false;
//                for (int d = 0; d < mask.numDimensions(); d++) {
//                    if (fc.getLongPosition(d) == 0 || fc.getLongPosition(d) == (dim[d] - 1)) {
//                        border = true;
//                        break;
//                    }
//                }
//
//                if (border) {
//                    fc.get().set(mc.get());
//                } else {
//                    fc.get().set(true);
//                }
//
//            }
//
//
//
//            List<Shape> strel = StructuringElements.diamond(3, mask.numDimensions());
//            long s_prev = 0;
//            long s_curr = sum(fill);
//            while (s_prev != s_curr) {
//                s_prev = s_curr;
//
//                fill = Erosion.erode(fill, strel, 5);
//
//                fc.reset();
//                mc.reset();
//
//                while (fc.hasNext()) {
//                    fc.fwd();
//                    mc.fwd();
//
//                    BitType value = fc.get();
//                    value.or(mc.get());
//                    fc.get().set(value);
//                }
//
//                s_curr = sum(fill);
//            }


//            ij.op().morphology().fillHoles(mask, fill);
//            Img<BitType> fill = (Img<BitType>) ij.op().morphology().fillHoles(mask);



//            // Create a labelled image
//            ImgLabeling<Integer, IntType> lbl = new ImgLabeling<>(new ArrayImgFactory<IntType>().create(bw, new IntType()));
//            Iterator<Integer> lblgen = new Iterator<Integer>() {
//                private Integer num = 1;
//                @Override
//                public boolean hasNext() {
//                    return num != Integer.MAX_VALUE;
//                }
//
//                @Override
//                public Integer next() {
//                    return num++;
//                }
//            };
//
//            ConnectedComponents.labelAllConnectedComponents(bw, lbl, lblgen, ConnectedComponents.StructuringElement.FOUR_CONNECTED);
//            RandomAccessibleInterval<IntType> lblimg = lbl.getIndexImg();
//
//            // Compute the size of the labelled regions
//            LabelRegions<Integer> rois = new LabelRegions<>(lbl);
//            for (LabelRegion<Integer> roi : rois) { // The iterator of the LabeledRegions hangs
//                System.out.println("ROI " + roi.getLabel() + " size=" + roi.size());
//            }








            ImageJFunctions.show(fill, "slice mask");
            System.out.println("done");


        }
        catch ( final ImgIOException e )
        {
            e.printStackTrace();
        } catch (IncompatibleTypeException e) {
            e.printStackTrace();
        }
    }

    private static long sum(Img<BitType> img) {
        Cursor<BitType> cursor = img.cursor();
        long s = 0;
        while (cursor.hasNext()) {
            cursor.next();
            if (cursor.get().get()) {
                s += 1;
            }
        }

        return s;
    }
}
