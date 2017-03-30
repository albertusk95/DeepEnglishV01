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
import com.sai.deepenglish.Spider.Spider;
import com.sai.deepenglish.Summariser.TextSummariser;
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

	private String idTarget = " ";


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


				int isRequestForSpecialFeature = 0;
				int isRequestForSummary = 0;
				int isRequestForFind = 0;


				// CHECK FOR SPECIAL FEATURES (@desummary AND @defind)
				if (msgText.contains("@desummary") || msgText.contains("@defind")) {

					String[] special_req_splitted = msgText.split("\\s+");

					if (special_req_splitted.length == 3) {

						// Possibly a request for special feature
						isRequestForSpecialFeature = 1;

						if (special_req_splitted[0].equals("@desummary")) {

							// Validate the format of @desummary
							// FORMAT: @desummary<SPACE><number_of_summarized_text><SPACE><URL>
							if (special_req_splitted[1].matches("[-+]?\\d*\\.?\\d+") && isValidURLText(special_req_splitted[2])) {

								// Valid
								isRequestForSummary = 1;
								replyToUser(payload.events[0].replyToken, "Summarizing the article in the URL, please wait...");

							}

						} else if (special_req_splitted[0].equals("@defind")) {

							// Validate the format of @defind
							// FORMAT: @defind<SPACE><keyword><SPACE><source_url>
							if (isValidURLText(special_req_splitted[2])) {

								// Valid
								isRequestForFind = 1;
								replyToUser(payload.events[0].replyToken, "Searching for keyword '" + special_req_splitted[1] + "' in " + special_req_splitted[2]);

							}

						}

					}

				}


				if (isRequestForSpecialFeature == 0) {

					// MAIN FEATURE CONTROLLER

					// Check whether the user asks the bot to leave the chat room
					if (!msgText.contains("bot leave")) {

						// Check the message's category
						if (!msgText.contains("[your answer]:")) {

							if (isValidURLText(msgText)) {

								if (quizStatus == 0) {

									replyToUser(payload.events[0].replyToken, "Hi, based on my analysis it's a valid URL. I'll try to get its content and generate the questions. This could take a few seconds based on the length of the article. So, please wait...");

									// Category 1: Link
									String linkContent = processLinkContent(msgText);
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

											// Check if the request contains special word like @de_summary, etc...
											if (msgText.contains("@desummary")) {

												// Process the special request
												try {

													handleOtherCategories(msgText, idTarget);

												} catch (IOException e) {
													System.out.println("Exception is raised ");
													e.printStackTrace();
												}

											} else {

												replyToUser(payload.events[0].replyToken, "Your text has more than 50 characters and based on my analysis it is not an URL. I'll generate the questions. This could take a few seconds based on the lenght of the article. So, please wait...");

												// Category 2: Article's body
												processText_ARTICLE_BODY(idTarget, payload.events[0].message.text);

											}

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

						if (payload.events[0].source.type.equals("group")) {

							leaveGR(payload.events[0].source.groupId, "group");

						} else if (payload.events[0].source.type.equals("room")) {

							leaveGR(payload.events[0].source.roomId, "room");

						}

					}

				} else {

					// SPECIAL FEATURES CONTROLLER
					if (isRequestForSummary == 1 || isRequestForFind == 1) {

						// Other categories
						try {

							handleOtherCategories(msgText, idTarget);

						} catch (IOException e) {
							System.out.println("Exception is raised ");
							e.printStackTrace();
						}

					} else {

						// Invalid request for special features
						String invalidReqSpecialFeature = "";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "I detect that you likely wanted to access one of special features.";

						invalidReqSpecialFeature = invalidReqSpecialFeature + "\n\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "To do it, try to use the following formats:";

						invalidReqSpecialFeature = invalidReqSpecialFeature + "\n\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "- To summarize an article in an URL: @desummary<SPACE><number_of_summarized_text><SPACE><URL>\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "Example:\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "Query: @desummary 3 http://www.example.com\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "Result: Text 1. Text 2. Text 3.";

						invalidReqSpecialFeature = invalidReqSpecialFeature + "\n\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "- To retrieve several pages on a website containing your keyword: @defind<SPACE><keyword><SPACE><website_url>\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "Example:\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "Query: @defind fruit http://www.example.com\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "Result:\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "- http://www.example.com/page1\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "- http://www.example.com/page2\n";
						invalidReqSpecialFeature = invalidReqSpecialFeature + "- http://www.example.com/page3\n";

						replyToUser(payload.events[0].replyToken, invalidReqSpecialFeature);

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
	private String processLinkContent(String url_msg_txt) {

		String content = "";

		try {

			final HTMLDocument htmlDoc = HTMLFetcher.fetch(new URL(url_msg_txt));
			final TextDocument doc = new BoilerpipeSAXInput(htmlDoc.toInputSource()).getTextDocument();

			content = CommonExtractors.ARTICLE_EXTRACTOR.getText(doc);

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


			///////////////////////////////////
			pushMessage(targetID, "Hi! I've got " + listOfEligibleNewData_ACTUAL.size() + " eligible questions. I'm generating the four answer options. This could take a few minutes based on the number of generated questions. So, please wait...");
			///////////////////////////////////


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
					pushMessage(targetID, "Sorry, there was a problem in accessing the database [storing questions]");

				} else {

					// Send a push message notifying that the bot had generated the questions successfully as well as asking the user
					// to click 'Start' button to start the test
					startTheQuizMessage();

				}

			} else {

				// Send error push message to user
				pushMessage(targetID, "Sorry, there was a problem in accessing the database [clearing questions]");

			}

		} else {

			pushMessage(targetID, "Sorry, based on my knowledge I couldn't find any eligible questions that are good enough to examnine your understanding. Please try again with another article.");

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

		// set id target
		ac.setIdTarget(idTarget);

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

		// set idTarget
		dg.setIdTarget(idTarget);

		// retrieve and set up all english words
		List<String> listOfEnglishWords = dg.retrieveAllEnglishWords();
		dg.setListOfEnglishWords(listOfEnglishWords);


		/////////////////////////////////////////
		//pushMessage(targetID, "Successfully set list of english words: " + listOfEnglishWords.size() + ", " + listOfEligibleBlankNewData.size());
		/////////////////////////////////////////


		listOfDBAccessStatus = dg.generateDistractor(targetID);

		return listOfDBAccessStatus;

	}


	// Method for initializing questions table
	private int clearQuestionsTable(String targetID) {

		// RETURN the number of deleted rows

		int clearingQuestTableStatus = mDao.clearQuestionsTable(targetID, lChannelAccessToken, idTarget);

		///////////////////////
		//pushMessage(targetID, "clearQuestionsTableStatus: " + clearingQuestTableStatus);
		///////////////////////

		return clearingQuestTableStatus;

	}


	// Method for initializing answers table
	private int clearAnswersTable(String targetID) {

		// RETURN the number of deleted rows

		int clearingAnsTableStatus = mDao.clearAnswersTable(targetID, lChannelAccessToken, idTarget);

		///////////////////////
		//pushMessage(targetID, "clearAnswersTableStatus: " + clearingAnsTableStatus);
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

			// set id target
			qc.setIdTarget(idTarget);

			// retrieve question with number <questionNumber>

			// REMOVE THE TWO PARAMETERS !!!
			Question retrievedQuestion = qc.retrieveQuestion(targetID, lChannelAccessToken);


			// Check if the whole questions are already shown
			if (retrievedQuestion == null) {

				// Send a push message notifying that the quiz is finished
				pushMessage(targetID, "You've answered all the questions! Nice. Here is your quiz result.");

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

			pushMessage(targetID, "Sorry, there was an error in accessing the database [clearing answers]");

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
		//pushMessage(targetID, "date start: " + dateStart);
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

		List<Answer> answerHistory = mDao.getAnswerHistory(idTarget);

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
		quizResult = quizResult + "--------------------------------\n";
		quizResult = quizResult + String.valueOf(countCorrectAns) + " from " + answerHistory.size();

		// create the time needed from start to finish
		List<Long> listOfTimeDiffElements = getListOfTimeDiffElements();

		quizResult = quizResult + "\n\n";
		quizResult = quizResult + "[Completion Time]\n";
		quizResult = quizResult + "--------------------\n";
		quizResult = quizResult + listOfTimeDiffElements.get(0) + " days, ";
		quizResult = quizResult + listOfTimeDiffElements.get(1) + " hours, ";
		quizResult = quizResult + listOfTimeDiffElements.get(2) + " minutes, ";
		quizResult = quizResult + listOfTimeDiffElements.get(3) + " seconds";

		quizResult = quizResult + "\n\n";
		quizResult = quizResult + "[Answer History]\n";
		quizResult = quizResult + "----------------------------------\n";
		quizResult = quizResult + "(No, Your Answer, Correct Answer)\n";
		quizResult = quizResult + "----------------------------------\n\n";

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

		String[] label = new String[4];
		label[0] = "Who am I?";
		label[1] = "What can you do?";
		label[2] = "Special features";
		label[3] = "Others";

		String[] action = new String[4];
		action[0] = "[who am i]";
		action[1] = "[what can you do]";
		action[2] = "[special features]";
		action[3] = "[others]";

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


	// Method for sending message notifying that the bot does not understand the request
	private void botDoesNotUnderstandMessage() {

		String txt_not_understand = "Sorry, I don't understand your request. I hope that you want to read the rules. Cheers :)";

		String[] label = new String[4];
		label[0] = "Who am I?";
		label[1] = "What can you do?";
		label[2] = "Special features";
		label[3] = "Others";

		String[] action = new String[4];
		action[0] = "[who am i]";
		action[1] = "[what can you do]";
		action[2] = "[special features]";
		action[3] = "[others]";

		String title = "BotNotUnderstand";


		buttonTemplate(txt_not_understand, label, action, title);

	}


	// Method for sending message when there a user adds this bot as friend
	private void greetingMessage() {

		//getUserProfile(payload.events[0].source.userId);

		getUserProfile(idTarget);

		String greetingMsg = "Welcome to DeepEnglish! You can click on any button below to get acquainted with me.";

		String[] label = new String[4];
		label[0] = "Who am I?";
		label[1] = "What can you do?";
		label[2] = "Special features";
		label[3] = "Others";

		String[] action = new String[4];
		action[0] = "[who am i]";
		action[1] = "[what can you do]";
		action[2] = "[special features]";
		action[3] = "[others]";

		String title = "Hello, " + displayName;


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

		String greetingMsg = "Thanks for the resource and your patience. I'd successfully generated the questions and their answer options. Click the button below to start the quiz.";

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

		if (userTxt.equals("end quiz")) {

			// finish the quiz and show the result
			replyToUser(payload.events[0].replyToken, "Ending the quiz. Done. Thanks for taking the quiz and here is your result.");

			showTheQuizResult(targetID);

		} else if (userTxt.equals("[who am i]")) {

			// Show the basic idea of DeepEnglish

			deepEngMsg = deepEngMsg + "[ WHO AM I? ]\n";
			deepEngMsg = deepEngMsg + "---------------";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "I'm here to help you in improving your English reading comprehension skill. ";
			deepEngMsg = deepEngMsg + "You'll learn through a reading quiz in the form of multiple choice questions.";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "[ THE BASIC IDEA ]\n";
			deepEngMsg = deepEngMsg + "--------------------\n";
			deepEngMsg = deepEngMsg + "You can read any articles written in English and try to understand what the article is all about. ";
			deepEngMsg = deepEngMsg + "Then, just give me that article and I'll generate several multiple choice questions based on it. ";
			deepEngMsg = deepEngMsg + "I'll try to understand the topic of the article and provide you with qualified questions. I'll also ";
			deepEngMsg = deepEngMsg + "try to create the 4 possible answers which can probably be tricky for you. I do this to examine your ";
			deepEngMsg = deepEngMsg + "understanding on the article you gave to me beforehand.";

			// Send as a push message
			pushMessage(targetID, deepEngMsg);

		} else if (userTxt.equals("[what can you do]")) {

			deepEngMsg = deepEngMsg + "[ WHAT CAN YOU CAN DO ]\n";
			deepEngMsg = deepEngMsg + "------------------------";

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
			deepEngMsg = deepEngMsg + "It includes the total number of correct answers, the completion time, and the answer history.";

			// Send as a push message
			pushMessage(targetID, deepEngMsg);

		} else if (userTxt.equals("[special features]")) {

			deepEngMsg = deepEngMsg + "[ SPECIAL FEATURES ]";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "Besides my main capability to generate questions based on your article, I also provides these special features that can be used to speed up your study:\n";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "1. Summarizer: Got a good article in a website yet too long to read? Simply give your article and the number of summarized text you want to this feature and it will give you the only important information of the article. Noted that you can only provide the link containing the article, so you can't copy the article and paste it as a request. To use it, you need to provide a valid request format in order to get the desired result.";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "Format:\n";
			deepEngMsg = deepEngMsg + "- @desummary<SPACE><number_of_summarized_text><SPACE><URL>";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "Example:\n";
			deepEngMsg = deepEngMsg + "- Query: @desummary 3 http://www.example.com\n";
			deepEngMsg = deepEngMsg + "- Result: Text 1. Text 2. Text 3.\n";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "2. Spider: Got a good website and wanted to search for a keyword in it? Just use this feature as it can give you all the pages in the website containing your search keyword.";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "Format:\n";
			deepEngMsg = deepEngMsg + "- @defind<SPACE><keyword><SPACE><source_url>";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "Example:\n";
			deepEngMsg = deepEngMsg + "- Query: @defind fruit http://www.example.com\n";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "- Result:\n";
			deepEngMsg = deepEngMsg + "1. http://www.example.com/page1\n";
			deepEngMsg = deepEngMsg + "2. http://www.example.com/page2\n";
			deepEngMsg = deepEngMsg + "3. http://www.example.com/page3";


			// Send as a push message
			pushMessage(targetID, deepEngMsg);


		} else if (userTxt.equals("[others]")) {

			deepEngMsg = deepEngMsg + "[ MY CREATOR ]\n";
			deepEngMsg = deepEngMsg + "-----------------";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "I was created by Albertus Kelvin";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "[ MY TECHNICAL STUFFS ]\n";
			deepEngMsg = deepEngMsg + "----------------------------";

			deepEngMsg = deepEngMsg + "\n\n";
			deepEngMsg = deepEngMsg + "I was built using these following awesome technologies:\n\n";
			deepEngMsg = deepEngMsg + "1. Java with Spring as the framework\n\n";
			deepEngMsg = deepEngMsg + "2. PostgreSQL as the database\n\n";
			deepEngMsg = deepEngMsg + "3. Machine learning. I use it to determine whether a question is good enough to examine your understanding on the article.\n\n";
			deepEngMsg = deepEngMsg + "4. Distractor generator algorithm. I use it to generate the best answers choices based on the corresponding question. Their relation can be tricky for you and I think it is good for a real test.\n\n";
			deepEngMsg = deepEngMsg + "5. Content extractor. I use it to extract only the important and suitable contents of the article's URL.\n\n";
			deepEngMsg = deepEngMsg + "6. Text summarizer. It is a part of Classifier4J, a Java library for text classification. I use it to create a summary of your article.\n\n";
			deepEngMsg = deepEngMsg + "7. Web crawler (spider). I use it to find all pages in a website that contain your requested keyword.";


			// Send as a push message
			pushMessage(targetID, deepEngMsg);

		} else {

			// VERY RANDOM REQUEST
			if (userTxt.contains("@desummary")) {

				String[] special_req_splitted = userTxt.split("\\s+");

				// Get the URL's content
				String summURLContent = processLinkContent(special_req_splitted[2]);

				// Summarize the content
				String summary_result = getSummary(Integer.parseInt(special_req_splitted[1]), summURLContent);

				// Send the summary as a push message
				String txtAdd_summary_result = "";
				txtAdd_summary_result = txtAdd_summary_result + "[ YOUR ARTICLE'S SUMMARY ]\n";
				txtAdd_summary_result = txtAdd_summary_result + "-------------------------------";

				txtAdd_summary_result = txtAdd_summary_result + "\n\n";
				txtAdd_summary_result = txtAdd_summary_result + summary_result;

				pushMessage(targetID, txtAdd_summary_result);

			} else if (userTxt.contains("@defind")) {

				String[] special_req_splitted = userTxt.split("\\s+");

				List<String> listOfPageWithKeyword = getPagesWithKeyword(special_req_splitted[1], special_req_splitted[2]);

				// send a push message
				String txt_defind = "";
				txt_defind = txt_defind + "[ PAGES CONTAINING THE KEYWORD ]\n";
				txt_defind = txt_defind + "----------------------------------]";

				txt_defind = txt_defind + "\n\n";
				txt_defind = txt_defind + "Keyword: " + special_req_splitted[1];

				txt_defind = txt_defind + "\n\n";
				for (int defindIDX = 0; defindIDX < listOfPageWithKeyword.size(); defindIDX++) {

					txt_defind = txt_defind + String.valueOf(defindIDX + 1) + ". " + listOfPageWithKeyword.get(defindIDX) + "\n";

				}

				pushMessage(targetID, txt_defind);

			} else {

				// CHAT BOT DOES NOT RECOGNIZE IT
				botDoesNotUnderstandMessage();

			}

		}

	}


	// Method for retrieving all pages containing the specified keyword
	private List<String> getPagesWithKeyword(String userKeyword, String sourceURL) {

		Spider spider = new Spider();

		return spider.search(sourceURL, userKeyword);

	}


	// Method for building the summary of article
	private String getSummary(int numOfSentences, String articleContent) {

		TextSummariser smr = new TextSummariser(numOfSentences, articleContent);

		return smr.getSummary();

	}


	// Method for creating a button template
	private void buttonTemplate(String message, String[] label, String[] action, String title) {

		ButtonsTemplate buttonsTemplate;

		if (title.equals("Start the Quiz")) {

			buttonsTemplate = new ButtonsTemplate(null, null, message,
					Arrays.asList(new MessageAction(label[0], action[0])));

		} else if (title.contains("Hello") || title.equals("Your Quiz State") || title.equals("BotNotUnderstand")) {

			buttonsTemplate = new ButtonsTemplate(null, null, message,
					Arrays.asList(new MessageAction(label[0], action[0]),
							new MessageAction(label[1], action[1]),
							new MessageAction(label[2], action[2]),
							new MessageAction(label[3], action[3])
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