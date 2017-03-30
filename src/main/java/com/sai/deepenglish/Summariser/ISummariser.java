package com.sai.deepenglish.Summariser;

/**
 * An interface for doing text summarisation
 *
 * @author Nick Lothian
 */
public interface ISummariser {

    /**
     * Extract a summary from a string
     *
     * @param input A String
     * @param numSentences The number of sentences the summary should be
     * @return A summary
     */
    public String summarise(String input, int numSentences);

}