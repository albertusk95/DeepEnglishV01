package com.sai.deepenglish.Model;

public class Answer {

    private Long id;
    private String question_no;
    private String chosenAnswer;
    private String rightAnswer;


    public Answer(Long id, String question_no, String chosenAnswer, String rightAnswer) {

        this.id = id;
        this.question_no = question_no;
        this.chosenAnswer = chosenAnswer;
        this.rightAnswer = rightAnswer;

    }

    public Answer() {

    }

    // GETTER
    public Long getID() {
        return id;
    }

    public String getQuestionNo() {
        return question_no;
    }

    public String getChosenAnswer() {
        return chosenAnswer;
    }

    public String getRightAnswer() {
        return rightAnswer;
    }

}