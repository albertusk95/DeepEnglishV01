package com.sai.deepenglish.Classifier;

import weka.core.*;
import weka.classifiers.meta.FilteredClassifier;
import java.util.ArrayList;
import java.io.*;

public class MyFilteredClassifier {

	private String text;
	private Instances instances;
	private FilteredClassifier classifier;

	public MyFilteredClassifier() {

	}
	
	public void setDataTest(String dataTest) {

		text = dataTest;

	}

	public void loadModel(String fileName) {

		try {

			ObjectInputStream in = new ObjectInputStream(new FileInputStream(fileName));
            Object tmp = in.readObject();
			classifier = (FilteredClassifier) tmp;
            in.close();

            System.out.println("===== Loaded model: " + fileName + " =====");

		} catch (Exception e) {

			// Given the cast, a ClassNotFoundException must be caught along with the IOException
			System.out.println("Problem found when reading: " + fileName);

		}

	}
	
	/**
	 * This method creates the instance to be classified, from the text that has been read.
	 */
	public void makeInstance() {

		// Create the attributes, class and text
		//FastVector fvNominalVal = new FastVector(2);
		//fvNominalVal.addElement("0");
		//fvNominalVal.addElement("1");

		ArrayList<String> fvNominalVal = new ArrayList<String>();
		fvNominalVal.add("0");
		fvNominalVal.add("1");

		Attribute attribute1 = new Attribute("class", fvNominalVal);
		Attribute attribute2 = new Attribute("text",(ArrayList<String>) null);

		// Create list of instances with one element
		//FastVector fvWekaAttributes = new FastVector(2);
		//fvWekaAttributes.addElement(attribute1);
		//fvWekaAttributes.addElement(attribute2);

		ArrayList<Attribute> fvWekaAttributes = new ArrayList<Attribute>();
		fvWekaAttributes.add(attribute1);
		fvWekaAttributes.add(attribute2);

		instances = new Instances("Test relation", fvWekaAttributes, 1);           

		// Set class index
		instances.setClassIndex(0);

		// Create and add the instance
		DenseInstance instance = new DenseInstance(2);
		instance.setValue(attribute2, text);

		// Another way to do it:
		// instance.setValue((Attribute)fvWekaAttributes.elementAt(1), text);

		instances.add(instance);
 		System.out.println("===== Instance created with reference dataset =====");
		System.out.println(instances);

	}
	
	/**
	 * This method performs the classification of the instance.
	 * Output is done at the command-line.
	 */
	public String classify() {

		double[] preds = null;

		try {
			
			System.out.println("===== Classified instance =====");
			
			//double pred = classifier.classifyInstance(instances.instance(0));
			//System.out.println("Class predicted: " + instances.classAttribute().value((int) pred));
			
			preds = classifier.distributionForInstance(instances.instance(0));
			System.out.println("Probs: " + preds[0] + ", " + preds[1]);

		} catch (Exception e) {
			System.out.println("Problem found when classifying the text");
		}

		if (preds[0] > preds[1]) {
			return "0";
		} else {
			return "1";
		}

	}
	

}	