package log;

import org.scijava.app.StatusService;
import org.scijava.log.LogService;

/**
 * @author Felix Meyenhofer
 * created: 05.07.18.
 */
public class FeedbackGate {

    private LogService log;
    private StatusService status;

    protected FeedbackGate() {
        this.log = null;
        this.status = null;
    }

    public void setLogService(LogService logService) {
        this.log = logService;
    }

    public void setStatusService(StatusService statusService) {
        this.status = statusService;
    }

    protected LogService getLogService() {
        return this.log;
    }

    protected StatusService getStatusService() {
        return this.status;
    }

    protected void consoleAndStatusUpdate(String message) {
        statusUpdate(message);
        consoleOutput(message);
    }

    protected void consoleAndStatusUpdate(int n, int of, String msg) {
        statusUpdate(n, of, msg);
        consoleOutput(msg);
    }

    private void statusUpdate(String msg) {
        if (status != null) {
            this.status.showStatus(msg);
        }
    }

    public void statusUpdate(int n, int of, String msg) {
        if ( status != null) {
            this.status.showStatus(n, of, msg);
        }
    }

    private void consoleOutput(String out) {
        if (log == null) {
            System.out.println(out);
        } else {
            log.info(out);
        }
    }
}
