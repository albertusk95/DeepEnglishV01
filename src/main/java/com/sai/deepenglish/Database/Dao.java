package com.sai.deepenglish.Database;

import java.util.List;

import com.sai.deepenglish.Model.Answer;
import com.sai.deepenglish.Model.Question;

public interface Dao {

    // SET ACTION
    public int storeEligibleQuestion(String targetID, String lChannelAccessToken, String user_id, int questionNumber, String theQuestion, String[] theChoices);
    public int clearQuestionsTable(String targetID, String lChannelAccessToken, String user_id);

    public int storeAnswer(String targetID, String lChannelAccessToken, String user_id, String questionNo, String chosenAnswer, String rightAnswer);
    public int clearAnswersTable(String targetID, String lChannelAccessToken, String user_id);

    // GET ACTION
    public Question getQuestion(String targetID, String lChannelAccessToken, String user_id, int questionNumber);

    public List<Answer> getAnswerHistory(String user_id);

}