package com.sai.deepenglish.Summariser;

/**
 * @author Peter Leschev
 */
public interface ITokenizer {

    /**
     * <p>Splits up the string passed into the tokens which
     * have individual probabilities.</p>
     *
     * @return Should never return null, rather it should return an empty array of
     *         Strings if there aren't any elements to return.
     */
    public String[] tokenize(String input);

}