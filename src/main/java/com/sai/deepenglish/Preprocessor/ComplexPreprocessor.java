package com.sai.deepenglish.Preprocessor;

import java.util.StringTokenizer;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * Preprocesses the complex-based representation
 */
public class ComplexPreprocessor {
	
	/** 
	 * Pre-processing stuff
	 */
	public String getProcessed(String str, MaxentTagger tagger) {

		String toreturn = "";
		
		toreturn = getPOS(str, tagger);
		
		return toreturn;
	}
	
	/**
	 * Fetches the part of speech
	 */
	private String getPOS(String sample, MaxentTagger tagger) {
		
		String tagged = tagger.tagString(sample.trim().replaceAll(" +", " "));	
		StringTokenizer stk = new StringTokenizer(tagged);
		
		String output = "";
	
		while (stk.hasMoreTokens()) {

			String tmp = stk.nextToken();
			String tmp2 = tmp.replaceAll("[^A-Za-z_0-9]", "");
			
			output = output + tmp2 + " ";

			if (tmp.contains(".")) {
				output=output.concat(".");
			}
			if (tmp.contains("!")){
				output=output.concat("!");
			}
			if (tmp.contains(",")) {
				output=output.concat(",");
			}				
			if (tmp.contains("?")) {
				output=output.concat("?");
			}
			
		}
		
		return output;
	
	}
}