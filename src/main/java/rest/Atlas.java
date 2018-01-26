package rest;


import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Minimum priors to interact with the API
 *
 * @author Felix Meyenhofer
 */
public class Atlas {

    static final String DEFAULT_PRODUCT_NAME = "Mouse Brain Reference Data";
    static final int DEFAULT_PRODUCT_ID = 12;


    public enum Species {
        HUMAN,
        MOUSE
    }


    public enum PlaneOfSection {
        CORONAL    ("yz", 0, new int[]{2, 1}, true),
        SAGITAL    ("xy", 2, new int[]{0, 1}, false),
        HORIZONTAL ("xz", 1, new int[]{0, 2}, false);

        private final int[] xy;
        private final String name;
        private final int fixed;
        private final boolean swap;

        PlaneOfSection(String name, int fixedDimension, int[] xy, boolean swapAxes) {
            this.name = name;
            this.fixed = fixedDimension;
            this.xy = xy;
            this.swap = swapAxes;
        }

        public int getFixedDimension() {
            return this.fixed;
        }

        public int[] getSectionAxes() {
            return xy;
        }

        public boolean swapAxes() {
            return swap;
        }

        public static List<String> getLabels() {
            List<String> labels = new ArrayList<>(PlaneOfSection.class.getEnumConstants().length);
            for (PlaneOfSection section : PlaneOfSection.class.getEnumConstants()) {
                labels.add(section.toString().toLowerCase());
            }
            return labels;
        }

        public static PlaneOfSection get(String label) {
            for (PlaneOfSection section : PlaneOfSection.class.getEnumConstants()) {
                if (section.toString().toLowerCase().equals(label.toLowerCase())) {
                    return section;
                }
            }
            return null;
        }
    }


    public enum VoxelResolution {
        TEN        (10,  "10um", new long[]{1320, 800, 1140}),
        TWENTYFIVE (25,  "25um", new long[]{ 528, 320,  456}),
        FIFTY      (50,  "50um", new long[]{ 264, 160,  228}),
        HUNDRED    (100, "100um",new long[]{ 132,  80,  116});

        private final String label;
        private double value;
        private long[] dim;

        VoxelResolution(double res, String label, long[] refVolDim) {
            this.value = res;
            this.label = label;
            this.dim = refVolDim;
        }

        public static VoxelResolution get(String resolution) {
            double value = Double.parseDouble(resolution.replace("um", ""));

            for (VoxelResolution vr : VoxelResolution.class.getEnumConstants()) {
                if (vr.value  == value) {
                    return vr;
                }
            }
            return null;
        }

        public static VoxelResolution getClosest(long value, int d) {
            TreeMap<Long, VoxelResolution> map = new TreeMap<>();
            for (VoxelResolution res : VoxelResolution.class.getEnumConstants()) {
                map.put(res.dim[d] - value, res);
            }
            return map.firstEntry().getValue();
        }

        public static VoxelResolution getClosest(double res) {
            TreeMap<Double, VoxelResolution> map = new TreeMap<>();
            for (VoxelResolution resolution : VoxelResolution.class.getEnumConstants()) {
                map.put(resolution.getValue() - res, resolution);
            }
            return map.firstEntry().getValue();
        }

        public static List<String> getLabels() {
            List<String> lbls = new ArrayList<>(VoxelResolution.class.getEnumConstants().length);
            for (VoxelResolution res : VoxelResolution.class.getEnumConstants()) {
                lbls.add(res.getLabel());
            }
            return lbls;
        }

        public double getValue() {
            return this.value;
        }

        public String getLabel() {
            return this.label;
        }

        public static String getLabel(double res) {
            for (VoxelResolution vr : VoxelResolution.class.getEnumConstants()) {
                if (vr.getValue() == res) {
                    return vr.getLabel();
                }
            }
            return null;
        }
    }


    public enum Modality {
        AUTOFLUO   ("average_template/",    "average_template_", "auto-fluorescence"),
        NISSEL     ("ara_nissl/",           "ara_nissl_",        "nissel"),
        ANNOTATION ("annotation/ccf_2016/", "annotation_",       "annotation");

        private String suburl;
        private String filename;
        private String name;

        Modality(String sub_url, String filename, String name) {
            this.suburl = sub_url;
            this.filename = filename;
            this.name = name;
        }

        String getSubUrl() {
            return this.suburl;
        }

        String getFileTrunk() {
            return this.filename;
        }

        String getName() {
            return this.name;
        }

        static Modality get(String modality) {
            for (Modality mod : Modality.class.getEnumConstants()) {
                if (mod.getName().equals(modality.toLowerCase())) {
                    return mod;
                }
            }
            return null;
        }

        public static List<String> getLabels() {
            List<String> lbls = new ArrayList<>(Modality.class.getEnumConstants().length);
            for (Modality mod : Modality.class.getEnumConstants()) {
                lbls.add(mod.name);
            }
            return lbls;
        }
    }
}
