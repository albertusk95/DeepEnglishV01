package com.sai.deepenglish.DataManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import edu.cmu.lti.lexical_db.ILexicalDatabase;
import edu.cmu.lti.lexical_db.NictWordNet;
import edu.cmu.lti.ws4j.impl.WuPalmer;
import edu.cmu.lti.ws4j.util.WS4JConfiguration;

public class DistractorGenerator {

    private static ILexicalDatabase db = new NictWordNet();

    private String pathToAllEnglishWords;

    private List<String> listOfActualNewWord;
    private List<String> listOfBlankNewWord;

    private List<String> listOfEnglishWords;

    public DistractorGenerator(String pathToAllEnglishWords, List<String> listOfActualNewWord, List<String> listOfBlankNewWord) {

        this.pathToAllEnglishWords = pathToAllEnglishWords;

        this.listOfActualNewWord = listOfActualNewWord;
        this.listOfBlankNewWord = listOfBlankNewWord;

    }


    public List<String> retrieveAllEnglishWords() {

        List<String> listOfText = new ArrayList<String>();

        BufferedReader br = null;
        FileReader fr = null;

        try {

            String sCurrentLine;
            br = new BufferedReader(new FileReader(pathToAllEnglishWords));

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

    public void setListOfEnglishWords(List<String> listOfEnglishWords) {

        this.listOfEnglishWords = listOfEnglishWords;

    }

    private <K, V extends Comparable<? super V>> Map<K, V> sortMapDistanceByValue(Map<K, V> map) {

        // For ascending order, comment Collections.reverseOrder()
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

    }

    private double compute(String word1, String word2) {

        WS4JConfiguration.getInstance().setMFS(true);
        double s = new WuPalmer(db).calcRelatednessOfWords(word1, word2);

        return s;

    }


    public void generateDistractor() {

        double distance;
        double maxSimilarityScore;

        List<String> listOfChoicesForThisQuestion;

        Map<Integer, Double> mapDistance;

        for (int idx = 0; idx < listOfActualNewWord.size(); idx++) {

            mapDistance =  new HashMap<Integer, Double>();

            // Get the similarity score
            for (int jdx = 0; jdx < listOfEnglishWords.size(); jdx++) {

                distance = compute(listOfActualNewWord.get(idx), listOfEnglishWords.get(jdx));

                mapDistance.put(jdx, distance);

            }

            // Initialize the lost of choices (4 items)
            listOfChoicesForThisQuestion = new ArrayList<String>();
            listOfChoicesForThisQuestion.add(listOfActualNewWord.get(idx));

            // Get top three score which means they are the most similar word with our actual new word
            Map<Integer, Double> mapSorted = sortMapDistanceByValue(mapDistance);

            // Initialize the counter (used to get the top three)
            int counterForTopThree = 0;

            // Loop a Map and get those three scores
            for (Map.Entry<Integer, Double> entry : mapSorted.entrySet()) {

                if (counterForTopThree < 3) {
                    listOfChoicesForThisQuestion.add(listOfEnglishWords.get(entry.getKey()));
                } else {
                    break;
                }

                counterForTopThree++;

            }

            // Store the choices in a database
            

        }

    }

}