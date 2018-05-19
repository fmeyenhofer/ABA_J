import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author Felix Meyenhofer
 */
public class URLBrowserTest {
    public static void main(String[] args) throws IOException, URISyntaxException {

        //    String coordString = coordinate2string(templateCoord);
        String uris = "http://mouse.brain-map.org/agea?seed=P56,6700,4100,5600&map1=P56,6700,4100,5600,[0.7,1]";
        URL url = new URL(uris);
        System.out.println(uris);

        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(url.toURI());
        }
    }
}
