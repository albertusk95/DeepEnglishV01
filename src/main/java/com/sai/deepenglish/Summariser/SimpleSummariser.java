package com.sai.deepenglish.Summariser;

import java.util.*;

public class SimpleSummariser implements ISummariser {


    protected Set getMostFrequentWords(int count, Map wordFrequencies) {
        return Utilities.getMostFrequentWords(count, wordFrequencies);
    }


    public String summarise(String input, int numSentences) {
        // get the frequency of each word in the input
        Map wordFrequencies = Utilities.getWordFrequency(input);

        // now create a set of the X most frequent words
        Set mostFrequentWords = getMostFrequentWords(100, wordFrequencies);

        // break the input up into sentences
        // workingSentences is used for the analysis, but
        // actualSentences is used in the results so that the
        // capitalisation will be correct.
        String[] workingSentences = Utilities.getSentences(input.toLowerCase());
        String[] actualSentences = Utilities.getSentences(input);

        // iterate over the most frequent words, and add the first sentence
        // that includes each word to the result
        Set outputSentences = new LinkedHashSet();
        Iterator it = mostFrequentWords.iterator();
        while (it.hasNext()) {
            String word = (String) it.next();
            for (int i = 0; i < workingSentences.length; i++) {
                if (workingSentences[i].indexOf(word) >= 0) {
                    outputSentences.add(actualSentences[i]);
                    break;
                }
                if (outputSentences.size() >= numSentences) {
                    break;
                }
            }
            if (outputSentences.size() >= numSentences) {
                break;
            }

        }

        List reorderedOutputSentences = reorderSentences(outputSentences, input);

        StringBuffer result = new StringBuffer("");
        it = reorderedOutputSentences.iterator();
        while (it.hasNext()) {
            String sentence = (String) it.next();
            result.append(sentence);
            result.append("."); // This isn't always correct - perhaps it should be whatever symbol the sentence finished with
            if (it.hasNext()) {
                result.append(" ");
            }
        }

        return result.toString();
    }

    /**
     * @param outputSentences
     * @param input
     * @return
     */
    private List reorderSentences(Set outputSentences, final String input) {
        // reorder the sentences to the order they were in the
        // original text
        ArrayList result = new ArrayList(outputSentences);

        Collections.sort(result, new Comparator() {
            public int compare(Object arg0, Object arg1) {
                String sentence1 = (String) arg0;
                String sentence2 = (String) arg1;

                int indexOfSentence1 = input.indexOf(sentence1.trim());
                int indexOfSentence2 = input.indexOf(sentence2.trim());
                int result = indexOfSentence1 - indexOfSentence2;

                return result;
            }

        });
        return result;
    }

}