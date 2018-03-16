package rest;

import gui.SvgDisplay;

import io.scif.img.ImgIOException;

import org.apache.batik.transcoder.TranscoderException;
import org.jdom2.Element;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * RESTful client for the http://atlas.brain-map.org
 *
 * This class exposes a comprehensible set of methods to interact with the
 * Allen RESTful API.
 *
 * This is the singleton layer on top of the {@link AllenCache}. The cache takes care
 * of downloading the data (once) if it is queried.
 *
 * It aggregates the interactions that have to be executed with the Allen API
 * and exposes typical actions that are needed in the context of ImageJ
 *
 * For reference:
 * http://help.brain-map.org/display/api/Allen+Brain+Atlas+API
 *
 * @author Felix Meyenhofer
 */
public class AllenClient {

    /** Singleton instance of the class */
    private static AllenClient singleton = new AllenClient();

    /** Local cache for atlas data */
    private AllenCache cache = new AllenCache();

    /** Flag to indicate if a display is created or not */
    private boolean doDisplay = false;
    private SvgDisplay display;

    /** ImageJ logger service */
    private LogService log = null;

    /** ImageJ status service */
    private StatusService status = null;


    /**
     * Constructor
     */
    private AllenClient() {}

    public static AllenClient getInstance() {
        return singleton;
    }

    public void setLoggerService(LogService logService) {
        this.log = logService;
    }

    public void setStatusService(StatusService statusService) {
        this.status = statusService;
    }

    public void setSvgDisplay(boolean status) {
        this.doDisplay = status;
    }

    public File getSectionImageDirectory() {
        return cache.getDirectory(AllenCache.DataType.img);
    }

    public File getAnnotationGrid(String resolution) throws TransformerException, IOException, URISyntaxException {
        AllenImage allenImage = this.cache.getAnnotationGrid(resolution);
        return allenImage.getFile();
    }

    public AllenXml getDatasetMetadata(String product_name, String dataset_id)
            throws TransformerException, IOException, URISyntaxException {
        return cache.getImageMetadataXml(product_name, dataset_id);
    }

    public AllenXml getSectionImageMetadata(String product_name, String dataset_id, String section_id)
            throws TransformerException, IOException, URISyntaxException {
        return cache.getImageMetadataXml(product_name, dataset_id, section_id);
    }

    public AllenXml getAtlasAnnotationMetadata(String product_id)
            throws IOException, TransformerException, URISyntaxException {
        return cache.getResponseXml(AllenAPI.RMA.createAtlasStructuresQuery(product_id));
    }

    public AtlasStructureGraph getAnnotationStructureGraph(AllenAtlas atlas)
            throws IOException, TransformerException, URISyntaxException {
        String graph_id = atlas.getStructureGraphId().toString();
        AllenXml xml = cache.getStructureGraphXml("StructureGraph", graph_id);

        HashMap<Integer, AtlasStructure> graph = new HashMap<>();
        Element root = xml.getDom().getRootElement().getChild("structure");
        parseStructureXmlElements(graph, root, "/");

        return new AtlasStructureGraph(graph);
    }

    public AllenRefVol getReferenceVolume(Atlas.Modality modality, Atlas.VoxelResolution resolution)
            throws TransformerException, IOException, URISyntaxException {

        AllenImage img = cache.getReferenceVolume(modality, resolution);

        return new AllenRefVol(img.getFile());
    }

    public List<String> getProductList(String species) throws IOException, TransformerException, URISyntaxException {
        AllenXml mouse_products = cache.getResponseXml(AllenAPI.RMA.createProductQueryUrl(Atlas.Species.get(species)));

        List<String> products = new ArrayList<>();
        for (Element product : mouse_products.getElements()) {
            String product_name = product.getChild("name").getValue();
            String product_id = product.getChild("id").getValue();
            products.add(product_name + " (" + product_id + ")");
        }

        return products;
    }

    public List<String> getDatasetList(String product_id) throws IOException, TransformerException, URISyntaxException {
        URL query = AllenAPI.RMA.createSectionDataSetsQuery(Integer.parseInt(product_id), Atlas.PlaneOfSection.CORONAL);
        AllenXml datasets = cache.getResponseXml(query);

        List<String> list = new ArrayList<>();
        for (Element dataset_element : datasets.getElements()) {
            String dataset_id = dataset_element.getChild("id").getValue();
            list.add(dataset_id);
        }

        return list;
    }

    private void parseStructureXmlElements(HashMap<Integer, AtlasStructure> collector, Element element, String path) {
        AtlasStructure structure = new AtlasStructure(element);
        path += structure.getId() + "/";
        structure.setGraphPath(path);
        collector.put(structure.getId(), structure);

        Element children = element.getChild("children");
        if (children != null) {
            for (Object obj : children.getChildren()) {
                Element child = (Element) obj;
                parseStructureXmlElements(collector, child, path);
            }
        }
    }

    /**
     * Text output, gated to the stdout or the ImageJ logger
     *
     * @param message to output
     */
    private void tell(String message) {
        if (log == null) {
            System.out.println(message);
        } else {
            log.info(message);
        }

        if (status != null) {
            status.showStatus(message);
        }
    }

    private void tell(int n, int of, String msg) {
        if (status != null) {
            status.showStatus(n, of, msg);
        } else if (log != null) {
            log.info(msg);
        } else {
            System.out.println(msg);
        }
    }

    /**
     * Download a reference volume (average template) image
     *
     * @param resolution pixel resolution in um
     * @throws IOException
     * @throws URISyntaxException
     * @throws TransformerException
     */
    private void downloadMouseRefVol(Atlas.VoxelResolution resolution)
            throws IOException, URISyntaxException, TransformerException {
        Atlas.Modality[] types = {
                Atlas.Modality.AUTOFLUO,
                Atlas.Modality.NISSEL,
                Atlas.Modality.ANNOTATION};

        for (Atlas.Modality type : types) {
            String msg = cache.getReferenceVolume(type, resolution).getStatusMessage();
            tell(msg);
        }
    }

    /**
     * Download all the section images of a section dataset
     *
     * @param dataset_id id of the dataset
     * @param downsample down sampling of the images
     * @param quality jpg quality
     * @throws IOException
     * @throws TransformerException
     * @throws URISyntaxException
     */
    public void downloadSectionDataSet(String dataset_id, int downsample, int quality)
            throws IOException, TransformerException, URISyntaxException {

        URL query = AllenAPI.RMA.createSectionDataSetQuery(dataset_id);
        AllenXml datasetXml = cache.getResponseXml(query);
        Element dataset_element = datasetXml.getElements().get(0);
        String product_name = dataset_element.getChild("products").getChild("product").getChild("abbreviation").getValue();


        tell(0,0,"Downloading SectionDataset " + dataset_id);

        AllenXml sub_images = cache.getResponseXml(AllenAPI.RMA.createSectionImagesQuery(dataset_id));
        System.out.print("\t");
        int N = sub_images.getElements().size();
        int n = 1;
        for (Element image_element : sub_images.getElements()) {
            tell(n, N, "Downloading SectionDataset " + dataset_id);
            String image_id = image_element.getChild("id").getValue();
            cache.getImageMetadataXml(dataset_element, product_name, dataset_id, image_id);
            cache.getImage(downsample, quality, product_name, dataset_id, image_id);
            n++;
        }

        tell(N, N, "Downloaded SectionDataset " + dataset_id + "(" + N + " images)");
    }
  

    /**
     * Collection of procedures to get the reference data
     * TODO: use the command line arguments to selectively call the different download procedures.
     *
     * @param args
     * @throws IOException
     * @throws TransformerException
     * @throws URISyntaxException
     * @throws TranscoderException
     * @throws ImgIOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public static void main(String[] args)
            throws IOException,
            TransformerException,
            URISyntaxException,
            TranscoderException,
            ImgIOException,
            ParserConfigurationException,
            SAXException {

        AllenClient client = new AllenClient();


        // List all the products for mouse and the associated datasets
        AllenXml mouse_products = client.cache.getResponseXml(AllenAPI.RMA.createProductQueryUrl(Atlas.Species.MOUSE));
        client.tell("Mouse products: ");
        for (Element product : mouse_products.getElements()) {
            String product_name = product.getChild("name").getValue();
            String product_id = product.getChild("id").getValue();
            client.tell("\t" + product_name + " (" + product_id + ")");

            if (product_name.contains("Reference")) {
                AllenXml datasets = client.cache.getResponseXml(
                        AllenAPI.RMA.createSectionDataSetsQuery(Integer.parseInt(product_id), Atlas.PlaneOfSection.CORONAL));
                for (Element dataset : datasets.getElements()) {
                    String dataset_id = dataset.getChild("id").getValue();
                    client.tell("\t\t" + dataset_id);
                }
            }
        }


//        // List all the treatments
//        AllenXml treatments = client.cache.getResponseXml(AllenAPI.RMA.createModelQueryUrl("Treatment"));
//        client.tell("\nTreatments:");
//        for (Element treatment : treatments.getElements()) {
//            String name = treatment.getChild("name").getValue();
//            String tags = treatment.getChild("tags").getValue();
//            client.tell("\t" + name + " (" + tags + ")");
//        }


//        // Download the all mouse reference datasets for the given products
//        String[] product_ids = {"7", "12"};
//        client.tell("Download reference products");
//        for (String product_id : product_ids) {
//            URL query = AllenAPI.RMA.createProductQueryUrl(product_id);
//            AllenXml atlas = client.cache.getResponseXml(query);
//
//            String product_name = (String) atlas.getValue("abbreviation");
//            client.cache.getMetadataXml(atlas.getDom().getRootElement(), product_name);
//            client.tell("* " + product_name + " (" + product_id + ")");
//
//            query = AllenAPI.RMA.createSectionDataSetsQuery(Integer.parseInt(product_id), Atlas.PlaneOfSection.coronal);
//            AllenXml datasets = client.cache.getResponseXml(query);
//            client.downloadSectionDataSet(datasets, 5, 100);
//        }


//        client.tell("\nDownloading section datasets");
//        for (int id : new int[]{100141805}){
//            URL query = AllenAPI.RMA.createSectionDataSetsQuery(id);
//            AllenXml datasets = client.cache.getResponseXml(query);
//            client.downloadSectionDataSet(datasets, 0, 100);
//        }
        

//        // Download some grid data
//        AllenImage grid = client.cache.getGrid("72109410");
//        client.tell("Query: " + grid.getUrl());
//        client.tell("Downloaded " + grid.getFile());


//        // Download the Mouse reference data
//        client.tell("\nReference volume dataset:");
//        client.downloadMouseRefVol(AllenAPI.Download.RefVol.VoxelResolution.TEN);


//        // Download some section dataset with a particular treatment
//        client.tell("Download the NeuN dataset");
//        URL treatment_query = AllenAPI.RMA.createSectionDataSetsQuery("neun", Atlas.PlaneOfSection.coronal);
//        AllenXml treatment_datasets = client.cache.getResponseXml(treatment_query);
//        client.downloadSectionDataSet(treatment_datasets, 1, 100);


//        AllenSvg svg = client.getSvg(AllenAtlas.MOUSEP56S, id);
//        client.createMaskFile();
//        File absPath = client.cache.getPath(AllenAtlas.MOUSEP56S, AllenDataTypes.SVG, id);
//        SvgDisplay display = new SvgDisplay(svg.getFile(), true);
//        display.show();


//        // Get the dataset info for the available registered fluo volumes.
//        AllenXml wkfs = client.cache.getResponseXml(AllenAPI.RMA.createdRegistedSampleQuery());
//        for (Element wkf : wkfs.getElements()) {
//            int datasetId = Integer.parseInt(wkf.getChild("attachable-id").getValue());
//            AllenXml dataset = client.cache.getResponseXml(AllenAPI.RMA.createSectionDataSetsQuery(datasetId));
//
//            if (dataset.getElements().size() == 1) {
//                Element info = dataset.getElements().get(0);
//
//                System.out.println(info.getChild("id").getValue() + " " + info.getChild("specimen").getChild("name").getValue());
//                System.out.println("\tRed: " + info.getChild("red-channel").getValue());
//                System.out.println("\tGreen: " + info.getChild("green-channel").getValue());
//                System.out.println("\tBlue: " + info.getChild("blue-channel").getValue());
//                System.out.println("\tWell known file: " + wkf.getChild("id").getValue());
//            } else {
//                System.out.println("Skipping Well known file: " + wkf.getChild("id").getValue());
//            }
//        }


//        // Download a registered volume
//        URL query = AllenAPI.Download.WellKnownFileType.createURL("310037372");
//        AllenXml response = client.cache.getResponseXml(query);
//        client.tell(query.toString());
//        client.cache.getImage();

        client.tell("\nDone.");
    }
}
