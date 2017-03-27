package com.sai.deepenglish.Master;

import java.io.IOException;
import java.net.URL;
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
						}

					} else {

						// Category 3: Answer
						processText_ANSWER(idTarget);

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

			// Pass the sentence having true label to the Distractor Generator
			if (out.equals("1")) {
				//generateDistractor(listOfActualNewDataToBeTested.get(idx), listOfBlankNewDataToBeTested.get(idx));
				listOfEligibleNewData_ACTUAL.add(listOfActualNewDataToBeTested.get(idx));
				listOfEligibleNewData_BLANK.add(listOfBlankNewDataToBeTested.get(idx));
			}

		}

		// HANDLER for the case when the size of the true label is 0?

		generateDistractor(listOfEligibleNewData_ACTUAL, listOfEligibleNewData_BLANK);


		//pushMessage(targetID, "Prediction: " + out + "; " + "Certainty: " + pc.getPredictionCertainty());


	}


	// Category 3
	private void processText_ANSWER(String targetID) {



	}


	private void generateDistractor(List<String> listOfEligibleActualNewData, List<String> listOfEligibleBlankNewData) {

		String pathToAllEnglishWords = "data/english_dict/words.txt";

		DistractorGenerator dg = new DistractorGenerator(pathToAllEnglishWords, listOfEligibleActualNewData, listOfEligibleBlankNewData);

		List<String> listOfEnglishWords = dg.retrieveAllEnglishWords();

		dg.setListOfEnglishWords(listOfEnglishWords);
		dg.generateDistractor();

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

	// Method for sending message when there a user adds this bot as friend
	private void greetingMessage() {

		getUserProfile(payload.events[0].source.userId);

		String greetingMsg = "Hi " + displayName + "! I'm DeepEnglish and I'd like to help you in improving your English reading comprehension skill. Let's learn together!" + payload.events[0].replyToken;
		String action = "Read the rules";
		String title = "Welcome";

		buttonTemplate(greetingMsg, action, action, title);

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
	private void buttonTemplate(String message, String label, String action, String title) {

		ButtonsTemplate buttonsTemplate = new ButtonsTemplate(null, null, message,
				Collections.singletonList(new MessageAction(label, action)));

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