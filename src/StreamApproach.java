import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamApproach {
    private final String baseUrl;
    private final List<Elements> allScrapedElements;

    public StreamApproach(String baseUrl) {
        this.baseUrl = baseUrl;
        this.allScrapedElements = new ArrayList<>();
    }

    public void scrape() {
        try {
            // First phase - get initial document
            Document doc = Jsoup.connect(baseUrl).get();
            System.out.println("Page title: " + doc.title());

            // First phase - get 50 links using stream
            Elements firstPhaseLinks = doc.body().select("div.mw-body-content a[href]").stream()
                    .filter(link -> link.attr("href").contains("wiki"))
                    .filter(this::isNotImage)
                    .limit(50)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            Elements::new
                    ));

            allScrapedElements.add(firstPhaseLinks);
            System.out.println("First phase completed. Found " + firstPhaseLinks.size() + " links");

            // Second phase - process each first phase link
            List<Elements> secondPhaseLinks = firstPhaseLinks.stream()
                    .map(this::scrapeSecondPhase)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            allScrapedElements.addAll(secondPhaseLinks);
            System.out.println("Second phase completed. Found " +
                    secondPhaseLinks.stream().mapToInt(Elements::size).sum() + " links");

            // Third phase - process all second phase links
            List<Elements> thirdPhaseLinks = secondPhaseLinks.stream()
                    .flatMap(elements -> elements.stream())
                    .map(this::scrapeThirdPhase)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            allScrapedElements.addAll(thirdPhaseLinks);
            System.out.println("Third phase completed. Found " +
                    thirdPhaseLinks.stream().mapToInt(Elements::size).sum() + " links");

            printResults();

        } catch (IOException e) {
            System.err.println("Error during scraping: " + e.getMessage());
        }
    }

    private Elements scrapeSecondPhase(Element link) {
        try {
            String url = normalizeUrl(link.attr("href"));
            Document doc = Jsoup.connect(url).get();

            return doc.body().select("div.mw-body-content a[href]").stream()
                    .filter(l -> l.attr("href").contains("wiki"))
                    .filter(this::isNotImage)
                    .limit(10)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            Elements::new
                    ));

        } catch (IOException e) {
            System.err.println("Error in second phase for URL " + link.attr("href") + ": " + e.getMessage());
            return null;
        }
    }

    private Elements scrapeThirdPhase(Element link) {
        try {
            String url = normalizeUrl(link.attr("href"));
            Document doc = Jsoup.connect(url).get();

            return doc.body().select("div.mw-body-content a[href]").stream()
                    .filter(l -> l.attr("href").contains("wiki"))
                    .filter(this::isNotImage)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            Elements::new
                    ));

        } catch (IOException e) {
            System.err.println("Error in third phase for URL " + link.attr("href") + ": " + e.getMessage());
            return null;
        }
    }

    private boolean isNotImage(Element link) {
        return Stream.of("jpg", "png", "svg", "jpeg", "webp")
                .noneMatch(ext -> link.attr("href").toLowerCase().endsWith(ext));
    }

    private String normalizeUrl(String url) {
        return url.startsWith("http") ? url : "https://pl.wikipedia.org" + url;
    }

    private void printResults() {
        System.out.println("\nAll scraped links:");

        int[] counter = {1}; // Using array to modify in lambda
        allScrapedElements.stream()
                .flatMap(elements -> elements.stream())
                .forEach(link -> System.out.printf("%d: %s - %s%n",
                        counter[0]++,
                        link.attr("href"),
                        link.text()));

        long totalLinks = allScrapedElements.stream()
                .mapToLong(Elements::size)
                .sum();

        System.out.println("\nTotal number of links scraped: " + totalLinks);
    }

    public static void main(String[] args) {
        String url = "https://pl.wikipedia.org/wiki/Java";
        StreamApproach scraper = new StreamApproach(url);
        scraper.scrape();
    }
}