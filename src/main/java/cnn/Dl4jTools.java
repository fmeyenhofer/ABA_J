package cnn;

import io.scif.img.IO;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;

/**
 * @author Felix Meyenhofer
 */
public class Dl4jTools {

    public static INDArray loadImg(File path) {
        DoubleType type = new DoubleType();
        ArrayImgFactory<DoubleType> factory = new ArrayImgFactory<>();
        RandomAccessibleInterval<DoubleType> rai = IO.openImgs(path.getAbsolutePath(), factory, type).get(0);

        return randomAccessibleInterval2INDArray(rai);
    }


    public static <T extends RealType<T> & NativeType<T>> INDArray randomAccessibleInterval2INDArray(RandomAccessibleInterval<T> rai) {

        long[] dimensions = new long[rai.numDimensions()];
        rai.dimensions(dimensions);

        int rows = 224;
        int cols = 224;
        long[] lowerBound = new long[]{0, 0};
        long[] upperBound = new long[]{223, 223};
        INDArray out = Nd4j.zeros(1, 3, rows, cols);

        // Scale
        double scaleX = ((double) rows) / ((double) dimensions[0]);
        double scaleY = ((double) cols) / ((double) dimensions[1]);
        final InvertibleRealTransform scale = new Scale(scaleX, scaleY);

        final ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval<T>> extended = Views.extendZero(rai);
        final RealRandomAccessible<T> interpolant = Views.interpolate(extended, new NLinearInterpolatorFactory<T>());
        final RandomAccessible<T> warp = RealViews.transform(interpolant, scale);
        final IntervalView interval = Views.interval(warp, lowerBound, upperBound);
//        ImageJFunctions.show(interval);

        // Copy the pixels in a nd-array
        Cursor<T> cursor = Views.flatIterable(Views.interval(warp, interval)).cursor();
        int row = 0;
        int col = 0;
        while (cursor.hasNext()) {
//            System.out.println("row :" + row + ", col: " + col);

            cursor.fwd();
            double value = cursor.get().getRealDouble();

            out.putScalar(new int[]{0, 0, row, col}, value);
            out.putScalar(new int[]{0, 1, row, col}, value);
            out.putScalar(new int[]{0, 2, row++, col}, value);

            if (row == rows) {
                row = 0;
                col++;
            }

        }

        return out;
    }

    public static void main(String[] args) {
        File path = new File("/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/26836491_25um_red_300.tif");

        DoubleType type = new DoubleType();
        ArrayImgFactory<DoubleType> factory = new ArrayImgFactory<>();
        RandomAccessibleInterval<DoubleType> rai = IO.openImgs(path.getAbsolutePath(), factory, type).get(0);
        ImageJFunctions.show(rai);


//        File path2 = new File("/Users/turf/Desktop/26836491_25um_red_300.tif");
//
//        ARGBType type2 = new ARGBType();
//        ArrayImgFactory<ARGBType> factory2 = new ArrayImgFactory<>();
//        RandomAccessibleInterval<ARGBType> rai2 = IO.openImgs(path2.getAbsolutePath(), factory2, type2);

        INDArray array = Dl4jTools.randomAccessibleInterval2INDArray(rai);

//        System.out.println(array.shape());
        System.out.println("done.");
    }
}
