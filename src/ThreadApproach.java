import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ThreadApproach {
    private final String baseUrl;
    private final List<Elements> allScrapedElements = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService executor;
    private final int THREAD_POOL_SIZE = 50; // Increased thread pool size
    private final ConcurrentHashMap<String, Boolean> visitedUrls = new ConcurrentHashMap<>();

    public ThreadApproach(String baseUrl) {
        this.baseUrl = baseUrl;
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public void scrape() {
        try {
            // First depth - initial scraping
            Document doc = Jsoup.connect(baseUrl).get();
            System.out.println("Page title: " + doc.title());

            Elements firstPhaseLinks = getFirstPhaseLinks(doc);
            allScrapedElements.add(firstPhaseLinks);
            System.out.println("First phase completed. Found " + firstPhaseLinks.size() + " links");

            // Second phase - scraping from first phase links
            CountDownLatch secondPhaseLatch = new CountDownLatch(firstPhaseLinks.size());
            List<Future<?>> secondPhaseFutures = new ArrayList<>();

            for (Element link : firstPhaseLinks) {
                Future<?> future = executor.submit(() -> {
                    try {
                        scrapeSecondPhase(link);
                    } finally {
                        secondPhaseLatch.countDown();
                    }
                });
                secondPhaseFutures.add(future);
            }

            // Wait for second phase completion
            secondPhaseLatch.await();
            for (Future<?> future : secondPhaseFutures) {
                try {
                    future.get();
                } catch (Exception e) {
                    // Continue with other futures if one fails
                }
            }

            System.out.println("Second phase completed.");

            // Third phase - scraping from second phase links
            List<Element> secondPhaseElements = allScrapedElements.stream()
                    .skip(1)
                    .flatMap(elements -> elements.stream())
                    .collect(Collectors.toList());

            CountDownLatch thirdPhaseLatch = new CountDownLatch(secondPhaseElements.size());
            List<Future<?>> thirdPhaseFutures = new ArrayList<>();

            for (Element link : secondPhaseElements) {
                Future<?> future = executor.submit(() -> {
                    try {
                        scrapeThirdPhase(link);
                    } finally {
                        thirdPhaseLatch.countDown();
                    }
                });
                thirdPhaseFutures.add(future);
            }

            // Wait for third phase completion
            thirdPhaseLatch.await();
            for (Future<?> future : thirdPhaseFutures) {
                try {
                    future.get();
                } catch (Exception e) {
                    // Continue with other futures if one fails
                }
            }

            System.out.println("Third phase completed.");
            printResults();

        } catch (Exception e) {
            System.err.println("Error during scraping: " + e.getMessage());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
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

    private void scrapeSecondPhase(Element link) {
        try {
            String url = normalizeUrl(link.attr("href"));
            Document doc = Jsoup.connect(url).get();
            Elements links = doc.body().select("div.mw-body-content a[href]");
            Elements filteredLinks = new Elements(links.stream()
                    .filter(l -> l.attr("href").contains("wiki"))
                    .filter(this::isNotImage)
                    .limit(10)
                    .collect(Collectors.toList()));

            synchronized (allScrapedElements) {
                allScrapedElements.add(filteredLinks);
            }
        } catch (IOException e) {
            System.err.println("Error in second phase: " + e.getMessage());
        }
    }

    private void scrapeThirdPhase(Element link) {
        try {
            String url = normalizeUrl(link.attr("href"));
            Document doc = Jsoup.connect(url).get();
            Elements links = doc.body().select("div.mw-body-content a[href]");
            Elements filteredLinks = new Elements(links.stream()
                    .filter(l -> l.attr("href").contains("wiki"))
                    .filter(this::isNotImage)
                    .collect(Collectors.toList())); // No limit in third phase

            synchronized (allScrapedElements) {
                allScrapedElements.add(filteredLinks);
            }
        } catch (IOException e) {
            System.err.println("Error in third phase: " + e.getMessage());
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
        int totalLinks = 0;
        int linkCounter = 1;
        System.out.println("\nAll scraped links:");

        for (Elements elements : allScrapedElements) {
            for (Element link : elements) {
                System.out.printf("%d: %s - %s%n",
                        linkCounter++, link.attr("href"), link.text());
                totalLinks++;
            }
        }

        System.out.println("\nTotal number of links scraped: " + totalLinks);
    }

    public static void main(String[] args) {
        String url = "https://pl.wikipedia.org/wiki/Java";
        ThreadApproach scraper = new ThreadApproach(url);
        scraper.scrape();
    }
}