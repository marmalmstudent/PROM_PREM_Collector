/** Database.java
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
package servlet.core.interfaces;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;

import servlet.core._Message;
import servlet.core._Question;
import servlet.core._User;
import servlet.implementation.exceptions.DBWriteException;


/**
 * This interface contains the methods required by the core part of
 * this program to function. The purpose of this interface is to give
 * the freedom of choosing your own database along with the core part
 * of this program.
 * 
 * @author Marcus Malmquist
 *
 */
public interface Database
{
	/* Public */
	
	/**
	 * Adds a user contained in {@code obj} to the database.
	 * 
	 * @param obj The JSONObject that contains the user data.
	 * 
	 * @return A JSONObject with information about if the user
	 * 		was added.
	 */
	void addUser(int clinicID, String name, String password,
			String email, String salt) throws DBWriteException;
	
	/**
	 * Adds questionnaire answers contained in {@code obj} to the
	 * database.
	 * 
	 * @param obj The JSONObject that contains the questionnaire answer
	 * 		data.
	 * 
	 * @return A JSONObject with information about if the answers
	 * 		were added.
	 */
	boolean addQuestionnaireAnswers(int clinic_id,
			String identifier, List<String> question_ids,
			List<String> question_answers);
	
	/**
	 * Adds a clinic contained in {@code obj} to the database.
	 * 
	 * @param obj The JSONObject that contains the clinic data.
	 * 
	 * @return A JSONObject with information about if the clinic
	 * 		was added.
	 */
	boolean addClinic(String name);
	
	boolean addPatient(int clinic_id, String identifier);
	
	/**
	 * Retrieves the clinics from the database.
	 * 
	 * @param obj The JSONObject that contains the request.
	 * 
	 * @return A JSONObject that contains the clinics.
	 */
	Map<Integer, String> getClinics();
	
	/**
	 * Retrieves the user from the database.
	 * 
	 * @param obj The JSONObject that contains the request, including
	 * 		which user to retrieve.
	 * 
	 * @return A JSONObject that contains the user.
	 */
	_User _getUser(String username);
	
	/**
	 * Updates the password for the user contained in {@code obj}.
	 * 
	 * @param obj The JSONObject that contains the new password,
	 * 		new salt, old password and the username.
	 * 
	 * @return A JSONObject that contains the user with the new password.
	 */
	boolean setPassword(String newPass, String newSalt,
			String name);
	
	/**
	 * Retrieves the error messages from the database.
	 * 
	 * @param obj The JSONObject that contains the request.
	 * 
	 * @return A JSONObject that contains the error messages.
	 */
	Map<String, _Message> getErrorMessages();
	
	/**
	 * Retrieves the information messages from the database.
	 * 
	 * @param obj The JSONObject that contains the request.
	 * 
	 * @return A JSONObject that contains the information messages.
	 */
	Map<String, _Message> getInfoMessages();
	
	/**
	 * Retrieves the questionnaire questions from the database.
	 * 
	 * @param obj The JSONObject that contains the request.
	 * 
	 * @return A JSONObject that contains the questionnaire questions.
	 */
	Map<Integer, _Question> loadQuestions();
	
	/**
	 * Retrieves the dates that questionnaire answers were added to the
	 * database.
	 * 
	 * @param obj The JSONObject that contains the request.
	 * 
	 * @return A JSONObject that contains the dates.
	 */
	List<String> loadQResultDates(int clinic_id);
	
	/**
	 * Retrieves the questionnaire results from the database.
	 * 
	 * @param obj The JSONObject that contains the request, including
	 * 		the upper and lower limit as well as which questions to
	 * 		retrieve.
	 * 
	 * @return A JSONObject that contains the questionnaire results.
	 */
	List<Map<String, String>> loadQResults(
			List<String> qlist, int clinic_id,
			Date begin, Date end);
	
	/**
	 * Sends a registration request to an administrator.
	 * 
	 * @param obj The JSONObject that contains the request, including
	 * 		the name, clinic and email.
	 * 
	 * @return A JSONObject that contains the status of the request.
	 */
	boolean requestRegistration(
			String name, String email, String clinic);

	/**
	 * Sends a registration responds that contains the login details
	 * to the user that have been registered.
	 * 
	 * @param obj The JSONObject that contains the request, including
	 * 		the usename and password.
	 * 
	 * @return A JSONObject that contains the status of the request.
	 */
	public boolean respondRegistration(
			String username, String email, String password);

	@FunctionalInterface
	public interface DatabaseFunction
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
		public String dbfunc(JSONObject obj);
	}
	
	/* Protected */
	
	/* Private */
}
