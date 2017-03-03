package rest;

import gui.SvgDisplay;

import io.scif.img.ImgIOException;

import org.apache.batik.transcoder.TranscoderException;
import org.jdom.Element;
import org.scijava.log.LogService;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;


/**
 * RESTful client for the http://atlas.brain-map.org
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

    /** Local cache for atlas data */
    private AllenCache cache;

    /** Flag to indicate if a display is created or not */
    private boolean doDisplay;
    private SvgDisplay display;

    /** ImageJ logger service */
    private LogService log;

    /**
     * Default constructor
     */
    private AllenClient() {
        this(null, true);
    }

    /**
     * Constructor
     * (Usually as used from the plugin)
     *
     * @param logger
     * @param doDisplay
     */
    public AllenClient(LogService logger, boolean doDisplay) {
        this.log = logger;
        this.doDisplay = doDisplay;
        this.cache = new AllenCache();

        if (doDisplay) {
            display = new SvgDisplay(false);
        }
    }

    /**
     * Text output, gated to the stdout or the ImageJ logger
     *
     * @param message
     */
    private void tell(String message) {
        if (log == null) {
            System.out.println(message);
        } else {
            log.info(message);
        }
    }

    private void downloadReferenceAtlas(String product_id, String product_name)
            throws IOException, TransformerException, URISyntaxException {

        URL query = AllenAPI.RMA.createSectionDataSetsQuery(Integer.parseInt(product_id), Atlas.PlaneOfSection.coronal);
        AllenXml datasets = cache.getResponseXml(query);
        for (Element dataset : datasets.getElements()) {
            String dataset_id = dataset.getChild("id").getValue();
            tell("\t\tdataset " + dataset_id);
            cache.getMetadataXml(dataset, product_name, dataset_id);

            AllenXml images = cache.getResponseXml(AllenAPI.RMA.createSectionImagesQuery(dataset_id));
            tell("\t\t\tdownloading");

            int n = 1;
            System.out.print("\t\t\t");
            for (Element image : images.getElements()) {
                String image_id = image.getChild("id").getValue();
                cache.getMetadataXml(image, product_name, dataset_id, image_id);
                cache.getImage(product_name, dataset_id, image_id);
                cache.getAnnotationSvg(product_name, dataset_id, image_id);
                System.out.print(".");

                if ((n % 50) == 0) {
                    System.out.print("\n\t\t\t");
                }

                n++;
            }

            System.out.print("\n");
        }
    }

    private void downloadMouseRefVol(AllenAPI.Download.RefVol.VoxelResolution resolution)
            throws IOException, URISyntaxException, TransformerException {
        AllenAPI.Download.RefVol.DataType[] types = {
                AllenAPI.Download.RefVol.DataType.template,
                AllenAPI.Download.RefVol.DataType.nissl,
                AllenAPI.Download.RefVol.DataType.annotation};

        for (AllenAPI.Download.RefVol.DataType type : types) {
            String msg = cache.getReferenceVolume(type, resolution).getStatusMessage();
            tell(msg);
        }
    }

    private void downloadSectionDataSet(String treatment_name)
            throws IOException, TransformerException, URISyntaxException {

        URL query = AllenAPI.RMA.createSectionDataSetsQuery(treatment_name, Atlas.PlaneOfSection.coronal);
        AllenXml datasets = cache.getResponseXml(query);

        tell("Download SectionDataSets:");
        for (Element dataset_element : datasets.getElements()) {
            String dataset_id = dataset_element.getChild("id").getValue();
            String product_name = dataset_element.getChild("products").getChild("product").getChild("abbreviation").getValue();
            cache.getMetadataXml(dataset_element, product_name, dataset_id);

            tell("\t" + dataset_id);
            AllenXml sub_images = cache.getResponseXml(AllenAPI.RMA.createSectionImagesQuery(dataset_id));
            System.out.print("\t");
            int n = 1;
            for (Element image_element : sub_images.getElements()) {
                System.out.print(".");
                String image_id = image_element.getChild("id").getValue();

                cache.getMetadataXml(dataset_element, product_name, dataset_id, image_id);
                cache.getImage(product_name, dataset_id, image_id);

                if ((n % 50) == 0) {
                    System.out.print("\n\t");
                }

                n++;
            }
            System.out.print("\n");
        }
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


        // List all the products for mouse
        AllenXml mouse_products = client.cache.getResponseXml(AllenAPI.RMA.createProductQueryUrl(Atlas.Species.Mouse));
        client.tell("Mouse products: ");
        for (Element product : mouse_products.getElements()) {
            client.tell("\t" + product.getChild("name").getValue() + " (" + product.getChild("id").getValue() + ")");
        }


//        // Download the mouse reference datasets
//        String[] product_ids = {"7", "12"};
//        client.tell("Download reference products");
//        for (String product_id : product_ids) {
//            URL query = AllenAPI.RMA.createProductQueryUrl(product_id);
//            AllenXml atlas = client.cache.getResponseXml(query);
//            String product_name = (String) atlas.getValue("abbreviation");
//            client.cache.getMetadataXml(atlas.getDom().getRootElement(), product_name);
//            client.tell("\t" + product_name + " (" + product_id + ")");
//            client.downloadReferenceAtlas(product_id, product_name);
//        }


        // List all the treatments
        AllenXml treatments = client.cache.getResponseXml(AllenAPI.RMA.createModelQueryUrl("Treatment"));
        client.tell("\nTreatments:");
        for (Element treatment : treatments.getElements()) {
            String name = treatment.getChild("name").getValue();
            String tags = treatment.getChild("tags").getValue();
            client.tell("\t" + name + " (" + tags + ")");
        }

//        // Download some grid data
//        AllenImage grid = client.cache.getGrid("72109410");
//        client.tell("Query: " + grid.getUrl());
//        client.tell("Downloaded " + grid.getFile());


        // Download the Mouse reference data
        client.tell("\nReference volume dataset:");
        client.downloadMouseRefVol(AllenAPI.Download.RefVol.VoxelResolution.TWENTYFIVE);


//        // Download some section dataset with a particular treatment
//        client.downloadSectionDataSet("neun");


//        AllenSvg svg = client.getSvg(AllenAtlas.MOUSEP56S, id);
//        client.createMaskFile();
//        File absPath = client.cache.getPath(AllenAtlas.MOUSEP56S, AllenDataTypes.SVG, id);
//        SvgDisplay display = new SvgDisplay(svg.getFile(), true);
//        display.show();

        client.tell("\nDone.");
    }
}
