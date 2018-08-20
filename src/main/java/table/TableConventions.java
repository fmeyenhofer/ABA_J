package table;

import java.util.ArrayList;

/**
 * Class containing some result table header conventions of ImageJ.
 * When 'Analyze Particles...' is used to measure ROI and in the
 * measurements centroid or center of mass is selected, ij gives specific
 * header names. These are stored here for lack of a better solution and motivation
 * to search through the ij1 code to find if these definitions could be grabbed from there.
 *
 * @author Felix Meyenhofer
 */
public class TableConventions {

    public enum Header {
        CENTROID("X", "Y"),
        BARYCENTER("XM", "YM");

        private String x;
        private String y;

        Header(String x, String y) {
            this.x = x;
            this.y = y;
        }

        public ArrayList<String> getNames() {
            return new ArrayList<String>() {{
                add(x);
                add(y);
            }};
        }

        public String getX() {
            return x;
        }

        public String getY() {
            return y;
        }

        public static Header findContained(ArrayList<String> headers) {
            for (Header header : Header.class.getEnumConstants()) {
                if (headers.containsAll(header.getNames())) {
                    return header;
                }
            }

            return null;
        }
    }
}
