package com.sai.deepenglish.Classifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import java.util.*;

//import org.apache.commons.collections4.BidiMap;
//import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import weka.classifiers.Classifier;
//import weka.classifiers.functions.LibSVM;
import weka.core.Instances;
import weka.core.SparseInstance;
import weka.core.tokenizers.NGramTokenizer;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToWordVector;

public class PolarityClassifier {

	private String pathToAllResources;
	
	private Map<String, Integer> tba;
	private Map<String, Integer> cba;
	
	private Classifier[] mnb_classifiers;
	
	private Instances[] text;
	private Instances[] complex;
	
	private Instances training_text;
	private Instances training_complex;
	
	private StringToWordVector stwv;
	
	private double predictionCertainty;

	/*
	 * Constructor of the class. 
	 * tba and cba refer to the (attribute-->index) relations.
	 */
	public PolarityClassifier(String pathToAllResources, Map<String, Integer> tb, Map<String, Integer> cb){
		
		System.out.println("Welcome to PolarityClassifier!\n");
		
		// resource directory
		this.pathToAllResources = pathToAllResources;
		
		// initiate BidiMap objects for tba and cba
		initializeAttributes(tb, cb);
		
		// initiate Instances objects for text, feature, and complex representation
		text = new Instances[2];
		complex = new Instances[2];	
		
		// initiate StringToWordVector as the filter and NGramTokenizer as the tokenizer
		initialiseTextFilter();
		
		// initiate MNB classifier from the previous built model
		initializeClassifiers();
	}
	
	public double getPredictionCertainty() {
		return predictionCertainty;
	}
	
	/*
	 * Begins the algorithm
	 */
	public String test(Instances[] all) {
		
		System.out.println("Begin the main algorithm...\n");
		
		String output = "";
		
		/**
		 * get the instances of every data test representation (text and complex)
		 * and apply a filter to it
		 */
		try {
			
			text[0] = getText(all[0]);
			complex[0] = getComplex(all[1]);
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		reformatText(text[0]);
		reformatComplex(complex[0]);
		
		try {
			
			output = apply();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return output;
	
	}
	
	
	/*
	 * The main method that sets up all the processes of the Ensemble classifier. 
	 * Returns the decision made by the two classifiers
	 */
	private String apply() throws Exception {
		
		double[] hc = applyHC();		// applies the HC and returns the results
		
		double content_true_vals = (hc[1]+hc[3])/62.02;
		double content_false_vals = (hc[0]+hc[2])/62.02;
		double hc_val = (1 + content_true_vals - content_false_vals) / 2;
		
		String output = "";
		
		if (hc_val < 0.5) {
			output = "0";
		} else if (hc_val > 0.5) {
			output = "1";
		} else {
			output = "1";
		}
		
		predictionCertainty = hc_val;
		
		return output;
	
	}
	
	/*
	 * Applies the learned MNB models and returns the output of HC
	 * mnb_classifiers[0] - text representation
	 * mnb_classifiers[1] - complex representation
	 */
	private double[] applyHC() throws Exception{
		
		double[] scores = new double[4];
		
		for (int i = 0; i < mnb_classifiers.length; i++) {
			
			Instances test = null;
			
			if (i == 0) {
				test = text[1];
			} else {
				test = complex[1];
			}
			
			test.setClassIndex(0);	
			
			// gets the probabilities for each class (0 / 1)
			double[] preds = mnb_classifiers[i].distributionForInstance(test.get(0));
			
			// classifyInstance
			System.out.println("TEST.GET(0): " + test.get(0));
			double classifyInstanceResult = mnb_classifiers[i].classifyInstance(test.get(0));
			if (i == 0) {
				System.out.println("classifyInstance from text: " + classifyInstanceResult);
			} else {
				System.out.println("classifyInstance from complex: " + classifyInstanceResult);
			}
			
			if (i == 0) {
				
				scores[0] = preds[0]*31.07;
				scores[1] = preds[1]*31.07;
				
				// add the class distribution into list of class distribution for text
				System.out.println("text false (0): " + preds[0]);
				System.out.println("text true (1): " + preds[1]);
				
			} else {
				
				scores[2] = preds[0]*30.95;
				scores[3] = preds[1]*30.95;
				
				// add the class distribution into list of class distribution for complex
				System.out.println("complex false (0): " + preds[0]);
				System.out.println("complex true (1): " + preds[1]);
				
			}
		}
		
		return scores;
	
	}
	
	/*
	 * Alters the order of the text representation's attributes according to the train files
	 * tba contains:
	 * [0 and, 17]
	 * [0 but, 18]
	 * [0 chiefs, 19]
	 */
	private void reformatText(Instances text_test) {
		
		// remove the attributes from the test set that are not used in the train set
		String[] options = new String[2];
		options[0] = "-R";
		String opt = "";
		boolean found = false;
		
		/**
		 * checks whether tba contains any attributes from data test
		 * and initializes options that will be used as the removal parameters
		 * Case example:
		 * - opt will contain 1, 2, 3,
		 * - options[1] will contain 1, 2, 3
		 */
		for (int j = 0; j < text_test.numAttributes(); j++) {
			
			if (tba.get(text_test.attribute(j).name()) == null) {
				int pos = j + 1;
				found = true;
				opt = opt + pos + ",";
			}
			
		}
		
		if (found == true) {
			options[1] = opt.substring(0, opt.length()-1);
		} else {
			options[1] = "";
		}
		
		// initializes remove's object and start to remove the attributes based on the given options
		Remove remove = new Remove();
		
		try {
			
			remove.setOptions(options);
			remove.setInputFormat(text_test);
			
			Instances newData = Filter.useFilter(text_test, remove);
			
			double[] values = new double[tba.size()];			
			
			for (int at = 0; at < newData.numAttributes(); at++) {			
				int pos = tba.get(newData.attribute(at).name());		// get the index of this attribute in the train set
				values[pos] = newData.get(0).value(at);					// and its value
			}
			
			training_text.add(0, new SparseInstance(1.0, values));
			text[1] = new Instances(training_text,0,1);
			training_text.remove(0);
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Alters the order of the complex representation's attributes according to the train files
	 *
	 * complex_text contains the data test which has been filtered and tokenized
	 * ex: this is a sample -> use stwv and ngram = 2
	 * The attributes:
	 * this is
	 * is a
	 * a sample
	 *
	 * This method will remove the attribute if any of those attributes can not be found in the training data
	 * Afterwards, the method will find the index of the attributes in the training data with its value
	 * Save the result in a new instances (complex[1])
	 */
	private void reformatComplex(Instances complex_test) { 
		
		// remove the attributes from the test set that are not used in the train set
		String[] options = new String[2];
		options[0] = "-R";
		
		String opt = "";
		boolean found = false;
		
		for (int j = 0; j < complex_test.numAttributes(); j++) {
			
			if (cba.get(complex_test.attribute(j).name()) == null) {
				int pos = j + 1;
				found = true;
				opt = opt + pos + ",";
			}
			
		}
		
		if (found == true) {
			options[1] = opt.substring(0, opt.length()-1);
		} else {
			options[1] = "";
		}
		
		Remove remove = new Remove();
		
		try {
			
			remove.setOptions(options);
			remove.setInputFormat(complex_test);
			
			Instances newData = Filter.useFilter(complex_test, remove);		
			double[] values = new double[cba.size()];			
			
			for (int at = 0; at < newData.numAttributes(); at++) {			
				int pos  = cba.get(newData.attribute(at).name());		// get the index of this attribute in the train set
				values[pos] = newData.get(0).value(at);					// and its value
			}
			
			/*
				public SparseInstance(double weight, double[] attValues)
				
				Constructor that generates a sparse instance from the given parameters. Reference to the dataset is set to null. 
				(ie. the instance doesn't have access to information about the attribute types)
				
				Parameters:
				weight - the instance's weight
				attValues - a vector of attribute values
			*/
			
			training_complex.add(0, new SparseInstance(1.0, values));
			
			/*
				public Instances(Instances source, int first, int toCopy)
				
				Creates a new set of instances by copying a subset of another set.
				
				Parameters:
				source - the set of instances from which a subset is to be created
				first - the index of the first instance to be copied
				toCopy - the number of instances to be copied
			*/
			
			complex[1]= new Instances(training_complex,0,1);
			
			training_complex.remove(0);
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	
	/**
	 * Returns the instances of text-based representations
	 * and apply filter to it in order the format of data test is same
	 * with the format of data train
	 */
	private Instances getText(Instances data) {
		
		data.setClassIndex(0);
		
		Instances newData=null;
		
		try {
			
			stwv.setInputFormat(data);
			newData = weka.filters.Filter.useFilter(data, stwv);
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return newData;
	
	}
	
	/**
	 * Returns the instances of complex-based representations
	 * and apply filter to it in order the format of data test is same
	 * with the format of data train
	 */
	private Instances getComplex(Instances data) {

		data.setClassIndex(0);
		
		Instances newData = null;
		
		try {
		
			stwv.setInputFormat(data);
			newData = weka.filters.Filter.useFilter(data, stwv);
		
		} catch (Exception e) {
			e.printStackTrace();
		}		
		
		return newData;
	
	}
	
	/**
	 * Initializes the StringToWordVector filter to be used in the representations
	 */
	private void initialiseTextFilter() {
	
		/*
		 * Example
		 * "min_gram" : "2",
         * "max_gram" : "3",
         * "token_chars": [ "letter", "digit" ]
		 * Text: 'FC Schalke 04'
    	 * Token: FC, Sc, Sch, ch, cha, ha, hal, al, alk, lk, lke, ke, 04
		 */
		stwv = new StringToWordVector();
		stwv.setLowerCaseTokens(true);
		stwv.setMinTermFreq(1);
			
		stwv.setTFTransform(false);
		stwv.setIDFTransform(false);		
		stwv.setWordsToKeep(1000000000);
		
		// create an object for the tokenizer
		NGramTokenizer tokenizer = new NGramTokenizer();
		tokenizer.setNGramMinSize(2);
		tokenizer.setNGramMaxSize(2);
		
		// set the tokenizer
		stwv.setTokenizer(tokenizer);
	
	}
	
	/**
	 * Initializes the MNB classifier, by loading the previously generated models
	 */
	private void initializeClassifiers(){
		
		mnb_classifiers = new Classifier[2];
		
		try {
			
			// read the previous built model
			mnb_classifiers[0] = (Classifier) weka.core.SerializationHelper.read(pathToAllResources + "models/data_text_100.model");
			mnb_classifiers[1] = (Classifier) weka.core.SerializationHelper.read(pathToAllResources + "models/data_complex_100.model");
				
			// create the instances for every representation
			BufferedReader trdr = new BufferedReader(new FileReader(new File(pathToAllResources + "train/data_text_100.arff")));
			BufferedReader crdr = new BufferedReader(new FileReader(new File(pathToAllResources + "train/data_complex_100.arff")));
			
			training_text = new Instances(trdr);
			training_complex = new Instances(crdr);
			
			trdr.close();
			crdr.close();
			
			System.out.println("InitializeClassifier done");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Initializes the BidiMaps
	 */
	private void initializeAttributes(Map<String, Integer> tb, Map<String, Integer> cb){
		tba = new HashMap<String, Integer>();
		cba = new HashMap<String, Integer>();
		
		tba = tb;
		cba = cb;
	}
}