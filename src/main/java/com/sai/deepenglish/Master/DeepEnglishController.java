package com.sai.deepenglish.Master;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import com.google.gson.Gson;

import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;
import com.sai.deepenglish.DataManager.AnswerController;
import com.sai.deepenglish.Model.Answer;
import de.l3s.boilerpipe.document.TextDocument;
import de.l3s.boilerpipe.extractors.CommonExtractors;
import de.l3s.boilerpipe.sax.BoilerpipeSAXInput;
import de.l3s.boilerpipe.sax.HTMLDocument;
import de.l3s.boilerpipe.sax.HTMLFetcher;
import org.apache.commons.validator.routines.UrlValidator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import retrofit2.Response;
import weka.core.Instances;

//import com.sai.deepenglish.DataManager.InstancesGenerator;
import com.sai.deepenglish.Trainer.Trainer;
import com.sai.deepenglish.Preprocessor.QuestionPreprocessor;
import com.sai.deepenglish.Classifier.PolarityClassifier;
import com.sai.deepenglish.Model.Payload;
import com.sai.deepenglish.DataManager.NewDataController;
import com.sai.deepenglish.DataManager.DistractorGenerator;
import com.sai.deepenglish.DataManager.QuestionController;
import com.sai.deepenglish.Database.Dao;
import com.sai.deepenglish.Model.Question;

@RestController
@RequestMapping(value="/deepenglishbot")
public class DeepEnglishController {

	// channel secret initialization
	@Autowired
	@Qualifier("com.linecorp.channel_secret")
	String lChannelSecret;

	// channel access token initialization
	@Autowired
	@Qualifier("com.linecorp.channel_access_token")
	String lChannelAccessToken;

	@Autowired
	Dao mDao;


	private String displayName;
	private Payload payload;


	@RequestMapping(value="/callback", method= RequestMethod.POST)
	public ResponseEntity<String> callback(@RequestBody String aPayload) {


		// Analyze the payload
		if(aPayload != null && aPayload.length() > 0) {
			System.out.println("Payload: " + aPayload);
		}

		Gson gson = new Gson();
		payload = gson.fromJson(aPayload, Payload.class);


		// Variable initialization
		String msgText = " ";
		String idTarget = " ";
		String eventTypeDetails;
		String eventType = payload.events[0].type;
		int msgTextLength;

		// Get event's type
		if (eventType.equals("join")){

			if (payload.events[0].source.type.equals("group")){
				replyToUser(payload.events[0].replyToken, "Hello Group! I'm DeepEnglish and I'd like to help you in improving your English reading comprehension skill.");
			}
			if (payload.events[0].source.type.equals("room")){
				replyToUser(payload.events[0].replyToken, "Hello Room! I'm DeepEnglish and I'd like to help you in improving your English reading comprehension skill.");
			}

		} else if (eventType.equals("follow")){

			greetingMessage();

		} else if (eventType.equals("message")) {

			/**
			 * MESSAGE PAYLOAD has several categories:
			 * 1. Link
			 * 2. Article's body
			 * 2. Answer
			 */

			// Assign the ID target as the destination of message reply
			if (payload.events[0].source.type.equals("group")){
				idTarget = payload.events[0].source.groupId;
			} else if (payload.events[0].source.type.equals("room")){
				idTarget = payload.events[0].source.roomId;
			} else if (payload.events[0].source.type.equals("user")){
				idTarget = payload.events[0].source.userId;
			}

			// Parsing message from user
			if (!payload.events[0].message.type.equals("text")){

				// If the message is not a text (image, sticker, etc), just send a greeting message
				//greetingMessage();

				replyToUser(payload.events[0].replyToken, payload.events[0].replyToken + " Hello Group! I'm DeepEnglish and I'd like to help you in improving your English reading comprehension skill.");

			} else {

				msgText = payload.events[0].message.text;
				msgText = msgText.toLowerCase();
				msgTextLength = msgText.length();

				// Check whether the user asks the bot to leave the chat room
				if (!msgText.contains("bot leave")) {

					// Check the message's category
					if (!msgText.contains("[your answer]:")) {

						if (isValidURLText(msgText)) {

							// Category 1: Link
							String linkContent = processLinkContent(idTarget);
							processText_ARTICLE_BODY(idTarget, linkContent);

						} else {

							if (!msgText.contains("[start the quiz]")) {

								// Check whether it's an article's body based on the message length
								if (msgTextLength > 5) {

									// Category 2: Article's body
									processText_ARTICLE_BODY(idTarget, payload.events[0].message.text);

								} else {

									// Other categories
									try {
										handleOtherCategories(msgText, payload, idTarget);
									} catch (IOException e) {
										System.out.println("Exception is raised ");
										e.printStackTrace();
									}

								}

							} else {

								// Retrieve and show the first question
								retrieveAndShowTheQuestion(idTarget, 1);

							}

						}

					} else {

						// Category 3: Answer
						// Tokenize the answer template
						// Get the question number
						// Answer template: No.<QUESTION_NO><SPACE>[your answer]: [ A ] desc...

						// Get No.<QUESTION_NO> and the answer
						String[] answerTemplateSplitted = msgText.split("\\s+");

						processText_ANSWER(idTarget, answerTemplateSplitted[0], answerTemplateSplitted[4]);

					}

				} else {

					if (payload.events[0].source.type.equals("group")){

						leaveGR(payload.events[0].source.groupId, "group");

					} else if (payload.events[0].source.type.equals("room")){

						leaveGR(payload.events[0].source.roomId, "room");

					}

				}

			}

		}

		return new ResponseEntity<String>(HttpStatus.OK);

	}

	private boolean isValidURLText(String textToTest) {

		UrlValidator urlValidator = new UrlValidator();
		boolean isValidURL = false;

		if (urlValidator.isValid(textToTest)) {
			isValidURL = true;
		}

		return isValidURL;

	}


	// Category 1
	private String processLinkContent(String targetID) {

		String content = "";

		try {

			final HTMLDocument htmlDoc = HTMLFetcher.fetch(new URL(payload.events[0].message.text));
			final TextDocument doc = new BoilerpipeSAXInput(htmlDoc.toInputSource()).getTextDocument();

			content = CommonExtractors.ARTICLE_EXTRACTOR.getText(doc);

			//System.out.println(content);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return content;

	}


	// Category 2
	private void processText_ARTICLE_BODY(String targetID, String dataTest) {

		// the location of resources
		String pathToAllResources = "data/";

		// the location of the original training data
		String pathToOriginalTrainingData = "data/original_data/data_original_blank.txt";

		// the location of the file of instances
		String pathToFileOfInstances = "data/tagged_data/data_instances_tagged.txt";

		// the location of the final training data
		//String pathToFinalTrainingData = "data/train/data_instances_tagged_custom_100.arff";

		/* PHASE 0 - INSTANCES GENERATION FOR TRAINING DATA - DONE
		// retrieve the list of the original data
		List<String> listOfOriginData;

		// generate the instances for each data in the list of original data
		InstancesGenerator ig = new InstancesGenerator(pathToOriginalTrainingData, pathToFileOfInstances);
		listOfOriginData = ig.retrieveOriginalData();
		ig.generateInstances(listOfOriginData);
		*/


		// INSTANCES GENERATION FOR TESTING DATA
		NewDataController ndc = new NewDataController(dataTest);
		ndc.generateInstances();

		List<String> listOfActualNewDataToBeTested = ndc.getListOfActualNewData();
		List<String> listOfBlankNewDataToBeTested = ndc.getListOfBlankNewData();

		List<String> listOfEligibleNewData_ACTUAL = new ArrayList<String>();
		List<String> listOfEligibleNewData_BLANK = new ArrayList<String>();


		/* PHASE 1 - WORK IN PROGRESS */
		// start training
		Trainer tr = new Trainer(pathToAllResources);
		//tr.train();


		/* PHASE 2 - CLASSIFY TRUE OR FALSE */
		PolarityClassifier pc = new PolarityClassifier(pathToAllResources, tr.getTextAttributes(), tr.getComplexAttributes());

		QuestionPreprocessor qp = new QuestionPreprocessor(pathToAllResources);

		for (int idx = 0; idx < listOfBlankNewDataToBeTested.size(); idx++) {

			qp.setQuestion(listOfBlankNewDataToBeTested.get(idx));
			qp.startProc();

			Instances[] all = qp.getAllInstances();

			// Start the analysis
			String out = pc.test(all);

			// Show the result
			System.out.println("Prediction: " + out);
			System.out.println("Certainty: " + pc.getPredictionCertainty());


			//////////////////
			//pushMessage(targetID, "pred: " + out + " " + "cert: " + pc.getPredictionCertainty());
			//////////////////


			// Pass the sentence having true label to the Distractor Generator
			if (out.equals("1")) {
				//generateDistractor(listOfActualNewDataToBeTested.get(idx), listOfBlankNewDataToBeTested.get(idx));
				listOfEligibleNewData_ACTUAL.add(listOfActualNewDataToBeTested.get(idx));
				listOfEligibleNewData_BLANK.add(listOfBlankNewDataToBeTested.get(idx));
			}

		}

		// HANDLER for the case when the size of the true label is 0?
		if (listOfEligibleNewData_ACTUAL.size() != 0) {


			pushMessage(targetID, "I've got " + listOfEligibleNewData_ACTUAL.size() + " eligible questions!");


			List<Integer> listOfDBAccessStatus = generateDistractor(targetID, listOfEligibleNewData_ACTUAL, listOfEligibleNewData_BLANK);

			// Check all the storing status. Send error message when there is one or more zero status
			int foundZeroStatus = 0;

			for (int storingStatus : listOfDBAccessStatus) {

				if (storingStatus == 0) {

					// There was a failure in storing the data into the database
					foundZeroStatus = 1;
					break;
				}

			}

			if (foundZeroStatus == 1) {

				// Send error push message to user
				pushMessage(targetID, "Sorry, there was a problem in accessing the database.");

			} else {

				// Send a push message notifying that the bot had generated the questions successfully as well as asking the user
				// to click 'Start' button to start the test
				startTheQuizMessage();

			}

		} else {

			pushMessage(targetID, "Sorry, I can't generate questions from the requested article.");

		}


	}


	// Category 3
	private void processText_ANSWER(String targetID, String questionNo, String chosenAnswer) {

		// Get the question number by tokenizing it with dot as the delimiter
		String[] tokenizedQuestionNo = questionNo.split("\\.");

		// Get the question number
		int actualQuestNo = Integer.parseInt(tokenizedQuestionNo[1]);


		// --------------------------------------------------------

		//////////////////////////////
		pushMessage(targetID, "CHECK ANSWER: " + actualQuestNo + " : " + chosenAnswer);
		//////////////////////////////

		// CHECK THE ANSWER

		AnswerController ac = new AnswerController(targetID, lChannelAccessToken, tokenizedQuestionNo[1], chosenAnswer);

		// set mDao
		ac.setDao(mDao);

		// check the result
		//int answerValue = ac.checkAnswer();

		// store the answer in the database
		int storingAnswerStatus = ac.storeAnswerInDB();


		// --------------------------------------------------------

		// Retrieve the next question
		retrieveAndShowTheQuestion(targetID, actualQuestNo + 1);

	}


	/**
	 * REMOVE targetID & lChannelAccessToken on generateDistractor and dg.generateDistractor and CTOR - DEBUGGING PURPOSE
	 */
	private List<Integer> generateDistractor(String targetID, List<String> listOfEligibleActualNewData, List<String> listOfEligibleBlankNewData) {

		String pathToAllEnglishWords = "data/english_dict/words.txt";

		DistractorGenerator dg = new DistractorGenerator(lChannelAccessToken, pathToAllEnglishWords, listOfEligibleActualNewData, listOfEligibleBlankNewData);

		// set mDao
		dg.setDao(mDao);

		// retrieve and set up all english words
		List<String> listOfEnglishWords = dg.retrieveAllEnglishWords();
		dg.setListOfEnglishWords(listOfEnglishWords);


		pushMessage(targetID, "Successfully set list of english words: " + listOfEnglishWords.size() + ", " + listOfEligibleBlankNewData.size());


		List<Integer> listOfDBAccessStatus = dg.generateDistractor(targetID);

		return listOfDBAccessStatus;

	}


	private void retrieveAndShowTheQuestion(String targetID, int questionNumber) {



		//////////////////////////
		pushMessage(targetID, "Inside retrieveAndShowTheQuestion: " + questionNumber);
		//////////////////////////


		QuestionController qc = new QuestionController(questionNumber);

		// set mDao
		qc.setDao(mDao);

		// retrieve question with number <questionNumber>

		// REMOVE THE TWO PARAMETERS !!!
		Question retrievedQuestion = qc.retrieveQuestion(targetID, lChannelAccessToken);


		// Check if the whole questions are already shown
		if (retrievedQuestion == null) {

			// Send a push message notifying that the quiz is finished
			pushMessage(targetID, "You've answered all the questions! Thank you.");

			// Show the result
			showTheQuizResult(targetID);

		} else {

			// Question and answer text
			String questionAndAnswer = "[Question " + String.valueOf(questionNumber) + "]\n\n";
			questionAndAnswer = questionAndAnswer + retrievedQuestion.getQuestion() + "\n\n";
			questionAndAnswer = questionAndAnswer + "[Answers]:" + "\n\n";
			questionAndAnswer = questionAndAnswer + "[ A ] " + retrievedQuestion.getChoiceZero() + "\n";
			questionAndAnswer = questionAndAnswer + "[ B ] " + retrievedQuestion.getChoiceOne() + "\n";
			questionAndAnswer = questionAndAnswer + "[ C ] " + retrievedQuestion.getChoiceTwo() + "\n";
			questionAndAnswer = questionAndAnswer + "[ D ] " + retrievedQuestion.getChoiceThree();


			// Label answer: A, B, C, D
			String[] labelAnswer = new String[4];
			labelAnswer[0] = "A";
			labelAnswer[1] = "B";
			labelAnswer[2] = "C";
			labelAnswer[3] = "D";


			// Answer template: No.<questionNo><SPACE>[your_answer]: A
			String[] actionAnswer = new String[4];
			actionAnswer[0] = "No." + String.valueOf(questionNumber) + " " + "[your answer]: [ A ] " + retrievedQuestion.getChoiceZero();
			actionAnswer[1] = "No." + String.valueOf(questionNumber) + " " + "[your answer]: [ B ] " + retrievedQuestion.getChoiceOne();
			actionAnswer[2] = "No." + String.valueOf(questionNumber) + " " + "[your answer]: [ C ] " + retrievedQuestion.getChoiceTwo();
			actionAnswer[3] = "No." + String.valueOf(questionNumber) + " " + "[your answer]: [ D ] " + retrievedQuestion.getChoiceThree();


			// Show the button template so that user can choose his/her answer
			String questionMsg = "Choose your answer";
			String title = "Answer";


			// Show the question and answer
			pushMessage(targetID, questionAndAnswer);

			// Show the answer button template
			buttonTemplate(questionMsg, labelAnswer, actionAnswer, title);

		}

	}

	// Method for showing the final result of the quiz
	private void showTheQuizResult(String targetID) {

		List<Answer> answerHistory = mDao.getAnswerHistory();

		// send a push message notifying the quiz result
		String quizResult = "";
		quizResult = quizResult + "[Quiz result]\n\n";
		quizResult = quizResult + "(No, Your Answer, Right Answer)\n";
		quizResult = quizResult + "-------------------------------\n\n";

		for (Answer ans : answerHistory) {

			quizResult = quizResult + "(" + ans.getQuestionNo() + ", " + ans.getChosenAnswer() + ", " + ans.getRightAnswer() + ")\n";

		}

		pushMessage(targetID, quizResult);

	}


	// Method for retrieving user profile (user id, display name, image, status)
	private void getUserProfile(String userId) {

		Response<UserProfileResponse> response = null;

		try {

			response = LineMessagingServiceBuilder
					.create(lChannelAccessToken)
					.build()
					.getProfile(userId)
					.execute();

		} catch (IOException e) {
			e.printStackTrace();
		}

		if (response.isSuccessful()) {

			UserProfileResponse profile = response.body();

			System.out.println(profile.getDisplayName());
			System.out.println(profile.getPictureUrl());
			System.out.println(profile.getStatusMessage());

			displayName = profile.getDisplayName();

		} else {

			System.out.println(response.code() + " " + response.message());

		}

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


	// Method for sending message when there a user adds this bot as friend
	private void greetingMessage() {

		getUserProfile(payload.events[0].source.userId);

		String greetingMsg = "Hi " + displayName + "! I'm DeepEnglish and I'd like to help you in improving your English reading comprehension skill. Let's learn together!" + payload.events[0].replyToken;

		String[] label = new String[1];
		label[0] = "Read the Rules";

		String[] action = new String[1];
		action[0] = "[read the rules]";

		String title = "Welcome";

		buttonTemplate(greetingMsg, label, action, title);

	}


	// Method for replying message to user
	private void replyToUser(String rToken, String messageToUser) {

		TextMessage textMessage = new TextMessage(messageToUser);
		ReplyMessage replyMessage = new ReplyMessage(rToken, textMessage);

		try {

			Response<BotApiResponse> response = LineMessagingServiceBuilder
					.create(lChannelAccessToken)
					.build()
					.replyMessage(replyMessage)
					.execute();

			System.out.println("Reply Message: " + response.code() + " " + response.message());

		} catch (IOException e) {
			System.out.println("Exception is raised ");
			e.printStackTrace();
		}

	}


	// Method for sending message as a notification to start the quiz
	private void startTheQuizMessage() {

		String greetingMsg = "Thank you for the resource. I'd successfully generated several questions based on the resource you submitted. Just click the button below to start the quiz.";

		String[] label = new String[1];
		label[0] = "Start the Quiz";

		String[] action = new String[1];
		action[0] = "[start the quiz]";

		String title = "Start the Quiz";

		buttonTemplate(greetingMsg, label, action, title);

	}


	// Method for handling other categories
	private void handleOtherCategories(String userTxt, Payload ePayload, String targetID) throws IOException {

		/*
		if (userTxt.equals("lihat daftar event")){
			pushMessage(targetID, "Aku akan mencarikan event aktif di dicoding! Dengan syarat : Kasih tau dong LINE ID kamu (pake \'id @\' ya). Contoh :");
			pushMessage(targetID, "id @john");
		}
		else if (userTxt.contains("summary")){
			pushMessage(targetID, event.getData().get(Integer.parseInt(String.valueOf(userTxt.charAt(1)))-1).getSummary());
		} else {
			pushMessage(targetID, "Hi "+displayName+", aku belum  mengerti maksud kamu. Silahkan ikuti petunjuk ya :)");
			greetingMessage();
		}
		*/

	}


	// Method for creating a button template
	private void buttonTemplate(String message, String[] label, String[] action, String title) {

		ButtonsTemplate buttonsTemplate;

		if (title.equals("Welcome") || title.equals("Start the Quiz")) {

			buttonsTemplate = new ButtonsTemplate(null, null, message,
					Arrays.asList(new MessageAction(label[0], action[0])));

		} else {

			buttonsTemplate = new ButtonsTemplate(null, title, message,
					Arrays.asList(new MessageAction(label[0], action[0]),
							new MessageAction(label[1], action[1]),
							new MessageAction(label[2], action[2]),
							new MessageAction(label[3], action[3])
					));

		}

		TemplateMessage templateMessage = new TemplateMessage(title, buttonsTemplate);
		PushMessage pushMessage = new PushMessage(payload.events[0].source.userId, templateMessage);

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


	// Method for leaving group or room
	private void leaveGR(String id, String type) {

		try {

			if (type.equals("group")) {

				Response<BotApiResponse> response = LineMessagingServiceBuilder
						.create(lChannelAccessToken)
						.build()
						.leaveGroup(id)
						.execute();

				System.out.println(response.code() + " " + response.message());

			} else if (type.equals("room")) {

				Response<BotApiResponse> response = LineMessagingServiceBuilder
						.create(lChannelAccessToken)
						.build()
						.leaveRoom(id)
						.execute();

				System.out.println(response.code() + " " + response.message());

			}

		} catch (IOException e) {
			System.out.println("Exception is raised");
			e.printStackTrace();
		}

	}
	
}