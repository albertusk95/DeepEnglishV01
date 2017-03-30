package com.sai.deepenglish.DataManager;

import org.springframework.beans.factory.annotation.Autowired;

import com.sai.deepenglish.Database.Dao;
import com.sai.deepenglish.Model.Question;

import java.util.List;

public class QuestionController {

    private Dao mDao;

    private int questionNumber;

    private String idTarget;


    public QuestionController(int questionNumber) {

        this.questionNumber = questionNumber;

    }


    public void setDao(Dao mDao) {
        this.mDao = mDao;
    }

    public void setIdTarget(String idTarget) {
        this.idTarget = idTarget;
    }

    public Question retrieveQuestion(String targetID, String lChannelAccessToken) {

        Question retrievedQuestion = mDao.getQuestion(targetID, lChannelAccessToken, idTarget, questionNumber);
        return retrievedQuestion;

    }

}