package util.ij2;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import java.util.*;

/**
 * @author Felix Meyenhofer
 */
public class AnnotationImageTool {

    public static<T extends RealType<T> & NativeType<T>> HashMap<Integer, List<float[]>> getContourCoordinates(RandomAccessibleInterval<T> annotationSection, Set<Integer> ids) {
        int nDim = annotationSection.numDimensions();

        // Initialize controur container
        HashMap<Integer, List<float[]>> contours = new HashMap<>();
        for (Integer id : ids) {
            contours.put(id, new ArrayList<>());
        }

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
            Integer id = (int) centerValue.getRealDouble();

            if (id > 0) {
                for (T neighborValue : neighborhood) {
                    if (!centerValue.valueEquals(neighborValue)) {
                        if (contours.containsKey(id)) {
                            List<float[]> coordinates = contours.get(id);
                            float[] position = new float[nDim];
                            center.localize(position);
                            coordinates.add(position);
                        }
                        break;
                    }
                }
            }
        }

        return contours;
    }
}
