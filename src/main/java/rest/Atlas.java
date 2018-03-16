package rest;

import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import org.scijava.util.ListUtils;

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
        MOUSE;

        public static Species get(String name) {
            for (Species species : Species.class.getEnumConstants()) {
                if (species.toString().toLowerCase().equals(name.toLowerCase())) {
                    return species;
                }
            }
            throw new RuntimeException("No species with the name '" + name.toLowerCase() + "' exists.");
        }

        public static List<String> getNames() {
            List<String> names = new ArrayList<>(Species.class.getEnumConstants().length);
            for (Species species : Species.class.getEnumConstants()) {
                names.add(species.toString().toLowerCase());
            }

            return names;
        }
    }


    public enum PlaneOfSection {
        CORONAL    ("yz", 0, new int[]{2, 1}, true),
        SAGITAL    ("xy", 2, new int[]{0, 1}, false),
        HORIZONTAL ("xz", 1, new int[]{0, 2}, false);

        private final int[] axesIndices;
        private final String name;
        private final int fixedAxisIndex;
        private final boolean swap;

        PlaneOfSection(String name, int fixedAxisIndex, int[] axesIndices, boolean swapAxes) {
            this.name = name;
            this.fixedAxisIndex = fixedAxisIndex;
            this.axesIndices = axesIndices;
            this.swap = swapAxes;
        }

        public int getRotationAxis(PlaneOfSection otherPlane) {
            for (int cAxis : this.axesIndices) {
                for (int dAxis : otherPlane.getSectionAxesIndices()) {
                    if (cAxis == dAxis) {
                        return cAxis;
                    }
                }
            }
            throw new RuntimeException("Could not find the common axis between " +
                    this + " and " + otherPlane + ". This should not be possible!");
        }

        public int getFixedAxisIndex() {
            return this.fixedAxisIndex;
        }

        public int[] getSectionAxesIndices() {
            return axesIndices;
        }

        public boolean swapAxes() {
            return swap;
        }

        public String getLabel() {
            return this.toString().toLowerCase();
        }

        public String getName() {
            return this.name;
        }

        public static List<String> getLabels() {
            List<String> labels = new ArrayList<>(PlaneOfSection.class.getEnumConstants().length);
            for (PlaneOfSection section : PlaneOfSection.class.getEnumConstants()) {
                labels.add(section.getLabel());
            }

            return labels;
        }

        public static PlaneOfSection get(String label) {
            for (PlaneOfSection section : PlaneOfSection.class.getEnumConstants()) {
                if (section.getLabel().equals(label.toLowerCase())) {
                    return section;
                }
            }
            throw new RuntimeException(label +
                    " does not exist. Available PlaneOfSections are " +
                    ListUtils.string(getLabels()));
        }

        public long[] template2SectionCoordinate(long[] rPos) {
            long[] sPos = new long[2];
            int i = 0;
            for (int axisIndex : axesIndices) {
                sPos[i++] = rPos[axisIndex];
            }

            return sPos;
        }

        public double[] template2SectionCoordinate(double[] rPos) {
            double[] sPos = new double[2];
            int i = 0;
            for (int axisIndex : axesIndices) {
                sPos[i++] = rPos[axisIndex];
            }

            return sPos;
        }

        public double[] section2TemplateCoordinate(double[] sPos, double sectionNumber) {
            double[] tPos = new double[3];
            int i = 0;
            for (int axisIndex : axesIndices) {
                tPos[axisIndex] = sPos[i++];
            }
            tPos[fixedAxisIndex] = sectionNumber;

            return tPos;
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
            throw new RuntimeException(resolution +
                    " does not exist. Available VoxelResolutions are: " +
                    ListUtils.string(getLabels()));
        }

        public static VoxelResolution getClosest(long value, int d) {
            TreeMap<Long, VoxelResolution> map = new TreeMap<>();
            for (VoxelResolution res : VoxelResolution.class.getEnumConstants()) {
                map.put(Math.abs(res.dim[d] - value), res);
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

        public long[] getDimension() {
            return this.dim;
        }

        public Interval getInterval() {
            long[] ub = new long[3];
            for (int d = 0; d < 3; d++) {
                ub[d] = dim[d] -1;
            }

            return new FinalInterval(new long[]{0, 0, 0}, ub);
        }
    }


    public enum Modality {
        AUTOFLUO   ("average_template/",    "average_template_", "auto-fluorescence"),
        NISSEL     ("ara_nissl/",           "ara_nissl_",        "nissl"),
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

        public static Modality get(String modality) {
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

    public static long[] getVolumeDimension(Dimensions dim, VoxelResolution resolution, PlaneOfSection plane) {
        long[] dim3d = new long[3];

        int d = 0;
        for (int axis : plane.getSectionAxesIndices()) {
            dim3d[axis] = dim.dimension(d++);
        }

        int df = plane.getFixedAxisIndex();
        dim3d[df] = resolution.getDimension()[df];

        return dim3d;
    }
}
