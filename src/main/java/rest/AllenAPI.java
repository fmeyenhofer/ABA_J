package rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.jdom.Element;
import org.xml.sax.SAXException;

/**
 * Collection of api arguments that can be put together
 * to form an URL to access certain data. It's a convenience Class
 * to easily generate the commands for the RESTful API.
 * Thus all the conventions the applications relies on are coded here as
 * well as the assumptions about the API that are made.
 *
 * For reference:
 * http://help.brain-map.org/display/api/Allen+Brain+Atlas+API
 * http://www.brain-map.org/api/examples/examples/rma_builder/rma_builder.html
 *
 * @author Felix Meyenhofer
 */
class AllenAPI {

    /** Base URL of the Allen Atlas REST API */
    static final String BASE_URL = "http://api.brain-map.org/";


    /**
     * Aggregation of URL's to do RESTful Model Access (RMA)
     */
    static class RMA {

        /** Sub URL for the RMA queries */
        private static final String SUB_URL = "api/v2/data/";

        /** Query function and arguments */
        private static final String FUN_QUERY = "query";
        private static final String ARG_MODEL = "?criteria=model::";
        private static final String ARG_CRITERIA = ",rma::criteria,";
        private static final String ARG_INCLUDE = ",rma::include,";
        private static final String ARG_OPTIONS = ",rma::options";

        /** Separators to distinguish the URL parts when encoded as filename */
        private static final String SEPARATOR = "__";

        /** Response format of the service */
        static final String FILE_EXTENSION = ".xml";

        static URL createModelQueryUrl(String model_name) throws IOException {
            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + model_name);
        }

        static URL createProductQueryUrl(Atlas.Species species) throws IOException {
            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + "Product" +
                    ARG_CRITERIA + "[name$il" + URLEncoder.encode("'*" + species + "*'", "UTF-8") + "]");
        }

        static URL createProductQueryUrl(String product_id) throws IOException {
            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + "Product" +
                    ARG_CRITERIA + "[id$eq" + URLEncoder.encode("'" + product_id + "'", "UTF-8") + "]");
        }

        static URL createSectionDataSetsQuery(String treatment_name, Atlas.PlaneOfSection section) throws MalformedURLException {
            return addSectionDataSetInclusionAttributes(
                    new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + "SectionDataSet" +
                    ARG_CRITERIA + "[failed$eqfalse]," +
                                   "plane_of_section[name$eq" + section + "]," +
                                   "treatments[name$il" + treatment_name + "]"));
        }

        static URL createSectionDataSetsQuery(Integer product_id, Atlas.PlaneOfSection section) throws MalformedURLException {
            return addSectionDataSetInclusionAttributes(
                    new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + "SectionDataSet" +
                    ARG_CRITERIA + "[failed$eqfalse]," +
                                   "products[id$eq" + product_id + "]," +
                                   "plane_of_section[name$eq" + section + "]"));
        }

        static URL createSectionDataSetsQuery(Integer dataset_id) throws MalformedURLException {
            return addSectionDataSetInclusionAttributes(
                    new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                            ARG_MODEL + "SectionDataSet" +
                            ARG_CRITERIA + "[id$eq" + dataset_id + "]"));
        }

        private static URL addSectionDataSetInclusionAttributes(URL query) throws MalformedURLException {
            return new URL(query.toString() +
                    ARG_INCLUDE +
                    "alignment3d," +
                    "genes," +
                    "probes," +
                    "reference_space," +
                    "treatments," +
                    "specimen," +
                    "reference_space," +
                    "products");
        }

        static URL createSectionImagesQuery(String dataset_id) throws MalformedURLException {
            return addSectionImageInclusionAttributes(
                    new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                            ARG_MODEL + "SectionImage" +
                            ARG_CRITERIA + "data_set[id$eq" + dataset_id + "]"));
        }

        static URL createSectionImageQuery(String image_id) throws MalformedURLException {
            return addSectionImageInclusionAttributes(
                    new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                            ARG_MODEL + "SectionImage" +
                            ARG_CRITERIA + "[id$eq" + image_id + "]"));
        }

        private static URL addSectionImageInclusionAttributes(URL query) throws MalformedURLException {
            return new URL(query.toString() +
                    ARG_INCLUDE + "alignment2d,associates,structure");
        }

        static URL createSubImagesQueryUrl(String dataset_id) throws MalformedURLException {
            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + "SectionImage" +
                    ARG_CRITERIA + "[data_set_id$eq" + dataset_id + "]");
        }

        static URL createReferenceAtlasQueryUrl(Atlas.Species species) throws MalformedURLException {
            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + "Product" +
                    ARG_CRITERIA + "[name$il*Reference*][name$il*" + species + "*]");
        }

        static URL createdRegistedSampleQuery(String dataset_id) throws MalformedURLException {
            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + "WellKnownFile" +
                    ARG_CRITERIA + "[attachable_id$eq" +  dataset_id + "]" +
                    "[well_known_file_type_id$eq" + Download.WellKnownFileType.TYPE_ID__RESTAMPLED_IMAGES_TO_25UM_ARA + "]");
        }

        static URL createdRegistedSampleQuery() throws MalformedURLException {
            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
                    ARG_MODEL + "WellKnownFile" +
                    ARG_CRITERIA +
                    "[well_known_file_type_id$eq" + Download.WellKnownFileType.TYPE_ID__RESTAMPLED_IMAGES_TO_25UM_ARA + "]");
        }

        static URL createAtlasStructuresQuery(String product_id) throws MalformedURLException {
            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION +
            ARG_MODEL + "Structure" +
            ARG_CRITERIA + "ontology(products[id$eq" + product_id + "])");
        }

        static URL adjustResponseSize(URL url) throws IOException, TransformerException, URISyntaxException {
            String[] parts = url.toString().split(ARG_INCLUDE);
            URL query = new URL(parts[0] + ARG_OPTIONS + "[only$eq" + URLEncoder.encode("'id'", "UTF-8") + "]");

            AllenXml xml = new AllenXml(query);
            Object numRows = xml.getResponseSize();

            return new URL(url.toString() + ARG_OPTIONS + "[num_rows$eq" + numRows + "]");
        }

        static String url2filename(URL url) throws UnsupportedEncodingException {
            String str = url.toString();
            String[] parts = str.split("\\?" + ARG_MODEL.substring(1, ARG_MODEL.length()))[1].split(ARG_CRITERIA);

            String model = parts[0];

            String criteria = "";
            if (parts.length > 1) {
                parts = parts[1].split(ARG_INCLUDE);
                criteria = SEPARATOR;

                for (String criterium: parts[0].split(",")) {
                    String[] args = criterium.split("\\$");
                    String lhs = args[0];
                    String ope = args[1].substring(0, 2);
                    String rhs = URLDecoder.decode(args[1].substring(2, args[1].length() - 1), "UTF-8");
                    criteria += lhs + "$" + ope + rhs + "],";
                }

                criteria = criteria.substring(0, criteria.length() - 1);
            }

            String include = "";
            if (parts.length > 1) {
                include = SEPARATOR + parts[1];
            }

            return model + criteria + include + FILE_EXTENSION;
        }

        static URL filename2url(String str) throws MalformedURLException, UnsupportedEncodingException {
            String[] parts = str.replace(FILE_EXTENSION, "").split(SEPARATOR);
            String model = ARG_MODEL + parts[0];

            String criteria = "";
            if (parts.length > 1) {
                criteria += ARG_CRITERIA.substring(0, ARG_CRITERIA.length()-1);

                for (String criterium: parts[1].split(",")) {
                    String[] args = criterium.split("\\$");
                    String lhs = args[0];
                    String ope = args[1].substring(0, 2);
                    String rhs = URLEncoder.encode(args[1].substring(2, args[1].length() - 1), "UTF-8");
                    criteria += "," + lhs + "$" + ope + rhs + "]";
                }
            }

            String include = (parts.length > 2) ? ARG_INCLUDE + parts[2] : "";

            return new URL(BASE_URL + SUB_URL + FUN_QUERY + FILE_EXTENSION + model + criteria + include);
        }
    }


    /**
     * Aggregation of all download functionality
     */
    static class Download {

        /** Number of down-samplings [0...]. Each down-sampling halves the original data (1 -> 1/2 size) */
        private static final String ARG_DOWNSAMPLE = "?downsample=";

        /** Default down-sampling (convenient for display, otherwise it does not fit) */
        static final int ARG_DOWNSAMPLE_DEFAULT = 2;

        /** JPEG quality [0...100] */
        private static final String ARG_QUALITY = "&quality=";

        /** Default image quality value */
        static final int ARG_QUALITY_DEFAULT = 100;

        /**
         * Create a unique file name from the query URL
         *
         * @param url
         * @return
         */
        static String url2filename(URL url) {
            String str = url.toString();

            String fileExtension;
            if (str.contains(SVG.SUB_URL)) {
                fileExtension = SVG.FILE_EXTENSION;
            } else {
                fileExtension = Image.FILE_EXTENSION;
            }

            str = str.replace(BASE_URL, "")
                    .replace(SVG.SUB_URL, "")
                    .replace(Image.SUB_URL, "")
                    .replace(AtlasImage.SUB_URL, "");

            String[] parts = str.split("\\?");

            return parts[0] + fileExtension;
        }


        /**
         *
         */
        static class WellKnownFileType {

            static final String SUB_URL = "api/v2/well_known_file_download/";

            static final String TYPE_ID__RESTAMPLED_IMAGES_TO_25UM_ARA = "268364491";

            static URL createURL(String id) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + id);
            }
        }



        /**
         * URL's to retrieve expression grid data
         * Reference: http://help.brain-map.org/display/api/Downloading+3-D+Expression+Grid+Data
         */
        static class GRID {

            static final String FILE_EXTENSION = ".zip";

            /** Sub-URL for the expression grid data download */
            private static final String SUB_URL = "grid_data/download/";

            private static final String INCLUDE = "?include=intensity";

            static URL createUrl(String dataset_id) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + dataset_id + INCLUDE);
            }
        }


        /**
         * URL's to retrieve the reference volumes
         */
        static class RefVol {

            static final String BASE_URL = "http://download.alleninstitute.org/informatics-archive/current-release/mouse_ccf/";

            static final String FILE_EXTENSION = ".nrrd";

            static URL createUrl(Atlas.Modality type, Atlas.VoxelResolution resolution) throws MalformedURLException {
                return new URL(BASE_URL + type.getSubUrl() + createFileName(type, resolution));
            }

            static String createFileName(Atlas.Modality type, Atlas.VoxelResolution resolution) {
                return type.getFileTrunk() + resolution.getLabel().replace("um", "") + FILE_EXTENSION;
            }

            static String getFileName(String modality, String resolution) {
                Atlas.Modality type = Atlas.Modality.get(modality);
                Atlas.VoxelResolution voxelResolution = Atlas.VoxelResolution.get(resolution);

                return createFileName(type, voxelResolution);
            }
        }


        /**
         * URL's to retrieve SVG files
         */
        static class SVG {

            static final String FILE_EXTENSION = ".svg";

            /** Sub-URL for the SVG file getSvg */
            private static final String SUB_URL ="api/v2/svg_download/";

            /** Argument for annotation group selection */
            private static final String ARG_GROUP = "?groups=";

            static URL createSvgUrl(String section_id) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + section_id + ARG_DOWNSAMPLE + Integer.toString(ARG_DOWNSAMPLE_DEFAULT));
            }

            static URL createSvgUrl(String section_id, int downsample, int grp_id) throws MalformedURLException {
                return createSvgUrl(section_id, downsample, new int[]{grp_id});
            }

            static URL createSvgUrl(String section_id, int downsample, int[] grp_ids) throws MalformedURLException {
                String str = Integer.toString(grp_ids[0]);
                for (int i=1; i < grp_ids.length; i++) {
                    str += "," + Integer.toString(grp_ids[i]);
                }

                return createSvgUrl(section_id, Integer.toString(downsample), str);
            }

            static URL createSvgUrl(String section_id, String downsample, String grp_id) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + section_id + ARG_DOWNSAMPLE + downsample + ARG_GROUP + grp_id);
            }
        }


        /**
         * Aggregation of URL's to retrieve general brain section image files
         */
        static class Image {

            static final String FILE_EXTENSION = ".jpg";

            /** sub-URL for the image getSvg */
            private static final String SUB_URL = "api/v2/image_download/";

            /** Views of the image getSvg */
            private static final String ARG_VIEW_EXPR = "&view=expression";
            private static final String ARG_VIEW_PROJ = "&view=projection";
            private static final String ARG_VIEW_TUAN = "&view=tumor_feature_annotation";
            private static final String ARG_VIEW_TUBO = "&view=tumor_feature_boundary";

            static URL createImageUrl(String id) throws MalformedURLException {
                return createImageUrl(id, ARG_DOWNSAMPLE_DEFAULT, ARG_QUALITY_DEFAULT);
            }

            static URL createImageUrl(String id, int down_sample, int quality) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + id +
                        ARG_DOWNSAMPLE + Integer.toString(down_sample) +
                        ARG_QUALITY + Double.toString(quality));
            }

            static URL createExpressionUrl(String id) throws MalformedURLException {
                return createExpressionUrl(id, ARG_DOWNSAMPLE_DEFAULT, ARG_QUALITY_DEFAULT);
            }

            static URL createExpressionUrl(String id, int down_sample, double quality) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + id +
                        ARG_DOWNSAMPLE + Integer.toString(down_sample) +
                        ARG_QUALITY + Double.toString(quality) +
                        ARG_VIEW_EXPR);
            }

            static URL createProjectionUrl(String id) throws MalformedURLException {
                return createProjectionUrl(id, ARG_DOWNSAMPLE_DEFAULT, ARG_QUALITY_DEFAULT);
            }

            static URL createProjectionUrl(String id, int down_sample, double quality) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + id +
                        ARG_DOWNSAMPLE + Integer.toString(down_sample) +
                        ARG_QUALITY + Double.toString(quality) +
                        ARG_VIEW_PROJ);
            }

            static URL createTumorFeatureAnnotationUrl(String id) throws MalformedURLException {
                return createTumorFeatureAnnotationUrl(id,  ARG_DOWNSAMPLE_DEFAULT, ARG_QUALITY_DEFAULT);
            }

            static URL createTumorFeatureAnnotationUrl(String id, int down_sample, double quality) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + id +
                        ARG_DOWNSAMPLE + Integer.toString(down_sample) +
                        ARG_QUALITY + Double.toString(quality) +
                        ARG_VIEW_TUAN);
            }

            static URL createTumorFeatureBoundaryUrl(String id) throws MalformedURLException {
                return createTumorFeatureBoundaryUrl(id,  ARG_DOWNSAMPLE_DEFAULT, ARG_QUALITY_DEFAULT);
            }

            static URL createTumorFeatureBoundaryUrl(String id, int down_sample, double quality) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + id +
                        ARG_DOWNSAMPLE + Integer.toString(down_sample) +
                        ARG_QUALITY + Double.toString(quality) +
                        ARG_VIEW_TUBO);
            }
        }


        /**
         * Aggregation of URL's to retrieve atlas images (section images with annotations)
         */
        static class AtlasImage {

            static final String FILE_EXTENSION = ".jpg";

            /** sub-URL for the atlas image getSvg */
            private static final String SUB_URL = "api/v2/atlas_image_download/";

            /** Boolean to indicate to include annotations or not */
            private static final String ARG_ANNOTATION = "&annotation=";

            /** Atlas id argument */
            private static final String ARG_ATLAS_ID = "&atlas=";

            static URL createAtlasImageUrl(String atlas_id, String img_id) throws MalformedURLException {
                return createAtlasImageUrl(atlas_id, img_id, ARG_DOWNSAMPLE_DEFAULT, ARG_QUALITY_DEFAULT, false);
            }

            static URL createAtlasImageUrl(String atlas_id, String section_id,  int down_sample, double quality, boolean annotation) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + section_id +
                        ARG_DOWNSAMPLE + Integer.toString(down_sample) +
                        ARG_QUALITY + Double.toString(quality) +
                        ARG_ANNOTATION + Boolean.toString(annotation) +
                        ARG_ATLAS_ID + atlas_id);
            }
        }


        /**
         * Query for annotation structure graphs.
         */
        public static class StructureGraph {

            /** sub-URL for the structure graphs */
            private static final String SUB_URL = "api/v2/structure_graph_download/";

            static URL createStructureGraphUrl(String structure_graph_id) throws MalformedURLException {
                return new URL(BASE_URL + SUB_URL + structure_graph_id + RMA.FILE_EXTENSION);
            }
        }


        /**
         * Quick functionality test.
         *
         * @param args something
         */
        public static void main(String[] args)
                throws IOException,
                ParserConfigurationException,
                SAXException,
                TransformerException,
                URISyntaxException {

            URL query = AllenAPI.RMA.createProductQueryUrl(Atlas.Species.MOUSE);
            URL adjusted = AllenAPI.RMA.adjustResponseSize(query);
            AllenXml xml = new AllenXml(adjusted);

            String filename = AllenAPI.RMA.url2filename(xml.getUrl());
            URL url = AllenAPI.RMA.filename2url(filename);

            System.out.println("URL handling");
            System.out.println("\toriginal url: " + query.toString());
            System.out.println("\tmodel: " + xml.getResponseModel());
            System.out.println("\tresponse size: " + xml.getResponseSize());
            System.out.println("\tadjusted url: " + xml.getUrl());
            System.out.println("\tfile name: " + filename);
            System.out.println("\trestored url: " + url.toString());


            System.out.println("Mouse products: ");
            for (Element product : xml.getElements()) {
                System.out.println("\t" + product.getChild("name").getValue() +
                        " (" + product.getChild("id").getValue() + ")");
            }
        }
    }
}
