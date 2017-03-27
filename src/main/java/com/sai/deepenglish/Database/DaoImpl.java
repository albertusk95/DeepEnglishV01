package com.sai.deepenglish.Database;

import java.io.IOException;
import java.util.List;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import javax.sql.DataSource;

import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import com.sai.deepenglish.Model.Answer;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.sai.deepenglish.Model.Question;
import org.springframework.stereotype.Component;
import retrofit2.Response;


public class DaoImpl implements Dao {

    // Query untuk table user
    //private final static String SQL_SELECT_ALL="SELECT id, user_id, line_id, display_name FROM user_table";
    //private final static String SQL_GET_BY_LINE_ID=SQL_SELECT_ALL + " WHERE LOWER(user_id) LIKE LOWER(?);";

    // Query untuk table event
    //private final static String SQL_SELECT_ALL_EVENT="SELECT id, event_id, user_id, line_id, display_name FROM friend_table";
    //private final static String SQL_JOIN_EVENT = "INSERT INTO friend_table (event_id, user_id, line_id, display_name) VALUES (?, ?, ?, ?);";
    //private final static String SQL_GET_BY_EVENT_ID=SQL_SELECT_ALL_EVENT + " WHERE LOWER(event_id) LIKE LOWER(?);";
    //private final static String SQL_GET_BY_JOIN=SQL_SELECT_ALL_EVENT + " WHERE event_id = ? AND user_id = ?;";

    // Query for questions table
    private final static String SQL_SELECT_ALL_QUESTION = "SELECT id, question_no, question, choice_zero, choice_one, choice_two, choice_three FROM questions_table";
    private final static String SQL_STORE_QUESTION = "INSERT INTO questions_table (question_no, question, choice_zero, choice_one, choice_two, choice_three) VALUES (?, ?, ?, ?, ?, ?);";
    private final static String SQL_CLEAR_QUESTIONS_TABLE = "DELETE FROM questions_table";
    private final static String SQL_GET_QUESTION_BY_NUMBER = SQL_SELECT_ALL_QUESTION + " WHERE LOWER(question_no) LIKE LOWER(?);";

    // Query for answers table
    private final static String SQL_SELECT_ALL_ANSWER = "SELECT id, question_no, chosen_answer, right_answer FROM answers_table;";
    private final static String SQL_STORE_ANSWER = "INSERT INTO answers_table (question_no, chosen_answer, right_answer) VALUES (?, ?, ?);";


    private JdbcTemplate mJdbc;


    // EXTRACTOR FOR DATA RETRIEVAL
    private final static ResultSetExtractor<Question> SINGLE_RS_EXTRACTOR_QUESTION = new ResultSetExtractor<Question>() {

        @Override
        public Question extractData(ResultSet aRs) throws SQLException, DataAccessException {

            while(aRs.next()) {

                Question q = new Question(
                                    aRs.getLong("id"),
                                    aRs.getString("question_no"),
                                    aRs.getString("question"),
                                    aRs.getString("choice_zero"),
                                    aRs.getString("choice_one"),
                                    aRs.getString("choice_two"),
                                    aRs.getString("choice_three")
                                );

                return q;

            }

            return null;

        }

    };


    private final static ResultSetExtractor< List<Question> > MULTIPLE_RS_EXTRACTOR_QUESTION = new ResultSetExtractor< List<Question> >() {
        @Override
        public List<Question> extractData(ResultSet aRs)
                throws SQLException, DataAccessException
        {
            List<Question> list = new Vector<Question>();

            while(aRs.next()) {

                Question q = new Question(
                        aRs.getLong("id"),
                        aRs.getString("question_no"),
                        aRs.getString("question"),
                        aRs.getString("choice_zero"),
                        aRs.getString("choice_one"),
                        aRs.getString("choice_two"),
                        aRs.getString("choice_three")
                );

                list.add(q);

            }

            return list;
        }
    };


    private final static ResultSetExtractor< List<Answer> > MULTIPLE_RS_EXTRACTOR_ANSWER = new ResultSetExtractor< List<Answer> >() {
        @Override
        public List<Answer> extractData(ResultSet aRs)
                throws SQLException, DataAccessException
        {
            List<Answer> list = new Vector<Answer>();

            while(aRs.next()) {

                Answer ans = new Answer(
                                aRs.getLong("id"),
                                aRs.getString("question_no"),
                                aRs.getString("chosen_answer"),
                                aRs.getString("right_answer")
                            );

                list.add(ans);

            }

            return list;
        }
    };


    // Constructor
    public DaoImpl(DataSource aDataSource) {

        mJdbc = new JdbcTemplate(aDataSource);

    }

    // Store the eligible question in the database
    public int storeEligibleQuestion(String targetID, String lChannelAccessToken, int questionNumber, String theQuestion, String[] theChoices) {


        ///////////////////////////////////////
        pushMessage(targetID, lChannelAccessToken, questionNumber + " : " + theQuestion + " : "
                    + theChoices[0] + " : " + theChoices[1] + " : " + theChoices[2] + " : " + theChoices[3]);
        ///////////////////////////////////////


        return mJdbc.update(SQL_STORE_QUESTION, new Object[]{String.valueOf(questionNumber), theQuestion, theChoices[0], theChoices[1], theChoices[2], theChoices[3]});

    }

    // Clear the questions table
    public int clearQuestionsTable(String targetID, String lChannelAccessToken) {


        if (mJdbc.query(SQL_GET_QUESTION_BY_NUMBER, new Object[]{"'" + String.valueOf(1) + "'"}, MULTIPLE_RS_EXTRACTOR_QUESTION) != null) {
        //if (mJdbc.query(SQL_GET_QUESTION_BY_NUMBER, new Object[]{"'" + String.valueOf(1) + "'"}, SINGLE_RS_EXTRACTOR_QUESTION) != null) {

            // not empty

            ///////////////////////////////////////
            pushMessage(targetID, lChannelAccessToken, "TABLE IS NOT EMPTY");
            ///////////////////////////////////////

            return mJdbc.update(SQL_CLEAR_QUESTIONS_TABLE);

        }

        // already empty

        ///////////////////////////////////////
        pushMessage(targetID, lChannelAccessToken, "TABLE IS EMPTY");
        ///////////////////////////////////////

        return 1;

    }

    // Store the answer in the database
    public int storeAnswer(String targetID, String lChannelAccessToken, String questionNo, String chosenAnswer, String rightAnswer) {

        return mJdbc.update(SQL_STORE_ANSWER, new Object[]{questionNo, chosenAnswer, rightAnswer});

    }


    // Retrieve the questions with number <questionNumber>
    public Question getQuestion(String targetID, String lChannelAccessToken, int questionNumber) {


        Question lQ = mJdbc.query(SQL_GET_QUESTION_BY_NUMBER, new Object[]{"%" + String.valueOf(questionNumber) + "%"}, SINGLE_RS_EXTRACTOR_QUESTION);


        ///////////////////////////////////////
        pushMessage(targetID, lChannelAccessToken, "'%" + questionNumber + "%'" + " : " + lQ);
        ///////////////////////////////////////


        return lQ;

        //return mJdbc.query(SQL_GET_QUESTION_BY_NUMBER, new Object[]{"%" + String.valueOf(questionNumber) + "%"}, MULTIPLE_RS_EXTRACTOR_QUESTION);

    }


    // Retrieve the answer history
    public List<Answer> getAnswerHistory() {

        return mJdbc.query(SQL_SELECT_ALL_ANSWER, MULTIPLE_RS_EXTRACTOR_ANSWER);

    }


    // Method for pushing message
    private void pushMessage(String sourceId, String lChannelAccessToken, String txt) {

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