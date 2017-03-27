package com.sai.deepenglish.DataManager;

import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import com.sai.deepenglish.Database.Dao;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

public class AnswerController {

    private Dao mDao;

    private String questionNo;
    private String chosenAnswer;

    public static List<Integer> listOfIndexForRightAnswer;


    private String targetID;
    private String lChannelAccessToken;


    public AnswerController(String targetID, String lChannelAccessToken, String questionNo, String chosenAnswer) {

        this.questionNo = questionNo;
        this.chosenAnswer = chosenAnswer;


        this.targetID = targetID;
        this.lChannelAccessToken = lChannelAccessToken;

    }

    public void setDao(Dao mDao) {
        this.mDao = mDao;
    }

    private String getAnswerInStr(int idxOfRightAnswer) {

        String answerInStr;

        // convert the index into 'A', 'B', 'C', or 'D'
        if (idxOfRightAnswer == 0) {
            answerInStr = "A";
        } else if (idxOfRightAnswer == 1) {
            answerInStr = "B";
        } else if (idxOfRightAnswer == 2) {
            answerInStr = "C";
        } else {
            answerInStr = "D";
        }

        return answerInStr;

    }

    /*
    public int checkAnswer() {

        // value 0 = false; 1 = true
        int answerValue;

        // get the index of the right answer for this question
        int idxOfRightAnswer = listOfIndexForRightAnswer.get(Integer.parseInt(questionNo) - 1);

        if (!chosenAnswer.equals(getAnswerInStr(idxOfRightAnswer))) {

            // wrong answer
            answerValue = 0;

        } else {

            // right answer
            answerValue = 1;

        }

        return answerValue;

    }
    */

    public int storeAnswerInDB() {

        /////////////////////
        pushMessage(targetID, "listOfIdxRight: " + listOfIndexForRightAnswer.get(Integer.parseInt(questionNo) - 1));
        /////////////////////

        int idxOfRightAnswer = listOfIndexForRightAnswer.get(Integer.parseInt(questionNo) - 1);


        /////////////////////
        pushMessage(targetID, "PASSED");
        /////////////////////


        int storingAnswerStatus = mDao.storeAnswer(targetID, lChannelAccessToken, questionNo, chosenAnswer, getAnswerInStr(idxOfRightAnswer));


        /////////////////////
        pushMessage(targetID, "idxRightAnswer: " + idxOfRightAnswer + ", status: " + storingAnswerStatus);
        /////////////////////


        return storingAnswerStatus;
    }

    //Method untuk push message
    private void pushMessage(String sourceId, String txt){
        TextMessage textMessage = new TextMessage(txt);
        PushMessage pushMessage = new PushMessage(sourceId,textMessage);
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