package com.sai.deepenglish.Spider;

import java.util.*;

public class Spider {

    // Fields
    private static final int MAX_PAGES_TO_SEARCH = 10;
    private Set<String> pagesVisited = new HashSet<String>();
    private List<String> pagesToVisit = new LinkedList<String>();

    private List<String> pageContainingKeyword = new ArrayList<String>();


    /**
     * Our main launching point for the Spider's functionality. Internally it creates spider legs
     * that make an HTTP request and parse the response (the web page).
     *
     * @param url - The starting point of the spider
     * @param searchWord - The word or string that you are searching for
     */
    public List<String> search(String url, String searchWord) {

        while(this.pagesVisited.size() < MAX_PAGES_TO_SEARCH) {

            String currentUrl;
            SpiderLeg leg = new SpiderLeg();

            if(this.pagesToVisit.isEmpty()) {
                currentUrl = url;
                this.pagesVisited.add(url);
            } else {
                currentUrl = this.nextUrl();
            }

            leg.crawl(currentUrl);

            boolean success = leg.searchForWord(searchWord);

            if(success) {

                // store the url in a list
                pageContainingKeyword.add(currentUrl);

                System.out.println(String.format("**Success** Word %s found at %s", searchWord, currentUrl));
                break;

            }

            this.pagesToVisit.addAll(leg.getLinks());

        }

        System.out.println("\n**Done** Visited " + this.pagesVisited.size() + " web page(s)");

        return pageContainingKeyword;

    }

    /**
     * Returns the next URL to visit (in the order that they were found). We also do a check to make
     * sure this method doesn't return a URL that has already been visited.
     *
     * @return
     */
    private String nextUrl() {
        String nextUrl;
        do {
            nextUrl = this.pagesToVisit.remove(0);
        } while(this.pagesVisited.contains(nextUrl));

        this.pagesVisited.add(nextUrl);
        return nextUrl;
    }
}