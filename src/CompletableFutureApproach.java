import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class CompletableFutureApproach {
    private final String baseUrl;
    private final List<Elements> allScrapedElements;
    private final ExecutorService executor;
    private static final int THREAD_POOL_SIZE = 50;

    public CompletableFutureApproach(String baseUrl) {
        this.baseUrl = baseUrl;
        this.allScrapedElements = Collections.synchronizedList(new ArrayList<>());
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public void scrape() {
        try {
            // First phase - initial scraping
            CompletableFuture<Elements> firstPhaseFuture = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            Document doc = Jsoup.connect(baseUrl).get();
                            System.out.println("Page title: " + doc.title());
                            return getFirstPhaseLinks(doc);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }, executor)
                    .thenApply(links -> {
                        allScrapedElements.add(links);
                        System.out.println("First phase completed. Found " + links.size() + " links");
                        return links;
                    });

            // Second phase - process first phase links
            CompletableFuture<List<Elements>> secondPhaseFuture = firstPhaseFuture
                    .thenCompose(firstPhaseLinks -> {
                        List<CompletableFuture<Elements>> futures = firstPhaseLinks.stream()
                                .map(link -> CompletableFuture.supplyAsync(
                                        () -> scrapeSecondPhase(link), executor))
                                .collect(Collectors.toList());

                        return CompletableFuture.allOf(
                                        futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList()));
                    })
                    .thenApply(secondPhaseLinks -> {
                        allScrapedElements.addAll(secondPhaseLinks);
                        System.out.println("Second phase completed. Found " +
                                secondPhaseLinks.stream().mapToInt(Elements::size).sum() + " links");
                        return secondPhaseLinks;
                    });

            // Third phase - process second phase links
            CompletableFuture<Void> thirdPhaseFuture = secondPhaseFuture
                    .thenCompose(secondPhaseLinks -> {
                        List<Element> allSecondPhaseElements = secondPhaseLinks.stream()
                                .flatMap(elements -> elements.stream())
                                .collect(Collectors.toList());

                        List<CompletableFuture<Elements>> futures = allSecondPhaseElements.stream()
                                .map(link -> CompletableFuture.supplyAsync(
                                        () -> scrapeThirdPhase(link), executor))
                                .collect(Collectors.toList());

                        return CompletableFuture.allOf(
                                        futures.toArray(new CompletableFuture[0]))
                                .thenAccept(v -> {
                                    List<Elements> thirdPhaseLinks = futures.stream()
                                            .map(CompletableFuture::join)
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.toList());
                                    allScrapedElements.addAll(thirdPhaseLinks);
                                    System.out.println("Third phase completed. Found " +
                                            thirdPhaseLinks.stream().mapToInt(Elements::size).sum() + " links");
                                });
                    });

            // Wait for all phases to complete and print results
            thirdPhaseFuture.thenRun(this::printResults).join();

        } catch (Exception e) {
            System.err.println("Error during scraping: " + e.getMessage());
        } finally {
            shutdownExecutor();
        }
    }

    private Elements getFirstPhaseLinks(Document doc) {
        Elements links = doc.body().select("div.mw-body-content a[href]");
        return new Elements(links.stream()
                .filter(link -> link.attr("href").contains("wiki"))
                .filter(this::isNotImage)
                .limit(50)
                .collect(Collectors.toList()));
    }

    private Elements scrapeSecondPhase(Element link) {
        try {
            String url = normalizeUrl(link.attr("href"));
            Document doc = Jsoup.connect(url).get();
            Elements links = doc.body().select("div.mw-body-content a[href]");

            return new Elements(links.stream()
                    .filter(l -> l.attr("href").contains("wiki"))
                    .filter(this::isNotImage)
                    .limit(10)
                    .collect(Collectors.toList()));

        } catch (IOException e) {
            System.err.println("Error in second phase: " + e.getMessage());
            return null;
        }
    }

    private Elements scrapeThirdPhase(Element link) {
        try {
            String url = normalizeUrl(link.attr("href"));
            Document doc = Jsoup.connect(url).get();
            Elements links = doc.body().select("div.mw-body-content a[href]");

            return new Elements(links.stream()
                    .filter(l -> l.attr("href").contains("wiki"))
                    .filter(this::isNotImage)
                    .collect(Collectors.toList())); // No limit for third phase

        } catch (IOException e) {
            System.err.println("Error in third phase: " + e.getMessage());
            return null;
        }
    }

    private boolean isNotImage(Element link) {
        String href = link.attr("href").toLowerCase();
        return !(href.endsWith("jpg") || href.endsWith("png") ||
                href.endsWith("svg") || href.endsWith("jpeg") ||
                href.endsWith("webp"));
    }

    private String normalizeUrl(String url) {
        return url.startsWith("http") ? url : "https://pl.wikipedia.org" + url;
    }

    private void printResults() {
        System.out.println("\nAll scraped links:");
        int[] counter = {1};
        allScrapedElements.forEach(elements -> elements.forEach(link ->
                System.out.printf("%d: %s - %s%n",
                        counter[0]++,
                        link.attr("href"),
                        link.text())));

        long totalLinks = allScrapedElements.stream()
                .mapToLong(Elements::size)
                .sum();
        System.out.println("\nTotal number of links scraped: " + totalLinks);
    }

    private void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public static void main(String[] args) {
        String url = "https://pl.wikipedia.org/wiki/Java";
        CompletableFutureApproach scraper = new CompletableFutureApproach(url);
        scraper.scrape();
    }
}