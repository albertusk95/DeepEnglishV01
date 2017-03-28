package com.sai.deepenglish.DataManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import com.sai.deepenglish.Database.Dao;
import edu.cmu.lti.lexical_db.ILexicalDatabase;
import edu.cmu.lti.lexical_db.NictWordNet;
import edu.cmu.lti.ws4j.impl.Lin;
import edu.cmu.lti.ws4j.impl.WuPalmer;
import edu.cmu.lti.ws4j.util.WS4JConfiguration;
import retrofit2.Response;

public class DistractorGenerator {

    private ILexicalDatabase db;

    private String pathToAllEnglishWords;

    private List<String> listOfActualNewWord;
    private List<String> listOfBlankNewWord;

    private List<String> listOfActualNewWord_SHUFFLED;
    private List<String> listOfBlankNewWord_SHUFFLED;

    private List<String> listOfEnglishWords;

    private Dao mDao;



    //////////////
    private String lChannelAccessToken;
    //////////////



    // EXPERIMENT PURPOSE
    private List<String> listOfTextToBeStoredInDB;

    public DistractorGenerator(String lChannelAccessToken, String pathToAllEnglishWords, List<String> listOfActualNewWord, List<String> listOfBlankNewWord) {

        db = new NictWordNet();

        this.pathToAllEnglishWords = pathToAllEnglishWords;

        this.listOfActualNewWord = listOfActualNewWord;
        this.listOfBlankNewWord = listOfBlankNewWord;

        // initialize the shuffled list for actual and blank new word
        listOfActualNewWord_SHUFFLED = new ArrayList<String>();
        listOfBlankNewWord_SHUFFLED = new ArrayList<String>();

        listOfTextToBeStoredInDB = new ArrayList<String>();



        //////////////
        this.lChannelAccessToken = lChannelAccessToken;
        //////////////


    }

    public  void setDao (Dao mDao) {
        this.mDao = mDao;
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
                .sorted(Map.Entry.comparingByValue(/*Collections.reverseOrder()*/))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

    }

    // Get minimum of three values
    private int Minimum (int a, int b, int c) {

        int mi;

        mi = a;

        if (b < mi) {
            mi = b;
        }
        if (c < mi) {
            mi = c;
        }

        return mi;

    }

    // Compute Levenshtein distance
    public int LD (String s, String t) {

        int d[][];          // matrix
        int n;              // length of s
        int m;              // length of t
        int i;              // iterates through s
        int j;              // iterates through t
        char s_i;           // ith character of s
        char t_j;           // jth character of t
        int cost;           // cost

        n = s.length ();
        m = t.length ();

        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        d = new int[n+1][m+1];

        for (i = 0; i <= n; i++) {
            d[i][0] = i;
        }

        for (j = 0; j <= m; j++) {
            d[0][j] = j;
        }

        for (i = 1; i <= n; i++) {

            s_i = s.charAt (i - 1);

            for (j = 1; j <= m; j++) {

                t_j = t.charAt (j - 1);

                if (s_i == t_j) {
                    cost = 0;
                }
                else {
                    cost = 1;
                }

                d[i][j] = Minimum (d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1] + cost);

            }

        }

        return d[n][m];

    }


    private int getIndexOfRightAnswer() {

        Random rr = new Random();
        int idxOfRightAnswer = rr.nextInt((3 - 0) + 1) + 0;

        return idxOfRightAnswer;

    }


    private String[] randomizeTheAnswerPosition(int idxOfRightAnswer, String theRightAnswer, List<String> listOfChoicesForThisQuestion) {

        String[] tmpArr = new String[4];

        int counterForIndexing = 0;

        for (int kdx = 0; kdx < 4; kdx++) {
            if (kdx != idxOfRightAnswer) {
                tmpArr[kdx] = listOfChoicesForThisQuestion.get(counterForIndexing);
                counterForIndexing++;
            } else {
                tmpArr[kdx] = theRightAnswer;
            }
        }

        return tmpArr;

    }


    private void shuffleActualandBlankNewWord() {

        // create a list storing the index of listOfActualNewWorld
        List<Integer> listOfIndexActualOrBlankNewWorld = new ArrayList<Integer>();

        for (int idxActOrBlank = 0; idxActOrBlank < listOfActualNewWord.size(); idxActOrBlank++) {
            listOfIndexActualOrBlankNewWorld.add(idxActOrBlank);
        }

        // shuffle the list of index
        Collections.shuffle(listOfIndexActualOrBlankNewWorld);

        // build the shuffled list
        for (int idxActOrBlank = 0; idxActOrBlank < listOfIndexActualOrBlankNewWorld.size(); idxActOrBlank++) {
            listOfActualNewWord_SHUFFLED.add(listOfActualNewWord.get(listOfIndexActualOrBlankNewWorld.get(idxActOrBlank)));
            listOfBlankNewWord_SHUFFLED.add(listOfBlankNewWord.get(listOfIndexActualOrBlankNewWorld.get(idxActOrBlank)));
        }

    }


    // REMOVE targetID and lChannelAccessToken ! DEBUGGING PURPOSE
    public List<Integer> generateDistractor(String targetID) {

        int distance;

        List<String> listOfChoicesForThisQuestion;
        String[] arrOfChoicesForThisQuestionFINAL;
        List<Integer> listOfDBAccessStatus = new ArrayList<Integer>();

        Map<Integer, Integer> mapDistance;


        // Shuffle the list of question
        shuffleActualandBlankNewWord();


        // Clear list of index for right answer
        AnswerController.listOfIndexForRightAnswer = new ArrayList<Integer>();


        for (int idx = 0; idx < listOfActualNewWord_SHUFFLED.size(); idx++) {

            mapDistance = new HashMap<Integer, Integer>();

            // Get the similarity score
            for (int jdx = 0; jdx < listOfEnglishWords.size(); jdx++) {

                distance = LD(listOfActualNewWord_SHUFFLED.get(idx), listOfEnglishWords.get(jdx));

                if (distance != 0) {
                    mapDistance.put(jdx, distance);
                }

            }


            // Initialize the lost of choices (3 items)
            listOfChoicesForThisQuestion = new ArrayList<String>();
            //listOfChoicesForThisQuestion.add(listOfActualNewWord.get(idx));

            // Get top three score which means they are the most similar word with our actual new word
            Map<Integer, Integer> mapSorted = sortMapDistanceByValue(mapDistance);

            // Initialize the counter (used to get the top three)
            int counterForTopThree = 0;

            // Loop a Map and get those three scores
            for (Map.Entry<Integer, Integer> entry : mapSorted.entrySet()) {

                if (counterForTopThree < 3) {

                    listOfChoicesForThisQuestion.add(listOfEnglishWords.get(entry.getKey()));

                } else {
                    break;
                }

                counterForTopThree++;

            }


            // Get the random number as the index for the right answer
            int idxOfRightAnswer = getIndexOfRightAnswer();


            // Store the right answer in the array index randomly
            arrOfChoicesForThisQuestionFINAL = randomizeTheAnswerPosition(idxOfRightAnswer, listOfActualNewWord_SHUFFLED.get(idx), listOfChoicesForThisQuestion);


            // Store the answers index in a global variable
            AnswerController.listOfIndexForRightAnswer.add(idxOfRightAnswer);


            //////////////////////////
            //pushMessage(targetID, "quest no: " + (idx + 1) + ", " + idxOfRightAnswer);
            //////////////////////////



            // Store the choices in a database
            // REMOVE TARGET ID !!!
            int statusOfChoiceStoring = storeTheChoiceInDB(targetID, idx + 1, listOfBlankNewWord_SHUFFLED.get(idx), arrOfChoicesForThisQuestionFINAL);


            // Store the storing status in a list (0 or 1)
            listOfDBAccessStatus.add(statusOfChoiceStoring);

        }

        // return the list of storing status to the controller
        return listOfDBAccessStatus;

    }


    // REMOVE TARGET ID !!!
    private int storeTheChoiceInDB(String targetID, int questionNo, String theQuestion, String[] theChoices) {


        /////////////////////
        //pushMessage(targetID, "Inside storeTheChoiceInDB " + questionNo + ":" + theQuestion + ": choices: "
        //            + theChoices[0] + ", " + theChoices[1] + ", " + theChoices[2] + ", " + theChoices[3]);
        /////////////////////


        int storingStatus = mDao.storeEligibleQuestion(targetID, lChannelAccessToken, questionNo, theQuestion, theChoices);

        return storingStatus;

    }


    // Method for pushing message
    private void pushMessage(String sourceId, String txt) {

        TextMessage textMessage = new TextMessage(txt);
        PushMessage pushMessage = new PushMessage(sourceId, textMessage);

        try {

            Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .pushMessage(pushMessage)
                    .execute();

            System.out.println(response.code() + " " + response.message());

        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }

    }

}