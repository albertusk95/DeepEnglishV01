package com.sai.deepenglish.Master;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

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
import com.sai.deepenglish.Classifier.MyFilteredClassifier;
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

	private Date dateStart;
	private Date dateFinish;

	private SimpleDateFormat ft;

	// quizStatus 0 means the quiz is not running
	private int quizStatus = 0;

	private String quizNotification;


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

				if (quizStatus == 0) {

					// If the message is not a text (image, sticker, etc), just send a greeting message
					greetingMessage();

				} else {

					// quiz is running. I don't recognize the request. Try it later.
					quizNotification = "Hi, I can't accept your request while the quiz is running. Please try again later when the quiz is finished.";
					sendQuizNotification(idTarget);

				}

			} else {

				msgText = payload.events[0].message.text;
				msgText = msgText.toLowerCase();
				msgTextLength = msgText.length();

				// Check whether the user asks the bot to leave the chat room
				if (!msgText.contains("bot leave")) {

					// Check the message's category
					if (!msgText.contains("[your answer]:")) {

						if (isValidURLText(msgText)) {

							if (quizStatus == 0) {

								quizStatus = 1;

								// Category 1: Link
								String linkContent = processLinkContent(idTarget);
								processText_ARTICLE_BODY(idTarget, linkContent);

							} else {

								// quiz is running
								quizNotification = "Hi, the quiz is running right now. I can't accept that new URL as a resource. Please try again later when the quiz is finished.";
								sendQuizNotification(idTarget);

							}

						} else {

							if (!msgText.contains("[start the quiz]")) {

								// Check whether it's an article's body based on the message length
								if (msgTextLength > 50) {

									if (quizStatus == 0) {

										replyToUser(payload.events[0].replyToken, "Hi, your text has more than 50 characters. I consider it as a new resource for the quiz.");

										// Category 2: Article's body
										processText_ARTICLE_BODY(idTarget, payload.events[0].message.text);

									} else {

										// quiz is still running
										quizNotification = "Hi, I can't accept that new article as a resource while the quiz is running. Please try again later when the quiz is finished.";
										sendQuizNotification(idTarget);

									}

								} else {

									// Other categories
									try {

										handleOtherCategories(msgText, idTarget);

									} catch (IOException e) {
										System.out.println("Exception is raised ");
										e.printStackTrace();
									}

								}

							} else {

								if (quizStatus == 0) {

									// set the quiz status
									quizStatus = 1;

									// Initialize the timer
									initializeTimer(idTarget);

									// Retrieve and show the first question
									retrieveAndShowTheQuestion(idTarget, 1);

								} else {

									// quiz is still running
									quizNotification = "Hi, the quiz is currently running. You can start again when this quiz is completed.";
									sendQuizNotification(idTarget);

								}

							}

						}

					} else {

						// Category 3: Answer
						// Tokenize the answer template
						// Get the question number
						// Answer template: No.<QUESTION_NO><SPACE>[your answer]: [ A ] desc...

						if (quizStatus == 1) {

							// Get No.<QUESTION_NO> and the answer
							String[] answerTemplateSplitted = msgText.split("\\s+");

							processText_ANSWER(idTarget, answerTemplateSplitted[0], answerTemplateSplitted[4]);

						} else {

							// quiz is not running
							quizNotification = "Hi, there is no any quiz running right now. You can submit an answer in a running quiz.";
							sendQuizNotification(idTarget);

						}
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
		//String pathToOriginalTrainingData = "data/original_data/data_original_blank.txt";

		// the location of the file of instances
		//String pathToFileOfInstances = "data/tagged_data/data_instances_tagged.txt";

		// the location of the final training data
		//String pathToFinalTrainingData = "data/train/data_instances_tagged_custom_100.arff";


		/////////////////////////////////////////////////////////
		// INSTANCE GENERATOR (USED ONLY WHEN NEEDED)
		/////////////////////////////////////////////////////////

		/*
		// retrieve the list of the original data
		List<String> listOfOriginData;

		// generate the instances for each data in the list of original data
		InstancesGenerator ig = new InstancesGenerator(pathToOriginalTrainingData, pathToFileOfInstances);
		listOfOriginData = ig.retrieveOriginalData();
		ig.generateInstances(listOfOriginData);
		*/


		/////////////////////////////////////////////////////////
		// LEARNING (USED ONLY WHEN NEEDED)
		/////////////////////////////////////////////////////////

		/*
		MyFilteredLearner learner = new MyFilteredLearner();

		learner.loadDataset(pathToAllResources + "train/data_instances_tagged_custom_text_100.arff");

		// Evaluation mus be done before training
		learner.evaluate();
		learner.learn();
		learner.saveModel(pathToAllResources + "models/data_text_100.model");
		*/


		/////////////////////////////////////////////////////////
		// INSTANCES GENERATOR FOR TESTING DATA
		/////////////////////////////////////////////////////////

		NewDataController ndc = new NewDataController(dataTest);
		ndc.generateInstances();

		List<String> listOfActualNewDataToBeTested = ndc.getListOfActualNewData();
		List<String> listOfBlankNewDataToBeTested = ndc.getListOfBlankNewData();

		// List of eligible questions
		List<String> listOfEligibleNewData_ACTUAL = new ArrayList<String>();
		List<String> listOfEligibleNewData_BLANK = new ArrayList<String>();

		// List of eligible questions which are sorted by the prediction score


		/* PHASE 1 - WORK IN PROGRESS */
		// start training
		//Trainer tr = new Trainer(pathToAllResources);
		//tr.train();


		/* PHASE 2 - CLASSIFY TRUE OR FALSE */
		/*
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
		*/


		/////////////////////////////////////////////////////////
		// CLASSIFYING
		/////////////////////////////////////////////////////////

		MyFilteredClassifier classifier = new MyFilteredClassifier();
		classifier.loadModel(pathToAllResources + "models/data_text_100.model");

		String predClassify;

		for (int idx = 0; idx < listOfBlankNewDataToBeTested.size(); idx++) {

			classifier.setDataTest(listOfBlankNewDataToBeTested.get(idx));

			classifier.makeInstance();

			predClassify = classifier.classify();

			// Pass the sentence having true label to the Distractor Generator
			if (predClassify.equals("1")) {
				listOfEligibleNewData_ACTUAL.add(listOfActualNewDataToBeTested.get(idx));
				listOfEligibleNewData_BLANK.add(listOfBlankNewDataToBeTested.get(idx));
			}

		}


		// HANDLER for the case when the size of the true label is 0?
		if (listOfEligibleNewData_ACTUAL.size() != 0) {


			pushMessage(targetID, "Hi! I've got " + listOfEligibleNewData_ACTUAL.size() + " eligible questions!");


			// Clear the questions table
			int clearingStatus = clearQuestionsTable(targetID);

			if (clearingStatus >= 0) {

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

				// Send error push message to user
				pushMessage(targetID, "Sorry, there was a problem in accessing the database [clear: table question]");

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
		//pushMessage(targetID, "CHECK ANSWER: " + actualQuestNo + " : " + chosenAnswer);
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

		List<Integer> listOfDBAccessStatus;

		DistractorGenerator dg = new DistractorGenerator(lChannelAccessToken, pathToAllEnglishWords, listOfEligibleActualNewData, listOfEligibleBlankNewData);

		// set mDao
		dg.setDao(mDao);

		// retrieve and set up all english words
		List<String> listOfEnglishWords = dg.retrieveAllEnglishWords();
		dg.setListOfEnglishWords(listOfEnglishWords);


		pushMessage(targetID, "Successfully set list of english words: " + listOfEnglishWords.size() + ", " + listOfEligibleBlankNewData.size());


		listOfDBAccessStatus = dg.generateDistractor(targetID);

		return listOfDBAccessStatus;

	}


	// Method for initializing questions table
	private int clearQuestionsTable(String targetID) {

		// RETURN the number of deleted rows

		int clearingQuestTableStatus = mDao.clearQuestionsTable(targetID, lChannelAccessToken);

		///////////////////////
		pushMessage(targetID, "clearQuestionsTableStatus: " + clearingQuestTableStatus);
		///////////////////////

		return clearingQuestTableStatus;

	}


	// Method for initializing answers table
	private int clearAnswersTable(String targetID) {

		// RETURN the number of deleted rows

		int clearingAnsTableStatus = mDao.clearAnswersTable(targetID, lChannelAccessToken);

		///////////////////////
		pushMessage(targetID, "clearAnswersTableStatus: " + clearingAnsTableStatus);
		///////////////////////

		return clearingAnsTableStatus;

	}


	private void retrieveAndShowTheQuestion(String targetID, int questionNumber) {


		//////////////////////////
		//pushMessage(targetID, "Inside retrieveAndShowTheQuestion: " + questionNumber);
		//////////////////////////


		int clearingAnsTableStatus = 0;

		if (questionNumber == 1) {

			clearingAnsTableStatus = clearAnswersTable(targetID);

		}

		if (clearingAnsTableStatus >= 0) {

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

		} else {

			pushMessage(targetID, "Sorry, there was an error in accessing the database [clear: table answer]");

		}

	}


	// Method for timer initialization
	private void initializeTimer(String targetID) {

		dateStart = new Date();


		/////////////////
		//pushMessage(targetID, "Inside initializeTimer");
		/////////////////


		// HH converts hour in 24 hours format (0-23), day calculation
		ft = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

		try {

			// Get the start date
			dateStart = ft.parse(ft.format(dateStart));

		} catch(Exception e) {
			e.printStackTrace();
		}


		/////////////////
		pushMessage(targetID, "date start: " + dateStart);
		/////////////////


	}

	// Method for computing the time difference elements
	private List<Long> getListOfTimeDiffElements() {

		List<Long> listOfTimeDiffElements = new ArrayList<Long>();

		// HH converts hour in 24 hours format (0-23), day calculation
		dateFinish = new Date();

		ft = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

		try {

			dateFinish = ft.parse(ft.format(dateFinish));

			// Compute the difference in milliseconds
			long diff = dateFinish.getTime() - dateStart.getTime();

			long diffSeconds = diff / 1000 % 60;
			long diffMinutes = diff / (60 * 1000) % 60;
			long diffHours = diff / (60 * 60 * 1000) % 24;
			long diffDays = diff / (24 * 60 * 60 * 1000);

			listOfTimeDiffElements.add(diffDays);
			listOfTimeDiffElements.add(diffHours);
			listOfTimeDiffElements.add(diffMinutes);
			listOfTimeDiffElements.add(diffSeconds);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return listOfTimeDiffElements;

	}

	// Method for showing the final result of the quiz
	private void showTheQuizResult(String targetID) {

		// set the quiz status to 0 (stop)
		quizStatus = 0;

		List<Answer> answerHistory = mDao.getAnswerHistory();

		// send a push message notifying the quiz result
		String quizResult = "";

		quizResult = quizResult + "[QUIZ RESULT]";

		// create the final score
		int countCorrectAns = 0;

		for (Answer ans : answerHistory) {

			if (ans.getChosenAnswer().toLowerCase().equals(ans.getRightAnswer().toLowerCase())) {
				countCorrectAns++;
			}

		}

		quizResult = quizResult + "\n\n";
		quizResult = quizResult + "[Number of Correct Answers]\n";
		quizResult = quizResult + "---------------------------\n";
		quizResult = quizResult + String.valueOf(countCorrectAns);

		// create the time needed from start to finish
		List<Long> listOfTimeDiffElements = getListOfTimeDiffElements();

		quizResult = quizResult + "\n\n";
		quizResult = quizResult + "[Completion Time]\n";
		quizResult = quizResult + "-----------------\n";
		quizResult = quizResult + listOfTimeDiffElements.get(0) + " days, ";
		quizResult = quizResult + listOfTimeDiffElements.get(1) + " hours, ";
		quizResult = quizResult + listOfTimeDiffElements.get(2) + " minutes, ";
		quizResult = quizResult + listOfTimeDiffElements.get(3) + " seconds";

		quizResult = quizResult + "\n\n";
		quizResult = quizResult + "[Answer History]\n";
		quizResult = quizResult + "---------------------------------\n";
		quizResult = quizResult + "(No, Your Answer, Correct Answer)\n";
		quizResult = quizResult + "---------------------------------\n\n";

		for (Answer ans : answerHistory) {

			quizResult = quizResult + "(" + ans.getQuestionNo() + ",   " + ans.getChosenAnswer().toUpperCase() + ",   " + ans.getRightAnswer() + ")\n";

		}

		// Send as a push message
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


	// Method for notifying the quiz state
	private void sendQuizNotification(String targetID) {

		//getUserProfile(payload.events[0].source.userId);

		String title = "Your Quiz State";
		String runningQuizMsg = quizNotification;

		String[] label = new String[3];
		label[0] = "Who am I?";
		label[1] = "What can you do?";
		label[2] = "What are my technical support?";

		String[] action = new String[3];
		action[0] = "[who am i]";
		action[1] = "[what can you do]";
		action[2] = "[what are my technical support]";


		buttonTemplate(runningQuizMsg, label, action, title);

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

		String greetingMsg = "Hi " + displayName + "! I'm DeepEnglish and I'd like to help you in improving your English reading comprehension skill. Just click on any button below to get acquainted with me :)";

		String[] label = new String[3];
		label[0] = "Who am I?";
		label[1] = "What can you do?";
		label[2] = "What are my technical support?";

		String[] action = new String[3];
		action[0] = "[who am i]";
		action[1] = "[what can you do]";
		action[2] = "[what are my technical support]";

		String title = "Welcome to DeepEnglish!";

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
	private void handleOtherCategories(String userTxt, String targetID) throws IOException {

		String deepEngMsg = "";

		if (userTxt.contains("end quiz")) {

			// finish the quiz and show the result
			replyToUser(payload.events[0].replyToken, "As you wish. Ending the quiz. Done.");

			showTheQuizResult(targetID);

		} else if (userTxt.contains("[who am i]")) {

			// Show the basic idea of DeepEnglish

			deepEngMsg = deepEngMsg + "[WHO AM I?]\n";
			deepEngMsg = deepEngMsg + "-----------";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "I'm here to help you in improving your English reading comprehension skill. ";
			deepEngMsg = deepEngMsg + "You'll learn through a reading quiz in the form of multiple choice questions.";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "[THE BASIC IDEA]\n";
			deepEngMsg = deepEngMsg + "----------------\n";
			deepEngMsg = deepEngMsg + "You can read any articles written in English and try to understand what the article is all about. ";
			deepEngMsg = deepEngMsg + "Then, just give me that article and I'll generate several multiple choice questions based on it. ";
			deepEngMsg = deepEngMsg + "I'll try to understand the topic of the article and provide you with qualified questions. I'll also ";
			deepEngMsg = deepEngMsg + "try to create the 4 possible answers which can probably be tricky for you. I do this to examine your ";
			deepEngMsg = deepEngMsg + "understanding on the article you gave to me beforehand.";

			// Send as a push message
			pushMessage(targetID, deepEngMsg);

		} else if (userTxt.contains("[what can you do]")) {

			deepEngMsg = deepEngMsg + "[WHAT CAN YOU DO?]\n";
			deepEngMsg = deepEngMsg + "------------------";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "Here are the main process you can do to get the maximum learning value:\n";
			deepEngMsg = deepEngMsg + "1. Search any articles written in English. The topic can be anything.\n\n";
			deepEngMsg = deepEngMsg + "2. Read that article and try to get its primary topic. You should do this in the similar way ";
			deepEngMsg = deepEngMsg + "when you take a TOEFL, IELTS, or TOEIC test.\n\n";
			deepEngMsg = deepEngMsg + "3. Afterwards, simply send that article to me. You can do it in two ways, namely provide me the ";
			deepEngMsg = deepEngMsg + "URL (link) of that article OR just by copying and pasteing the article.\n\n";
			deepEngMsg = deepEngMsg + "4. I'll try to understand the article and then generate several multiple choice questions ";
			deepEngMsg = deepEngMsg + "based on it. This process may take a few seconds, so I hope that you can be patient :)\n\n";
			deepEngMsg = deepEngMsg + "5. If I successfully found the eligible questions, I'll show a message notifying the next ";
			deepEngMsg = deepEngMsg + "process. Vice versa, I'll also show a message notifying that the process of question generation ";
			deepEngMsg = deepEngMsg + "can't be completed successfully. For the latter case, there's a possibility that I couldn't find ";
			deepEngMsg = deepEngMsg + "any questions that are good enough to examine your understanding. Just try to provide me with another article :)\n\n";
			deepEngMsg = deepEngMsg + "6. For the success condition, I'll provide the question one by one and move on to the other ";
			deepEngMsg = deepEngMsg + "when you've submitted an answer for the corresponding question.\n\n";
			deepEngMsg = deepEngMsg + "7. You can choose to complete all questions generated by me OR end the quiz while it's running. ";
			deepEngMsg = deepEngMsg + "For the latter case, just type 'end quiz' and I'll stop the quiz.\n\n";
			deepEngMsg = deepEngMsg + "8. After the quiz is finished (or ended in the middle session), I'll show your quiz result. ";
			deepEngMsg = deepEngMsg + "It includes the total number of correct answers, the completion time, and the answer history.\n\n";

			// Send as a push message
			pushMessage(targetID, deepEngMsg);

		}

	}


	// Method for creating a button template
	private void buttonTemplate(String message, String[] label, String[] action, String title) {

		ButtonsTemplate buttonsTemplate;

		if (title.equals("Start the Quiz")) {

			buttonsTemplate = new ButtonsTemplate(null, null, message,
					Arrays.asList(new MessageAction(label[0], action[0])));

		} else if (title.equals("Welcome to DeepEnglish!") || title.equals("Your Quiz State")) {

			buttonsTemplate = new ButtonsTemplate(null, null, message,
					Arrays.asList(new MessageAction(label[0], action[0]),
							new MessageAction(label[1], action[1]),
							new MessageAction(label[2], action[2])
					));

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