package com.sai.deepenglish.DataManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class InstancesGenerator {

	private String pathToOriginalTrainingData;
	private String pathToFileOfInstances;
	private MaxentTagger tagger;
	
	public InstancesGenerator(String pathToOriginalTrainingData, String pathToFileOfInstances) {
	
		this.pathToOriginalTrainingData = pathToOriginalTrainingData;
		this.pathToFileOfInstances = pathToFileOfInstances;
		
		// Initialize the tagger
		tagger = new MaxentTagger("taggers/english-left3words-distsim.tagger");
		
	}
	
	public List<String> retrieveOriginalData() {
		
		List<String> listOfText = new ArrayList<String>();
		
		BufferedReader br = null;
		FileReader fr = null;

		try {

			String sCurrentLine;
			br = new BufferedReader(new FileReader(pathToOriginalTrainingData));

			while ((sCurrentLine = br.readLine()) != null) {
				// add the string into the list
				listOfText.add(sCurrentLine);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				
				if (br != null) {
					br.close();
				}
				
				if (fr != null) {
					fr.close();
				}
				
			} catch (IOException ex) {
				ex.printStackTrace();
			}

		}
		
		return listOfText;
		
	}
	
	private void saveInstanceToFile(String instance, String eligibility) {
		
		BufferedWriter bw = null;
		FileWriter fw = null;

		try {
			
			fw = new FileWriter(pathToFileOfInstances, true);
			bw = new BufferedWriter(fw);
			
			bw.write(eligibility);
			bw.write("\t");
			
			bw.write(instance);
			bw.write("\n");

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {

				if (bw != null) {
					bw.close();
				}

				if (fw != null) {
					fw.close();
				}

			} catch (IOException ex) {

				ex.printStackTrace();

			}
		}
		
	}

	private String createTextWithBlankPosition(String[] token) {
		String tmp = "";
		
		for (int i = 0; i < token.length; i++) {
			if (i != token.length - 1) {
				tmp = tmp + token[i] + " ";
			} else {
				tmp = tmp + token[i];
			}
		}
		
		return tmp;
	}
		
	private int getIndexOfTheBlankPosition(String[] token) {
		
		for (int i = 0; i < token.length; i++) {
			if (token[i].equals("[]")) {
				return i;
			}
		}
		
		return -1;
	
	}
	
	public String setPOSTagging(String textToBeTagged) {
		
		String tagged;
		String sample;
		
		sample = textToBeTagged;
			
		//tag the string
		tagged = tagger.tagString(sample);
		
		return tagged;
		
	}
	
	public void generateInstances(List<String> listOfOriginData) {
		
		String textWithBlankPosition;
		
		// print out for debugging purpose
		/*
		System.out.println("Here are the original data: ");
		for(int i = 0; i < listOfOriginData.size(); i++) {
			System.out.println(listOfOriginData.get(i));
		}
		*/
		
		// generate the instances for each data in the list of original data
		for (int i = 0; i < listOfOriginData.size(); i++) {
			
			/**
			 * listOfOriginData consists of:
			 * - the actual word
			 * - tab
			 * - text
			 */
			
			// separate the label (true/false) from the original text
			String[] splittedOriginData = listOfOriginData.get(i).split("\\t");
				
			// get the actual word of the blank position
			String actualWordForBlankPosition = splittedOriginData[0];
			
			// tokenize the text
			String[] token = splittedOriginData[1].split("\\s+");
			
			// get the index of the blank position
			int indexOfTheBlankPosition = getIndexOfTheBlankPosition(token);
			
			String tmp_token;
			String isEligible; 
			
			// make it same
			token[indexOfTheBlankPosition] = actualWordForBlankPosition;
			
			for (int j = 0; j < token.length; j++) {
				
				tmp_token = token[j];
					
				if (j == indexOfTheBlankPosition) {
					isEligible = "true";
				} else {
					isEligible = "false";
				}
				
				// change the token to "[]" as the blank position
				token[j] = "[]";
				
				// create a new text including the blank position
				textWithBlankPosition = createTextWithBlankPosition(token);
				
				String textWithBlankPosition_TAGGED = setPOSTagging(textWithBlankPosition);
				
				// add the instance to the file of instances
				saveInstanceToFile(textWithBlankPosition_TAGGED, isEligible);
				
				// change the token to the original string
				token[j] = tmp_token;
			
			}
			
		}
				
	}
	
}
