package util.ij2;

import net.imagej.ImageJ;
import net.imagej.ops.OpService;

import net.imglib2.*;
import net.imglib2.Cursor;
import net.imglib2.algorithm.labeling.AllConnectedComponents;
import net.imglib2.algorithm.labeling.Watershed;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.labeling.Labeling;

import net.imglib2.labeling.LabelingType;
import net.imglib2.labeling.NativeImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;

import java.io.IOException;


/**
 * Collections of brain section image operations.
 *
 * @author Felix Meyenhofer
 */
public class ImageSectionTools {


    public static <T extends NativeType<T>> Img<BitType> createMask(RandomAccessibleInterval<T> rai, OpService ops) {
        double sigma = 13.0;

        Img fil = ops.copy().img((Img<T>) rai);
        ops.filter().gauss(fil, sigma);

        Img<BitType> bw = ops.create().img(fil, new BitType());
        ops.threshold().huang(bw, fil);

        Img<BitType> hol = ops.create().img(bw);
        ops.morphology().fillHoles(hol, bw);

        Img<BitType> msk = extractCenterBlob(hol, ops);

//        ImageJFunctions.show(bw, "thresholded");
//        ImageJFunctions.show(hol, "filled");
//        ImageJFunctions.show(msk, "mask");

        return msk;
    }


    public static <T extends RealType<T>> Img<BitType> extractCenterBlob(RandomAccessibleInterval<BitType> msk, OpService ops) {
        long[] dimensions = new long[msk.numDimensions()];
        msk.max(dimensions);

        // get the border
        Img<BitType> out = ops.create().img(msk);
        ops.morphology().outline(out, msk, false);

        // invert the outline
        Cursor<BitType> cursor = out.cursor();
        while (cursor.hasNext()) {
            cursor.next().not();
        }

        // distance transform
        RandomAccessibleInterval<T> dst = ops.image().distancetransform(out);
        IterableInterval<T> interval = Views.interval(dst, dst);

        // get the intensity boundaries
        T min = interval.firstElement().createVariable();
        min.setReal(0.0);
        T max = interval.firstElement().createVariable();
        ops.stats().max(max, interval);

        // create the seeds for the watershed (the minimum is one and the border another)
        // also invert the distance transform
        final NativeImgLabeling<Integer, IntType> sds =
                new NativeImgLabeling<>(new ArrayImgFactory<IntType>().create(msk, new IntType()));

        RandomAccess<LabelingType<Integer>> sdsCursor = sds.randomAccess();
        Cursor<T> dstCursor = interval.cursor();

        long[] position = new long[msk.numDimensions()];
        while (dstCursor.hasNext()) {
            dstCursor.fwd();
            dstCursor.localize(position);
            T dstValue = dstCursor.get();

            if (dstValue.equals(max)) {
                dstValue.set(min);
                sdsCursor.setPosition(position);
                sdsCursor.get().setLabel(1);
            } else {
                boolean isBorder = false;
                for (int p = 0; p < position.length; p++) {
                    if (0 == position[p] || position[p] == dimensions[p]) {
                        isBorder = true;
                        break;
                    }
                }

                if (isBorder) {
                    dstValue.set(min);
                    sdsCursor.setPosition(position);
                    sdsCursor.get().setLabel(2);
                } else {
                    dstValue.mul(-1.0);
                    dstValue.add(max);
                }
            }
        }

        // watershed
        final NativeImgLabeling<Integer, IntType> blb =
                new NativeImgLabeling<>(new ArrayImgFactory<IntType>().create(msk, new IntType()));
        Watershed<T, IntType> watershed = new Watershed<>();
        watershed.setIntensityImage(dst);
        watershed.setStructuringElement(AllConnectedComponents.getStructuringElement(2));
        watershed.setSeeds((Labeling)sds);
        watershed.setOutputLabeling((Labeling)blb);
        watershed.process();

        // create output mask
        Img<BitType> obj = ops.create().img(msk);

        Cursor<IntType> lblCur = Views.flatIterable(blb.getStorageImg()).cursor();
        Cursor<BitType> bwCur = Views.flatIterable(obj).cursor();
        while (bwCur.hasNext()) {
            bwCur.next().set(lblCur.next().getInteger() == 2);
        }

//        ImageJFunctions.show(out, "outline");
//        ImageJFunctions.show(sds.getStorageImg(), "seeds");
//        ImageJFunctions.show(dst, "dist. transform");
//        ImageJFunctions.show(dst, "modified inv. dist. transform");

        return obj;
    }


    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        Object vol = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/section2volume/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif");
//        Object vol = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/section2volume/average_template_50um_coronal-180.tif");
        Img<BitType> msk = ImageSectionTools.createMask((RandomAccessibleInterval) vol, ij.op());

        ij.ui().show("input", vol);
        ij.ui().show("section mask", msk);
    }
}
