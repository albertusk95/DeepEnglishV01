package com.sai.deepenglish.Model;

public class Question {

    private Long id;
    private String question_no;
    private String question;
    private String choice_zero;
    private String choice_one;
    private String choice_two;
    private String choice_three;


    public Question(Long id, String question_no, String question, String choice_zero, String choice_one, String choice_two, String choice_three) {

        this.id = id;
        this.question_no = question_no;
        this.question = question;
        this.choice_zero = choice_zero;
        this.choice_one = choice_one;
        this.choice_two = choice_two;
        this.choice_three = choice_three;

    }

    public Question() {

    }

    // GETTER
    public Long getID() {
        return id;
    }

    public String getQuestionNo() {
        return question_no;
    }

    public String getQuestion() {
        return question;
    }

    public String getChoiceZero() {
        return choice_zero;
    }

    public String getChoiceOne() {
        return choice_one;
    }

    public String getChoiceTwo() {
        return choice_two;
    }

    public String getChoiceThree() {
        return choice_three;
    }

}