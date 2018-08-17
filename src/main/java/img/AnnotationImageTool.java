package img;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Felix Meyenhofer
 */
public class AnnotationImageTool {

    public static <T extends RealType<T> & NativeType<T>> HashMap<Double, List<float[]>> getContourCoordinates(RandomAccessibleInterval<T> annotationSection) {
        int nDim = annotationSection.numDimensions();

        // Initialize contour container
        HashMap<Double, List<float[]>> contours = new HashMap<>();

        // Take care of border conditions and get a cursor for the neighborhood centers
        Interval interval = Intervals.expand(annotationSection, -1);
        annotationSection = Views.interval(annotationSection, interval);
        Cursor<T> center = Views.iterable(annotationSection).cursor();

        // Define the pixel neighborhood (4-connected)
        DiamondShape shape = new DiamondShape(1);

        // Extract all contour pixel positions of annotations defined by their ID
        for (Neighborhood<T> neighborhood : shape.neighborhoods(annotationSection)) {
            center.fwd();
            T centerValue = center.get();
            Double id = centerValue.getRealDouble();

            // Initialize contour coordinate container if necessary
            if (!contours.containsKey(id)) {
                contours.put(id, new ArrayList<>());
            }

            // Check if the pixel lies on the contour
            for (T neighborValue : neighborhood) {
                if (!centerValue.valueEquals(neighborValue)) {
                    float[] position = new float[nDim];
                    center.localize(position);
                    contours.get(id).add(position);
                    break;
                }
            }
        }

        return contours;
    }

    public static <T extends RealType<T> & NativeType<T>> List<RandomAccessibleInterval<BitType>> getMasks(Img<T> annotationSection, List<Integer> ids) {
        HashMap<Integer, Img<BitType>> msks = new HashMap<>(ids.size());
        for (int id : ids) {
            msks.put(id, new ArrayImgFactory<BitType>().create(annotationSection, new BitType()));
        }

        Cursor<T> cursor = annotationSection.cursor();

        while (cursor.hasNext()) {
            T pixel = cursor.next();
            int id = (int) pixel.getRealDouble();

            if (ids.contains(id)) {
                Img<BitType> msk = msks.get(id);
                RandomAccess<BitType> ra = msk.randomAccess();
                ra.setPosition(cursor);
                ra.get().set(true);
            }
        }

        return new ArrayList(msks.values());
    }


    public static <T extends RealType<T> & NativeType<T>> Img<BitType> getRootMask(Img<T> annotationSection) {
        Img<BitType> msk = new ArrayImgFactory<BitType>().create(annotationSection, new BitType());

        Cursor<T> c1 = Views.flatIterable(annotationSection).cursor();
        Cursor<BitType> c2 = Views.flatIterable(msk).cursor();

        while (c1.hasNext()) {
            c2.fwd();
            T pixel = c1.next();

            if (pixel.getRealDouble() > 0) {
                c2.get().set(true);
            }
        }

        return msk;
    }
}
