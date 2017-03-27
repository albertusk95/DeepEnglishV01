package com.sai.deepenglish.Preprocessor;

import java.io.IOException;
import java.util.ArrayList;

import weka.core.Attribute;
import weka.core.Instances;
import weka.core.SparseInstance;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class QuestionPreprocessor {
	
	private String question;
	private String pathToAllResources;
	
	private ComplexPreprocessor cp;
	
	private Instances complex_instances;
	private Instances text_instances;
	
	private MaxentTagger tagger;
	
	public QuestionPreprocessor(String pathToAllResources) {
		
		System.out.println("Hi from QuestionPreprocessor!\n");
		
		this.pathToAllResources = pathToAllResources;
		
		cp = new ComplexPreprocessor();
		
		// Initialize the tagger
		tagger = new MaxentTagger("taggers/english-left3words-distsim.tagger");

		//tagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger");
	}
	
	/** 
	 * Getter 
	 * Get all instances created before (instances that contains data test)
	 */
	public Instances[] getAllInstances() {
		
		Instances[] all = new Instances[2];
		
		all[0] = text_instances;
		all[1] = complex_instances;
		
		return all;
	
	}
	
	public void setQuestion(String t) {
		question = t;
	}
	
	public void startProc(){
	
		String processed_text = getTextInstances();	
		getComplexInstances(processed_text);
	
	}
	
	/*
	 * Initializes the text-based Instances
	 */
	private String getTextInstances() {
		
		ArrayList<Attribute> atts = new ArrayList<Attribute>(2);
        ArrayList<String> classVal = new ArrayList<String>();
        classVal.add("0");
        classVal.add("1");
        atts.add(new Attribute("eligibilityClassAttribute",classVal));
        atts.add(new Attribute("text",(ArrayList<String>)null));
	    
        // create instances (relation) with name TextInstances with attributes atts and initial size 0
        Instances textRaw = new Instances("TextInstances",atts,0);
        
        // preprocesses the tweet 
        double[] instanceValue1 = new double[textRaw.numAttributes()];
        
        // NO NEED TO DO THIS FOR THE CASE OF EDUCATION ARTICLE
		//String tmp_txt = tp.getProcessed(question);
        
		String tmp_txt = question;
		
		//System.out.println("Preprocessed text: " + tmp_txt);
        
		// initialize the instance containing the tweet
        instanceValue1[1] = textRaw.attribute(1).addStringValue(tmp_txt);
        
		// store the instance within the instances
		textRaw.add(new SparseInstance(1.0, instanceValue1));
		
		text_instances = new Instances(textRaw);
		
        return tmp_txt;
	
	}
	
	/*
	 * Initiates the complex-based Instances
	 */
	private void getComplexInstances(String processed_text) {
		
		ArrayList<Attribute> atts = new ArrayList<Attribute>(2);
        ArrayList<String> classVal = new ArrayList<String>();
        classVal.add("0");
        classVal.add("1");
        atts.add(new Attribute("eligibilityClassAttribute",classVal));
        atts.add(new Attribute("text",(ArrayList<String>)null));
		
        Instances textRaw = new Instances("TextInstances",atts,0);
        
		double[] instanceValue1 = new double[textRaw.numAttributes()];
        
		// preprocesses the complex-based tweet
        String tmp_cmplx = cp.getProcessed(processed_text, tagger);
		
        System.out.println("Preprocessed complex: " + tmp_cmplx);
        
        instanceValue1[1] = textRaw.attribute(1).addStringValue(tmp_cmplx);
        textRaw.add(new SparseInstance(1.0, instanceValue1));
		complex_instances = new Instances(textRaw);
		
	}
	
}