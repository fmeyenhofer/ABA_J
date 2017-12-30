package img.ij2;

import io.scif.img.IO;
import mpicbg.models.Point;
import net.imagej.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;



import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Felix Meyenhofer
 */
public class SectionContours {

    public static RandomAccessibleInterval<ARGBType> contourMatcher(RandomAccessibleInterval<BitType> msk) {

        long[] dimensions = new long[msk.numDimensions()];
        double[] position = new double[msk.numDimensions()];

        msk.dimensions(dimensions);

        Collection<Point> pts = new ArrayList<>();


        // Take care of border conditions and get a cursor for the neighborhood centers
        Interval interval = Intervals.expand(msk, -1);
        msk = Views.interval(msk, interval);
        Cursor<BitType> center = Views.iterable(msk).cursor();

        // Define the pixel neighborhood (4-connected)
        DiamondShape shape = new DiamondShape(1);

        for (Neighborhood<BitType> neighborhood : shape.neighborhoods(msk)) {
            BitType centerValue = center.next();

            // Only "true" pixels
            if (centerValue.get()) {
                for (BitType neighborValue : neighborhood) {
                    // If it has at leas one "false" neighbor it's at the border
                    if (!centerValue.valueEquals(neighborValue)) {
                        center.localize(position);
                        pts.add(new Point(position));
                        break;
                    }
                }
            }
        }

        // smooth

        // delaunay triangulation

//        DelaunayTriangulation delaunay = new DelaunayTriangulation();

        // subsample



        // display
        RandomAccessibleInterval<ARGBType> rgb = ArrayImgs.argbs(dimensions);
        RandomAccess<ARGBType> ra = rgb.randomAccess();

        for (Point point : pts) {
            long[] pos = new long[msk.numDimensions()];
            int c = 0;
            for (double coord : point.getW()) {
                pos[c++] = Math.round(coord);
            }
            
            ra.setPosition(pos);
            ra.get().set(0x0000ff00);
        }

        return rgb;
    }


    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        String path = "/Users/turf/Desktop/ref-mask.tif";
//        RandomAccessibleInterval<UnsignedByteType> usbt = (RandomAccessibleInterval) ij.io().open();

        BitType type = new BitType();
        ArrayImgFactory<BitType> factory = new ArrayImgFactory<>();
        RandomAccessibleInterval<BitType> rai = IO.openImgs(path, factory, type).get(0);

//        Converter<UnsignedByteType, BitType > c1 = new Conver
//        RandomAccessibleInterval<ARGBType> rai = Converters.convert(usb, c1, new ARGBType());

        RandomAccessibleInterval<ARGBType> rgb = SectionContours.contourMatcher(rai);

        ImageJFunctions.show(rgb, "contour points");
        ImageJFunctions.show(rai, "mask");
        
//        ij.ui().show("contour points", rgb);
    }

}
