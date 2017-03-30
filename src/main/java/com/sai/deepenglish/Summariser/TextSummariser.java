package com.sai.deepenglish.Summariser;

public class TextSummariser {

    private int nSentences;
    private String articleContent;

    public TextSummariser(int nSentences, String articleContent) {
        this.nSentences = nSentences;
        this.articleContent = articleContent;
    }

    public String getSummary() {

        ISummariser summ = new SimpleSummariser();
        String strSumm = summ.summarise(articleContent, nSentences);
        return strSumm;

    }

}