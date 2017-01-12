package rest;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Helper Class managing the paths to the data files in the local root.
 *
 * @author Felix Meyenhofer
 */
class AllenCache {

    /** Root directory of the cache */
    private File root;

    /** Directory for the RMA query result files */
    private File rmadir;

    /** Directory to hold the section images */
    private File imgdir;

    /**
     * Constructor
     */
    AllenCache() {
        this.root = new File(new File(System.getProperty("user.home")), "allen-cache"); //TODO put this in the fiji user settings. Use a setting dialog if not defined
        ensureExistence(this.root);

        this.rmadir = new File(this.root, "RMA");
        ensureExistence(this.rmadir);

        this.imgdir = new File(this.root, "IMG");
        ensureExistence(this.imgdir);
    }

    /**
     * Generate a file path in the root
     *
     * @param atlas identifier for the atlas
     * @param id section id
     * @param type type of data (svg or image)
     * @return path to a unique root file
     */
    File getPath(AllenAtlas atlas, String id, AllenDataTypes type) {
        File atlasDir = new File(this.root, Integer.toString(atlas.getId()));
        ensureExistence(atlasDir);

        return new File(atlasDir, id + type.getFileExtension());
    }

    AllenImage getImage(URL url, String dataSetId) throws IOException, URISyntaxException, TransformerException {
        String filename = AllenAPI.Download.url2filename(url);
        File subdir = new File(this.imgdir, dataSetId);
        ensureExistence(subdir);

        File path = new File(subdir, filename);


        if (path.exists()) {
            return new AllenImage(path);
        } else {
            return new AllenImage(url, path);
        }
    }

    AllenJson getResponse(URL url) throws IOException, URISyntaxException, TransformerException {
        // Create a file name to save the query response
        String filename = AllenAPI.RMA.url2filename(url);
        File path = new File(this.rmadir, filename);

        // Adjust the query so that it includes all the results
        url = AllenAPI.RMA.adjustResponseSize(url);

        if (path.exists()) {
            return new AllenJson(path);
        } else {
            return new AllenJson(url, path);
        }
    }

    /**
     * Helper function to check if the cache directory exist and create it if not
     *
     * @param dir to check
     */
    private void ensureExistence(File dir) {
        if (!dir.exists()) {
            boolean status = dir.mkdir();
            if (!status) {
                throw new RuntimeException("Could not create the root directory at ' " + dir.getAbsolutePath() + "'");
            }
        }
    }
}
