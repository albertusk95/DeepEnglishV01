package com.sai.deepenglish.Trainer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.*;

//import org.apache.commons.collections4.BidiMap;
//import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayesMultinomial;
import weka.classifiers.evaluation.Evaluation;
//import weka.classifiers.functions.LibSVM;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.tokenizers.NGramTokenizer;
import weka.filters.unsupervised.attribute.StringToWordVector;

public class Trainer {

	private String pathToAllResources;

	private BufferedWriter twr;
	private BufferedWriter cwr;
	
	private Map<String, Integer> tba;
	private Map<String, Integer> cba;
	
	//private BidiMap<String, Integer> cba;
	
	public Trainer(String pathToAllResources) {
		
		System.out.println("Welcome to Trainer!");
		
		this.pathToAllResources = pathToAllResources;
		
		//cba = new DualHashBidiMap<String, Integer>();
		
		tba = new HashMap<String, Integer>();
		cba = new HashMap<String, Integer>(); 
	 
	}
	
	public void train() {
		trainText();
		trainComplex();
	}
	
	public void trainText() {
		
		System.out.println("Hi from trainText!\n");
		
		Instances data = null;
		
		try {
			
			// get the instances with a filter within it
			data = getText(pathToAllResources + "train/data_instances_tagged_custom_text_100.arff");
			
			// save the filtered text-based instances
			saveFile(data, "data_text_100");
			
			System.out.println("Write the attributes to " + pathToAllResources + "attributes/data_text_100.tsv");
			
			// write the attributes to a file (with TAB as the delimiter)
			twr = new BufferedWriter(new FileWriter(new File(pathToAllResources + "attributes/data_text_100.tsv")));	
			
			for (int i = 0; i < data.numAttributes(); i++) {
				tba.put(data.attribute(i).name(), i);
				twr.write(data.attribute(i).name() + "\t" + i + "\n");
			}
			
			twr.close();
			
		} catch (Exception e) {
			System.out.println("text-based training file not found");
		}
		
		// create a classifier
		Classifier cls = (Classifier)new NaiveBayesMultinomial();
		
		// build model
		try {
			
			System.out.println("Building text model...");
			cls.buildClassifier(data);
			System.out.println("Text model built");
		
		} catch (Exception e) {
			System.out.println("could not build classifier on the text-based representation");
		}
		
		// save model
		try {
			
			System.out.println("Saving text model...");
			weka.core.SerializationHelper.write(pathToAllResources + "models/data_text_100.model", cls);
			System.out.println("Text model saved");
			
		} catch (Exception e) {
			System.out.println("could not save the text-based model");
		}
	
	}
	
	public void trainComplex() {
		
		System.out.println("Hi from trainComplex!\n");
		
		Instances data = null;
		
		try {
			
			// get the instances with a filter inside
			data = getComplex(pathToAllResources + "train/data_instances_tagged_custom_100.arff");
			
			// save the instances to a file
			saveFile(data, "data_complex_100");
			
			System.out.println("Write the attributes to " + pathToAllResources + "attributes/data_complex_100.tsv");
			
			// write the attributes
			cwr = new BufferedWriter(new FileWriter(new File(pathToAllResources + "attributes/data_complex_100.tsv")));	// writes the attributes in a file

			for (int i = 0; i < data.numAttributes(); i++){
				
				//cba.put(data.attribute(i).name(), i);
				
				cba.put(data.attribute(i).name(), i);
				cwr.write(data.attribute(i).name() + "\t" + i + "\n");
			
			}
			
			cwr.close();
			
			System.out.println("DONE\n");
			
		} catch (Exception e) {
			System.out.println("combined training file not found.");
		}
		
		// create a classifier
		Classifier cls = (Classifier)new NaiveBayesMultinomial();
		
		// build model
		try {
			
			System.out.println("Building complex model");
			cls.buildClassifier(data);
			System.out.println("Complex model built");
			
		} catch (Exception e) {
			System.out.println("could not build classifier on the complex-based representation");
		}
		
		System.out.println();
		
		// save model
		try {
			
			System.out.println("Saving complex model...");
			weka.core.SerializationHelper.write(pathToAllResources + "models/data_complex_100.model", cls);
			System.out.println("Complex model saved");
		
		} catch (Exception e) {
			System.out.println("could not save the complex-based model");
		}
		
	}
	
	/*
	 * Text: all of the two words combinations
	 */
	public Map<String, Integer> getTextAttributes(){
		
		try {
			tba.clear();
			
			BufferedReader rdr = new BufferedReader(new FileReader(new File(pathToAllResources + "attributes/data_text_100.tsv")));
			
			String inline;
			
			while ((inline = rdr.readLine()) != null) {
				// TAB as delimiter
				String[] dic = inline.split("\\t");
				tba.put(dic[0], Integer.parseInt(dic[1]));
			}
			
			rdr.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return tba;
	
	}
	
	/*
	 * Complex: all of the two words combinations where each word has their own TAGGER 
	 */
	public Map<String, Integer> getComplexAttributes(){
		
		try {
			cba.clear();
			
			BufferedReader rdr = new BufferedReader(new FileReader(new File(pathToAllResources + "attributes/data_complex_100.tsv")));
			
			String inline;
			
			while ((inline = rdr.readLine()) != null){
				String[] dic = inline.split("\\t");
				cba.put(dic[0], Integer.parseInt(dic[1]));
			}
			
			rdr.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return cba;
	
	}
	
	/**
	 * Returns the text-based instances with StringToWordVector as the filter
	 */
	private Instances getText(String fileText) {
		
		System.out.println("Add StringToWordVector and NGramTokenizer");
	
		DataSource ds;
		Instances newData = null;
		
		try {
		
			// create object for data train (text based)
			ds = new DataSource(fileText);
			
			// create new instances for data train (text based)
			Instances data =  ds.getDataSet();
			data.setClassIndex(0);
			
			// set the filter for the dataset
			StringToWordVector filter = new StringToWordVector();
			filter.setInputFormat(data);
			filter.setLowerCaseTokens(true);
			filter.setMinTermFreq(1);
			filter.setTFTransform(false);
			filter.setIDFTransform(false);		
			filter.setWordsToKeep(1000000000);
			
			// set NGram tokenizer for the filter
			NGramTokenizer tokenizer = new NGramTokenizer();
			tokenizer.setNGramMinSize(2);
			tokenizer.setNGramMaxSize(2);
			
			filter.setTokenizer(tokenizer);	
			
			// create a new instances which has a filter 
			newData = weka.filters.Filter.useFilter(data, filter);
		
			System.out.println("DONE\n");
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return newData;
	
	}
	
	/**
	 * Returns the combined (text + POS) representations with StringToWordVector as the filter
	 */
	private Instances getComplex(String fileComplex) {
		
		System.out.println("Add StringToWordVector and NGramTokenizer");
		
		DataSource ds;
		Instances newData = null;
		
		try {
			// create object for the data train (complex-based)
			ds = new DataSource(fileComplex);
		
		
			// create a new instances
			Instances data =  ds.getDataSet();
			data.setClassIndex(0);
			
			// create a STWV filter
			StringToWordVector filter = new StringToWordVector();
			filter.setInputFormat(data);
			filter.setLowerCaseTokens(true);
			filter.setMinTermFreq(1);
			filter.setTFTransform(false);
			filter.setIDFTransform(false);		
			filter.setWordsToKeep(1000000000);
			
			// create a tokenizer for STWV
			NGramTokenizer tokenizer = new NGramTokenizer();
			tokenizer.setNGramMinSize(2);
			tokenizer.setNGramMaxSize(2);
			
			// set the tokenizer as part of the filter
			filter.setTokenizer(tokenizer);	
			
			// create a new instances which has a filter 
			newData = weka.filters.Filter.useFilter(data, filter);
			
			System.out.println("DONE\n");
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return newData;
	}	
	
	/**
	 * Save the filtered instances to an ARFF file
	 * The instances has been filtered with STWV
	 */
	public void saveFile(Instances dataset, String fileName){
		
		System.out.println("Saving the filtered instances to " + pathToAllResources + "train/" + fileName + ".arff");
		
		ArffSaver saver = new ArffSaver();
		saver.setInstances(dataset);
		
		try {
		
			saver.setFile(new File(pathToAllResources + "train/" + fileName + ".arff"));
			saver.writeBatch();
		
			System.out.println("DONE\n");
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
		