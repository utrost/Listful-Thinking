package app.listful.scraping;

public class ScraperBusyException extends RuntimeException {
    public ScraperBusyException() {
        super("Website reading is busy. Please retry shortly.");
    }
}
