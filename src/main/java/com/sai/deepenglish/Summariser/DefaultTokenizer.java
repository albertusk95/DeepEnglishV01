package com.sai.deepenglish.Summariser;

import com.sai.deepenglish.Summariser.util.ToStringBuilder;

/**
 * @author Peter Leschev
 */
public class DefaultTokenizer implements ITokenizer {

    public static int BREAK_ON_WORD_BREAKS = 1;

    public static int BREAK_ON_WHITESPACE = 2;

    private int tokenizerConfig = -1;
    private String customTokenizerRegExp = null;

    /**
     * Constructor that using the BREAK_ON_WORD_BREAKS tokenizer config by default
     */
    public DefaultTokenizer() {
        this(BREAK_ON_WORD_BREAKS);
    }

    public DefaultTokenizer(int tokenizerConfig) {
        setTokenizerConfig(tokenizerConfig);
    }


    public void setTokenizerConfig(int tokConfig) {

        if (tokConfig != BREAK_ON_WORD_BREAKS && tokConfig != BREAK_ON_WHITESPACE) {
            throw new IllegalArgumentException("tokenConfiguration must be either BREAK_ON_WORD_BREAKS or BREAK_ON_WHITESPACE");
        }

        tokenizerConfig = tokConfig;
    }

    public String[] tokenize(String input) {

        String regexp = "";

        if (customTokenizerRegExp != null) {
            regexp = customTokenizerRegExp;
        } else if (tokenizerConfig == BREAK_ON_WORD_BREAKS) {
            regexp = "\\W";
        } else if (tokenizerConfig == BREAK_ON_WHITESPACE) {
            regexp = "\\s";
        } else {
            throw new IllegalStateException("Illegal tokenizer configuration. customTokenizerRegExp = null & tokenizerConfig = " + tokenizerConfig);
        }

        if (input != null) {
            String[] words = input.split(regexp);
            return words;

        } else {
            return new String[0];
        }
    }

    public String toString() {

        ToStringBuilder toStringBuilder = new ToStringBuilder(this);

        if (customTokenizerRegExp != null) {
            toStringBuilder = toStringBuilder.append("customTokenizerRegExp", customTokenizerRegExp);
        } else if (tokenizerConfig == BREAK_ON_WORD_BREAKS) {
            toStringBuilder = toStringBuilder.append("tokenizerConfig", "BREAK_ON_WORD_BREAKS");
        } else if (tokenizerConfig == BREAK_ON_WHITESPACE) {
            toStringBuilder = toStringBuilder.append("tokenizerConfig", "BREAK_ON_WHITESPACE");
        }

        return toStringBuilder.toString();
    }
}