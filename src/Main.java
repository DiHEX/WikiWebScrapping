import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String url = "https://pl.wikipedia.org/wiki/Java";

        try {
            // Połączenie z witryną i pobranie HTML
            Document doc = Jsoup.connect(url).get();

            // Pobranie tytułu strony
            String title = doc.title();
            System.out.println("Page title: " + title);

            List<Elements> allScrappedElements = new ArrayList<>();

            // Pobranie pierwszych 50 linków na stronie
            Elements linksFromFirstScrapping = doc.body().select("div.mw-body-content a[href]");
            Elements firstPhaseOfScrapping = new Elements(linksFromFirstScrapping.stream()
                    .filter(link -> link.attr("href").contains("wiki"))
                    .filter(link -> !(link.attr("href").endsWith("jpg")
                            || link.attr("href").endsWith("png")
                            || link.attr("href").endsWith("svg")
                            || link.attr("href").endsWith("jpeg")
                            || link.attr("href").endsWith("webp")))
                    .limit(50).toList());

            allScrappedElements.add(firstPhaseOfScrapping);
            List<Elements> secondPhaseOfScraping = new ArrayList<>();

            for (Element link : firstPhaseOfScrapping) {
                String scrappedUrl = link.attr("href");
                if (!scrappedUrl.startsWith("http")) {
                    scrappedUrl = "https://pl.wikipedia.org" + scrappedUrl;
                }

                Document scrappedDoc = null;

                try {
                    scrappedDoc = Jsoup.connect(scrappedUrl).get();
                } catch (Exception e) {
                    System.out.println("An error occurred while fetching the webpage: " + e.getMessage());
                }

                if (scrappedDoc != null) {
                    Elements linksFromSecondScrapping = scrappedDoc.body().select("div.mw-body-content a[href]");
                    Elements secondLinks = new Elements(linksFromSecondScrapping.stream()
                            .filter(scrappedLink -> scrappedLink.attr("href").contains("wiki"))
                            .filter(scrappedLink -> !(scrappedLink.attr("href").endsWith("jpg")
                                    || scrappedLink.attr("href").endsWith("png")
                                    || scrappedLink.attr("href").endsWith("svg")
                                    || scrappedLink.attr("href").endsWith("jpeg")
                                    || scrappedLink.attr("href").endsWith("webp")))
                            .limit(10).toList());
                    secondPhaseOfScraping.add(secondLinks);
                }
            }

            allScrappedElements.addAll(secondPhaseOfScraping);
            List<Elements> thirdPhaseOfScraping = new ArrayList<>();

            for (Element link : secondPhaseOfScraping.stream().flatMap(x -> x.stream()).toList()) {
                String scrappedUrl = link.attr("href");
                if (!scrappedUrl.startsWith("http")) {
                    scrappedUrl = "https://pl.wikipedia.org" + scrappedUrl;
                }

                Document scrappedDoc = null;

                try {
                    scrappedDoc = Jsoup.connect(scrappedUrl).get();
                } catch (Exception e) {
                    System.out.println("An error occurred while fetching the webpage: " + e.getMessage());
                }

                if (scrappedDoc != null) {
                    Elements linksFromThirdScrapping = scrappedDoc.body().select("div.mw-body-content a[href]");
                    Elements thirdLinks = new Elements(linksFromThirdScrapping.stream()
                            .filter(scrappedLink -> scrappedLink.attr("href").contains("wiki"))
                            .filter(scrappedLink -> !(scrappedLink.attr("href").endsWith("jpg")
                                    || scrappedLink.attr("href").endsWith("png")
                                    || scrappedLink.attr("href").endsWith("svg")
                                    || scrappedLink.attr("href").endsWith("jpeg")
                                    || scrappedLink.attr("href").endsWith("webp"))).toList());
                    thirdPhaseOfScraping.add(thirdLinks);
                }
            }

            allScrappedElements.addAll(thirdPhaseOfScraping);

            int i = 1;
            System.out.println("\nLinks found on the page:");
            for (Elements scrappedElements : allScrappedElements) {
                for (Element link : scrappedElements) {
                    System.out.println(i + ": " + link.attr("href") + " - " + link.text());
                    i++;
                }
            }

        } catch (IOException e) {
            System.out.println("An error occurred while fetching the webpage: " + e.getMessage());
        }
    }
}
