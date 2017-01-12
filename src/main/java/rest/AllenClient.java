package rest;

import gui.SvgDisplay;

import io.scif.img.ImgIOException;

import org.apache.batik.transcoder.TranscoderException;
import org.scijava.log.LogService;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

/**
 * RESTful client for the http://atlas.brain-map.org
 *
 * It aggregates the interactions that have to be executed with the Allen API
 * and exposes typical actions that are needed in the context of ImageJ
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
     * Constructor
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
     * Log output, gating to the stdout or the ImageJ logger
     *
     * @param message
     */
    private void print(String message) {
        if (log == null) {
            System.out.println(message);
        } else {
            log.info(message);
        }
    }

    /**
     * Make the {@link AllenAPI} main URL accessible for
     * loging purposes
     *
     * @return
     */
    public String getApiUrl() {
        return AllenAPI.BASE_URL;
    }


    public AllenSvg getSvg(String atlasName, String sectionId) throws TransformerException, IOException, URISyntaxException {
        AllenAtlas atlas = AllenAtlas.getByName(atlasName);
        return getSvg(atlas, sectionId);
    }

    public AllenSvg getSvg(AllenAtlas atlas, String sectionId) throws IOException, URISyntaxException, TransformerException {
        File file = cache.getPath(atlas, sectionId, AllenDataTypes.SVG);
        AllenSvg svg;

        if (file.exists()) {
            print("Loading from cache: " + file);
            svg = new AllenSvg(file);
        } else {
            URL url = AllenAPI.Download.SVG.createSvgUrl(sectionId);
            print("Downloading from Allen: " + url);
            svg = new AllenSvg(url, file);
            print("Cached SVG locally: " + file.getAbsolutePath());
        }

        return svg;
    }

//    public AllenImage getImageFile(AllenAtlas atlas, String imageId) throws IOException, URISyntaxException, TransformerException {
//        File file = cache.getPath(atlas, imageId, AllenDataTypes.IMG);
//        AllenImage imageFile;
//
//        if (file.exists()) {
//            imageFile = new AllenImage(file);
//            print("Image file already cached: " + file);
//        } else {
////            URL url = AllenAPI.AtlasImage.createAtlasImageUrl(atlas, imageId);
//            URL url = AllenAPI.Download.Image.createExpressionUrl(imageId);
//            print("Downloading atlas image: " + url);
//            imageFile = new AllenImage(url, file);
//            print("Cached atlas image locally: " + file);
//        }
//
//        return imageFile;
//    }
//
//    private ArrayList<String> getAnnotatedSectionImageIds(AllenAtlas atlas) throws ParserConfigurationException, SAXException, IOException, TransformerException, URISyntaxException {
//        ArrayList<String> ids = new ArrayList<>();
//        for (Object obj : AllenAPI.RMA.getAnnotatedSectionImageIds(atlas)) {
//            ids.add(obj.toString());
//        }
//
//        return ids;
//    }

    //TODO: this is for testing only. It will need ultimatly to  be replaced with one or several methods also producing GUI's to gather user input
    private void selectSection() throws IOException, TransformerException, URISyntaxException {
        // Get the products for a given species
        URL query = AllenAPI.RMA.createProductQueryUrl(Atlas.Species.Mouse);
        AllenJson products = cache.getResponse(query);

        // Get the data sets for a given product
//        query = AllenAPI.RMA.createDataSetsQueryUrl(Atlas.DEFAULT_PRODUCT_ID, Atlas.PlaneOfSection.coronal);
//        query = AllenAPI.RMA.createDataSetsQueryUrl(1, Atlas.PlaneOfSection.coronal);
//        AllenJson dataSets = cache.getResponse(query);
//        ArrayList<Object> dataSetIds = dataSets.getValues("id");

        // Get the section images
        int num = 0;
//        query = AllenAPI.RMA.createSectionImagesQueryUrl((Integer) dataSetIds.get(num));
        query = AllenAPI.RMA.createSectionImagesQueryUrl(79912554);
        AllenJson sectionImages = cache.getResponse(query);

        String dataSetId = "78557206";//dataSetIds.get(num).toString();
        for (Object obj : sectionImages.getValues("id")) {
            URL url = AllenAPI.Download.Image.createImageURL(obj.toString());
            AllenImage img = cache.getImage(url, dataSetId);
            System.out.println(img.getStatusMessage());
        }

        System.out.println("here");
    }


    public static void main(String[] args) throws IOException, TransformerException, URISyntaxException, TranscoderException, ImgIOException, ParserConfigurationException, SAXException {

        AllenClient client = new AllenClient();
        client.selectSection();

//        AllenAtlas atlas = AllenAtlas.MOUSEP56C;
//        AllenClient client = new AllenClient();
//        ArrayList<String> ids = client.getAnnotatedSectionImageIds(atlas);
//
//        for (String id : ids) {
//            client.getImageFile(atlas, id);
//            client.getSvg(atlas, id);
//        }


//        AllenSvg svg = client.getSvg(AllenAtlas.MOUSEP56S, id);
//        client.createMaskFile();
//        File absPath = client.cache.getPath(AllenAtlas.MOUSEP56S, AllenDataTypes.SVG, id);
//        SvgDisplay display = new SvgDisplay(svg.getFile(), true);
//        display.show();
    }
}
