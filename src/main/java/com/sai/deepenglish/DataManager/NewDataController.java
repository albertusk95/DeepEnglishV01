package com.sai.deepenglish.DataManager;

import java.util.List;
import java.util.ArrayList;

public class NewDataController {

    private String userInput;
    private List<String> processedActualForBlankNewData;
    private List<String> processedBlankNewData;

    public NewDataController(String userInput) {

        this.userInput = userInput;
        processedActualForBlankNewData = new ArrayList<String>();
        processedBlankNewData = new ArrayList<String>();
    }

    public List<String> getListOfActualNewData() {

        return processedActualForBlankNewData;

    }

    public List<String> getListOfBlankNewData() {

        return processedBlankNewData;

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

    private void saveActualNewDataToList(String actualWordForBlankPosition) {

        processedActualForBlankNewData.add(actualWordForBlankPosition);

    }

    private void saveBlankNewDataToList(String newDataWithBlankPosition) {

        processedBlankNewData.add(newDataWithBlankPosition);

    }

    private void generateBlankPoints(String tokenOfUserInput) {

        String textWithBlankPosition;
        String tmp_token;

        // tokenize the text
        String[] token = tokenOfUserInput.split("\\s+");

        for (int j = 0; j < token.length; j++) {

            tmp_token = token[j];

            // change the token to "[]" as the blank position
            token[j] = "[ ]";

            // create a new text including the blank position
            textWithBlankPosition = createTextWithBlankPosition(token);

            // add the instance to the file of instances
            if (!tmp_token.matches("^\\s*$")) {
                saveActualNewDataToList(tmp_token);
                saveBlankNewDataToList(textWithBlankPosition);
            }

            // change the token to the original string
            token[j] = tmp_token;

        }

    }

    public void generateInstances() {

        // tokenize the input user
        String[] tokenizedUserInput = userInput.split("\\.");

        for (int i = 0; i < tokenizedUserInput.length; i++) {

            generateBlankPoints(tokenizedUserInput[i]);

        }

    }

}
