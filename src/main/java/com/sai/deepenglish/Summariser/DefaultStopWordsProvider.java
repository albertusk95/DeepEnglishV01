package com.sai.deepenglish.Summariser;

import com.sai.deepenglish.Summariser.util.ToStringBuilder;

import java.util.Arrays;


/**
 * @author Nick Lothian
 * @author Peter Leschev
 */
public class DefaultStopWordsProvider implements IStopWordProvider {
    // This array is sorted in the constructor
    private String[] stopWords = { "a", "and", "the", "me", "i", "of", "if", "it", "is", "they", "there", "but", "or", "to", "this", "you", "in", "your", "on", "for", "as", "are", "that", "with", "have", "be", "at", "or", "was", "so", "out", "not", "an" };
    private String[] sortedStopWords = null;

    public DefaultStopWordsProvider() {
        sortedStopWords = getStopWords();
        Arrays.sort(sortedStopWords);
    }

    /**
     * getter method which can be overridden to
     * supply the stop words. The array returned by this
     * method is sorted and then used internally
     *
     * @return the array of stop words
     */
    public String[] getStopWords() {
        return stopWords;
    }

    public boolean isStopWord(String word) {
        if (word == null || "".equals(word)) {
            return false;
        } else {
            // search the sorted array for the word, converted to lowercase
            // if it is found, the index will be >= 0
            return (Arrays.binarySearch(sortedStopWords, word.toLowerCase()) >= 0);
        }
    }

    public String toString() {
        return new ToStringBuilder(this).append("stopWords.size()", sortedStopWords.length).toString();
    }
}