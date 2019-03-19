package table;

import java.util.ArrayList;
import java.util.List;

/**
 * Class containing some result table header conventions of ImageJ.
 * When 'Analyze Particles...' is used to measure ROI and in the
 * measurements centroid or center of mass is selected, ij gives specific
 * header names. These are stored here for lack of a better solution and motivation
 * to search through the ij1 code to find if these definitions could be grabbed from there.
 *
 * @author Felix Meyenhofer
 */
class TableConventions {

    public enum Header implements XYHeaders {
        @SuppressWarnings("unused")
        CENTROID("X", "Y"),

        @SuppressWarnings("unused")
        BARYCENTER("XM", "YM");

        private String x;
        private String y;

        Header(String x, String y) {
            this.x = x;
            this.y = y;
        }

        public List<String> getNames() {
            return new ArrayList<String>() {{
                add(x);
                add(y);
            }};
        }

        public String getXColumn() {
            return x;
        }

        public String getYColumn() {
            return y;
        }

        public List<String> getColumns() {
            List<String> cols = new ArrayList<>(2);
            cols.add(getXColumn());
            cols.add(getYColumn());
            return cols;
        }

        public static Header findContained(List<String> headers) {
            for (Header header : Header.class.getEnumConstants()) {
                if (headers.containsAll(header.getNames())) {
                    return header;
                }
            }

            return null;
        }
    }
}
