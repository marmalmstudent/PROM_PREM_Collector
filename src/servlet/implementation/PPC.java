/** JSONRead.java
 * 
 * Copyright 2017 Marcus Malmquist
 * 
 * This file is part of PROM_PREM_Collector.
 * 
 * PROM_PREM_Collector is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * PROM_PREM_Collector is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with PROM_PREM_Collector.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package servlet.implementation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import common.implementation.Constants;
import servlet.core.Crypto;
import servlet.core.MailMan;
import servlet.core.PPCLogger;
import servlet.core.PasswordHandle;
import servlet.core.ServletConst;
import servlet.core.User;
import servlet.core.UserManager;
import servlet.core._Message;
import servlet.core._Question;
import servlet.core.interfaces.Database;
import servlet.core.interfaces.Encryption;
import servlet.core.interfaces.Implementations;

/**
 * This class handles redirecting a request from the applet to the
 * appropriate method in the servlet.
 * 
 * @author Marcus Malmquist
 *
 */
public class PPC
{
	
	/**
	 * Parses the {@code message} and redirects the request to the request
	 * to the appropriate method.
	 * 
	 * @param message The request from the applet.
	 * 
	 * @return The response from the servlet.
	 */
	public String handleRequest(String message, String remoteAddr, String hostAddr)
	{
		try {
			JSONMapData obj = new JSONMapData(getJSONObject(message));
			return getDBMethod(obj.jmap.get("command")).netfunc(obj.jobj, remoteAddr, hostAddr);
		} catch (Exception e) {
			logger.log("Unknown request", e);
			return null;
		}
	}
	
	PPC()
	{
		dbm = new HashMap<String, NetworkFunction>();
		db = MySQL_Database.getDatabase();
		um = UserManager.getUserManager();
		crypto = new SHA_Encryption();

		dbm.put(ServletConst.CMD_ADD_USER, this::addUser);
		dbm.put(Constants.CMD_ADD_QANS, this::addQuestionnaireAnswers);
		dbm.put(ServletConst.CMD_ADD_CLINIC, this::addClinic);
		dbm.put(Constants.CMD_GET_CLINICS, this::getClinics);
		dbm.put(Constants.CMD_GET_USER, this::getUser);
		dbm.put(Constants.CMD_SET_PASSWORD, this::setPassword);
		dbm.put(Constants.CMD_GET_ERR_MSG, this::getErrorMessages);
		dbm.put(Constants.CMD_GET_INFO_MSG, this::getInfoMessages);
		dbm.put(Constants.CMD_LOAD_Q, this::loadQuestions);
		dbm.put(Constants.CMD_LOAD_QR_DATE, this::loadQResultDates);
		dbm.put(Constants.CMD_LOAD_QR, this::loadQResults);
		dbm.put(Constants.CMD_REQ_REGISTR, this::requestRegistration);
		dbm.put(ServletConst.CMD_RSP_REGISTR, this::respondRegistration);
		dbm.put(Constants.CMD_REQ_LOGIN, this::requestLogin);
		dbm.put(Constants.CMD_REQ_LOGOUT, this::requestLogout);
	}
	
	void terminate()
	{
		um.terminate();
	}
	
	private static PPCLogger logger;
	private static JSONParser parser;
	private Map<String, NetworkFunction> dbm;
	private Database db;
	private UserManager um;
	private Encryption crypto;
	
	static {
		logger = PPCLogger.getLogger();
		parser = new JSONParser();
	}


	/**
	 * Attempts to parse {@code str} into a {@code JSONObject}.
	 * 
	 * @param str The string to be converted into a {@code JSONObject}.
	 * 
	 * @return The {@code JSONObject} representation of {@code str}, or
	 * 		{@code null} if {@code str} does not represent a
	 * 		{@code JSONObject}.
	 */
	private static JSONObject getJSONObject(String str)
	{
		try {
			return (JSONObject) parser.parse(str);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Attempts to parse {@code str} into a {@code JSONArray}.
	 * 
	 * @param str The string to be converted into a {@code JSONArray}.
	 * 
	 * @return The {@code JSONArray} representation of {@code str}, or
	 * 		{@code null} if {@code str} does not represent a
	 *  	{@code JSONArray}.
	 */
	private static JSONArray getJSONArray(String str)
	{
		try {
			return (JSONArray) parser.parse(str);
		} catch (Exception e) {
			return null;
		}
	}
	
	private static Date getDate(String date)
	{
		try {
			return (new SimpleDateFormat("yyyy-MM-dd")).parse(date);
		} catch (java.text.ParseException e) {
			return new Date(0L);
		}
	}
	
	/**
	 * Finds the Method Reference associated with the {@code command}
	 * 
	 * @param command The command/method that is associated with a servlet
	 * 		method that shuld handle the request.
	 * 
	 * @return A reference to the servlet method that should handle the
	 * 		request.
	 * 
	 * @throws NullPointerException If no method exists that can handle
	 * 		the request.
	 */
	private NetworkFunction getDBMethod(String command)
	{
		return dbm.get(command);
	}
	
	private String addUser(JSONObject obj, String remoteAddr, String hostAddr) throws Exception
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", ServletConst.CMD_ADD_USER);
		
		boolean success = remoteAddr.equals(hostAddr) && db.addUser(
				Integer.parseInt(in.jmap.get("clinic_id")),
				in.jmap.get("name"), in.jmap.get("password"),
				in.jmap.get("email"), in.jmap.get("salt"));
		if (success) {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_SUCCESS);
		} else {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
		}
		return out.jobj.toString();
	}
	
	private String addQuestionnaireAnswers(
			JSONObject obj, String remoteAddr, String hostAddr)
					throws NullPointerException
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_ADD_QANS);
		
		String _details = in.jmap.get("details");
		String _patient = in.jmap.get("patient");
		String _questions = in.jmap.get("questions");

		JSONMapData inpl, patient;
		try {
			inpl = new JSONMapData(getJSONObject(Crypto.decrypt(_details)));
			patient = new JSONMapData(getJSONObject(Crypto.decrypt(_patient)));
		} catch (NumberFormatException e) {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
			return out.jobj.toString();
		}
		
		String _uid = inpl.jmap.get("uid");
		long uid;
		try {
			uid = Long.parseLong(_uid);
		} catch (NumberFormatException e) {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
			return out.jobj.toString();
		}
		
		int clinic_id;
		try {
			clinic_id = db.getUser(um.nameForUID(uid)).clinic_id;
		} catch (NullPointerException e) {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
			return out.jobj.toString();
		}

		String forename = patient.jmap.get("forename");
		String surname = patient.jmap.get("surname");
		String personal_id = patient.jmap.get("personal_id");
		
		String identifier;
		try {
			Encryption encrypt = Implementations.Encryption();
			identifier = encrypt.encryptMessage(forename, personal_id, surname);
		} catch (NullPointerException e) {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
			return out.jobj.toString();
		}

		List<String> question_answers = new ArrayList<String>();
		JSONArrData m = new JSONArrData(getJSONArray(_questions));
		for (String e : m.jlist)
			question_answers.add(QDBFormat.getDBFormat(new JSONMapData(getJSONObject(e))));
		
		if (db.addPatient(clinic_id, identifier)
				&& db.addQuestionnaireAnswers(clinic_id, identifier, question_answers)) {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_SUCCESS);
		} else {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
		}
		return out.jobj.toString();
	}
	
	private String addClinic(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", ServletConst.CMD_ADD_CLINIC);
		
		if (remoteAddr.equals(hostAddr) && db.addClinic(in.jmap.get("name"))) {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_SUCCESS);
		} else {
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
		}
		return out.jobj.toString();
	}
	
	private String getClinics(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_GET_CLINICS);
			
		Map<Integer, String> _clinics = db.getClinics();
		JSONMapData clinics = new JSONMapData(null);
		if (remoteAddr.equals(hostAddr))
			for (Entry<Integer, String> e : _clinics.entrySet())
				clinics.jmap.put(Integer.toString(e.getKey()), e.getValue());
		out.jmap.put("clinics", clinics.jobj.toString());
		return out.jobj.toString();
	}
	
	private String getUser(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_GET_USER);

		User _user = db.getUser(in.jmap.get("name"));
		JSONMapData user = new JSONMapData(null);
		if (remoteAddr.equals(hostAddr) && _user != null) {
			user.jmap.put("clinic_id", Integer.toString(_user.clinic_id));
			user.jmap.put("name", _user.name);
			user.jmap.put("password", _user.password);
			user.jmap.put("email", _user.email);
			user.jmap.put("salt", _user.salt);
			user.jmap.put("update_password", _user.update_password ? "1" : "0");
			out.jmap.put("user", user.jobj.toString());
		} else {
			out.jmap.put("user", (new JSONObject()).toString());
		}
		return out.jobj.toString();
	}
	
	private String setPassword(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);

		JSONMapData inpl = new JSONMapData(getJSONObject(Crypto.decrypt(in.jmap.get("details"))));
		long uid = Long.parseLong(inpl.jmap.get("uid"));
		String name = um.nameForUID(uid);
		String oldPass = inpl.jmap.get("old_password");
		String newPass1 = inpl.jmap.get("new_password1");
		String newPass2 = inpl.jmap.get("new_password2");

		Encryption hash = Implementations.Encryption();
		String newSalt = hash.getNewSalt();
		
		User user = db.getUser(name);
		int status = PasswordHandle.newPassError(user, oldPass, newPass1, newPass2);
		if (status == Constants.SUCCESS) {
			db.setPassword(name, user.hashWithSalt(oldPass),
					hash.hashString(newPass1, newSalt), newSalt);
		}
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_SET_PASSWORD);
		out.jmap.put(Constants.SETPASS_REPONSE,
				Integer.toString(status));
		return out.jobj.toString();
	}
	
	private String getErrorMessages(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_GET_ERR_MSG);
		out.jmap.put("messages", getMessages(db.getErrorMessages()).toString());
		return out.jobj.toString();
	}
	
	private String getInfoMessages(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_GET_INFO_MSG);
		out.jmap.put("messages", getMessages(db.getInfoMessages()).toString());
		return out.jobj.toString();
	}
	
	private String loadQuestions(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_LOAD_Q);
		
		Map<Integer, _Question> questions = db.loadQuestions();
		JSONMapData _questions = new JSONMapData(null);
		for (Entry<Integer, _Question> _e : questions.entrySet()) {
			_Question _q = _e.getValue();
			JSONMapData _question = new JSONMapData(null);
			int i = 0;
			for (String str : _q.options)
				_question.jmap.put(String.format("option%d", i++), str);
			_question.jmap.put("type", _q.type);
			_question.jmap.put("id", Integer.toString(_q.id));
			_question.jmap.put("question", _q.question);
			_question.jmap.put("description", _q.description);
			_question.jmap.put("optional", _q.optional ? "1" : "0");
			_question.jmap.put("max_val", Integer.toString(_q.max_val));
			_question.jmap.put("min_val", Integer.toString(_q.min_val));
			
			_questions.jmap.put(Integer.toString(_e.getKey()),
					_question.jobj.toString());
		}
		out.jmap.put("questions", _questions.jobj.toString());
		return out.jobj.toString();
	}
	
	private String loadQResultDates(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_LOAD_QR_DATE);

		JSONMapData inpl = new JSONMapData(getJSONObject(Crypto.decrypt(in.jmap.get("details"))));
		long uid = Long.parseLong(inpl.jmap.get("uid"));
		User user = db.getUser(um.nameForUID(uid));
		if (user == null) {
			out.jmap.put("dates", new JSONArray().toString());
			return out.jobj.toString();
		}
		List<String> dlist = db.loadQResultDates(user.clinic_id);

		JSONArrData dates = new JSONArrData(null);
		for (String str : dlist)
			dates.jlist.add(str);
		out.jmap.put("dates", dates.jarr.toString());
		return out.jobj.toString();
	}
	
	private String loadQResults(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_LOAD_QR);

		JSONMapData inpl = new JSONMapData(getJSONObject(Crypto.decrypt(in.jmap.get("details"))));
		long uid = Long.parseLong(inpl.jmap.get("uid"));
		User _user = db.getUser(um.nameForUID(uid));
		JSONArrData questions = new JSONArrData(getJSONArray(in.jmap.get("questions")));

		List<Map<String, String>> _results = db.loadQResults(
				_user.clinic_id, questions.jlist,
				getDate(in.jmap.get("begin")),
				getDate(in.jmap.get("end")));

		JSONArrData results = new JSONArrData(null);
		for (Map<String, String> m : _results) {
			JSONMapData answers = new JSONMapData(null);
			for (Entry<String, String> e : m.entrySet())
				answers.jmap.put(String.format("%d",
						Integer.parseInt(e.getKey().substring("question".length()))),
						QDBFormat.getQFormat(e.getValue()));
			results.jlist.add(answers.jobj.toString());
		}
		out.jmap.put("results", results.jarr.toString());
		return out.jobj.toString();
	}

	/**
	 * Sends a registration request to an administrator.
	 * 
	 * @param obj The JSONObject that contains the request, including
	 * 		the name, clinic and email.
	 * 
	 * @return A JSONObject that contains the status of the request.
	 */
	private String requestRegistration(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_REQ_REGISTR);

		JSONMapData inpl = new JSONMapData(getJSONObject(Crypto.decrypt(in.jmap.get("details"))));
		
		if (MailMan.sendRegReq(inpl.jmap.get("name"), inpl.jmap.get("email"), inpl.jmap.get("clinic")))
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_SUCCESS);
		else
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
		return out.jobj.toString();
	}

	/**
	 * Sends a registration responds that contains the login details
	 * to the user that have been registered.
	 * 
	 * @param obj The JSONObject that contains the request, including
	 * 		the usename and password.
	 * 
	 * @return A JSONObject that contains the status of the request.
	 */
	private String respondRegistration(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", ServletConst.CMD_RSP_REGISTR);
		
		if (MailMan.sendRegResp(in.jmap.get("username"),
				in.jmap.get("password"), in.jmap.get("email")))
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_SUCCESS);
		else
			out.jmap.put(Constants.INSERT_RESULT, Constants.INSERT_FAIL);
		return out.jobj.toString();
	}

	/**
	 * Requests to log in.
	 * 
	 * @param obj The JSONObject that contains the request, including the
	 * 		username.
	 * 
	 * @return A JSONObject that contains the status of the request.
	 */
	private String requestLogin(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_REQ_LOGIN);

		JSONMapData inpl = new JSONMapData(getJSONObject(Crypto.decrypt(in.jmap.get("details"))));
		User user = db.getUser(inpl.jmap.get("name"));
		if (user == null
				|| !user.passwordMatch(inpl.jmap.get("password"))) {
			out.jmap.put("update_password", "0");
			out.jmap.put(Constants.LOGIN_REPONSE, Constants.INVALID_DETAILS_STR);
			return out.jobj.toString();
		}

		String hash = crypto.encryptMessage(
				Long.toHexString((new Date()).getTime()),
				user.name, crypto.getNewSalt());
		long uid = Long.parseLong(hash.substring(0, 2*Long.BYTES-1), 2*Long.BYTES);
		
		int response = um.addUser(user.name, uid);
		out.jmap.put(Constants.LOGIN_REPONSE, Integer.toString(response));
		out.jmap.put("update_password", user.update_password ? "1" : "0");
		if (response == Constants.SUCCESS) {
			out.jmap.put(Constants.LOGIN_UID, Long.toString(uid));
		}
		out.jmap.put("remote_ip", remoteAddr);
		out.jmap.put("host_ip", hostAddr);
		return out.jobj.toString();
	}

	/**
	 * Requests to log out.
	 * 
	 * @param obj The JSONObject that contains the request, including the
	 * 		username.
	 * 
	 * @return A JSONObject that contains the status of the request.
	 */
	private String requestLogout(JSONObject obj, String remoteAddr, String hostAddr)
	{
		JSONMapData in = new JSONMapData(obj);
		JSONMapData out = new JSONMapData(null);
		out.jmap.put("command", Constants.CMD_REQ_LOGOUT);

		JSONMapData inpl = new JSONMapData(getJSONObject(Crypto.decrypt(in.jmap.get("details"))));
		long uid = Long.parseLong(inpl.jmap.get("uid"));
		String response = um.delUser(um.nameForUID(uid)) ? Constants.SUCCESS_STR : Constants.ERROR_STR;
		out.jmap.put(Constants.LOGOUT_REPONSE, response);
		return out.jobj.toString();
	}
	
	// --------------------------------
	
	private JSONObject getMessages(Map<String, _Message> _msg)
	{
		JSONMapData out = new JSONMapData(null);
		for (Entry<String, _Message> e : _msg.entrySet()) {
			_Message _message = e.getValue();
			
			JSONMapData msg = new JSONMapData(null);
			for (Entry<String, String> _e : _message.msg.entrySet()) {
				msg.jmap.put(_e.getKey(), _e.getValue());
			}

			JSONMapData message = new JSONMapData(null);
			message.jmap.put("name", _message.name);
			message.jmap.put("code", _message.code);
			message.jmap.put("message", msg.jobj.toString());

			out.jmap.put(e.getKey(), message.jobj.toString());
		}
		return out.jobj;
	}
	
	private static class JSONMapData
	{
		JSONObject jobj;
		Map<String, String> jmap;
		
		@SuppressWarnings("unchecked")
		JSONMapData(JSONObject jobj)
		{
			this.jobj = jobj != null ? jobj : new JSONObject();
			this.jmap = (Map<String, String>) this.jobj;
		}
	}
	
	private static class JSONArrData
	{
		JSONArray jarr;
		List<String> jlist;
		
		@SuppressWarnings("unchecked")
		JSONArrData(JSONArray jarr)
		{
			this.jarr = jarr != null ? jarr : new JSONArray();
			this.jlist = (List<String>) this.jarr;
		}
	}

	@FunctionalInterface
	private interface NetworkFunction
	{
		/**
		 * A method that processes the request contained in {@code obj}
		 * and returns the answer as a string.
		 * 
		 * @param obj The JSONObject that contains the request along with
		 * 		required data to process the request.
		 * 
		 * @return The String representation of the JSONObject that
		 * 		contains the answer.
		 */
		public String netfunc(JSONObject obj, String remoteAddr, String hostAddr) throws Exception;
	}

	private static class QDBFormat
	{
		static String getDBFormat(JSONMapData fc)
		{
			String val = null;
			if ((val = fc.jmap.get("SingleOption")) != null) {
				
				return String.format("'option%d'", Integer.parseInt(val));
				
			} else if ((val = fc.jmap.get("MultipleOption")) != null) {
				
				JSONArrData options = new JSONArrData(getJSONArray(val));
				List<String> lstr = new ArrayList<>();
				for (String str : options.jlist)
					lstr.add(String.format("option%d", Integer.parseInt(str)));
				return String.format("[%s]", String.join(",", lstr));
				
			} else if ((val = fc.jmap.get("Slider")) != null) {
				
				return String.format("'slider%d'", Integer.parseInt(val));
				
			} else if ((val = fc.jmap.get("Area")) != null) {
				
				return String.format("'%s'", val);
				
			} else
				
				return "''";
		}
		
		static String getQFormat(String dbEntry)
		{
			JSONMapData fmt = new JSONMapData(null);
			if (dbEntry == null || dbEntry.trim().isEmpty())
				return fmt.toString();
			
			if (dbEntry.startsWith("option")) {
				fmt.jmap.put("SingleOption", String.format("%d",
						Integer.parseInt(dbEntry.substring("option".length()))));
			} else if (dbEntry.startsWith("slider")) {
				fmt.jmap.put("Slider", String.format("%d",
						Integer.parseInt(dbEntry.substring("slider".length()))));
			} else if (dbEntry.startsWith("[") && dbEntry.endsWith("]")) {
                /* multiple answers */
				List<String> entries = Arrays.asList(dbEntry.split(","));
				JSONArrData options = new JSONArrData(null);
				if (entries.get(0).startsWith("option")) {
                    /* multiple option */
					for (String str : entries)
						options.jlist.add(String.format("%d",
								Integer.parseInt(str.substring("option".length()))));
					fmt.jmap.put("MultipleOption", options.jarr.toString());
				}
			} else {
                /* must be plain text entry */
				return dbEntry;
			}
			return fmt.jobj.toString();
		}
	}
}
