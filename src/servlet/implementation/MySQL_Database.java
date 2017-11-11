/** MySQL_Database.java
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.json.simple.parser.JSONParser;

import servlet.core.PPCLogger;
import servlet.core._Message;
import servlet.core._Question;
import servlet.core._User;
import servlet.core.interfaces.Database;
import servlet.implementation.exceptions.DBReadException;
import servlet.implementation.exceptions.DBWriteException;
import common.Utilities;

/**
 * This class is an example of an implementation of
 * Database_Interface. This is done using a MySQL database and a
 * MySQL Connector/J to provide a MySQL interface to Java.
 * 
 * This class is designed to be thread safe and a singleton.
 * 
 * @author Marcus Malmquist
 *
 */
public class MySQL_Database implements Database
{
	/* Public */
	
	/**
	 * Retrieves the active instance of this class
	 * 
	 * @return The active instance of this class.
	 */
	public static synchronized MySQL_Database getDatabase()
	{
		if (database == null)
			database = new MySQL_Database();
		return database;
	}

	@Override
	public final Object clone()
			throws CloneNotSupportedException
	{
		throw new CloneNotSupportedException();
	}
	
	@Override
	public void addUser(int clinicID, String name, String password,
			String email, String salt) throws DBWriteException
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String qInsert = String.format(
				"INSERT INTO `users` (`clinic_id`, `name`, `password`, `email`, `registered`, `salt`, `update_password`) VALUES ('%d', '%s', '%s', '%s', '%s', '%s', '%d')",
				clinicID, name, password, email,
				sdf.format(new Date()), salt, 1);
		queryUpdate(qInsert);
	}

	@Override
	public boolean addQuestionnaireAnswers(int clinic_id,
			String identifier, List<String> question_ids,
			List<String> question_answers)
	{

		String resultInsert = String.format("INSERT INTO `questionnaire_answers` (`clinic_id`, `patient_identifier`, `date`, %s) VALUES ('%d', '%s', '%s', %s)",
				String.join(", ", question_ids), clinic_id, identifier,
				(new SimpleDateFormat("yyyy-MM-dd")).format(new Date()),
				String.join(", ", question_answers));
		try {
			queryUpdate(resultInsert);
			return true;
		} catch (DBWriteException dbw) {
			logger.log(DBWRITEERROR_STR, dbw);
		}
		return false;
	}

	@Override
	public boolean addClinic(String name)
	{
		String qInsert = String.format(
				"INSERT INTO `clinics` (`id`, `name`) VALUES (NULL, '%s')",
				name);
		try {
			queryUpdate(qInsert);
			return true;
		} catch (DBWriteException dbw) {
			logger.log(DBWRITEERROR_STR, dbw);
		}
		return false;
	}

	@Override
	public boolean addPatient(int clinic_id, String identifier) {
		String patientInsert = String.format(
				"INSERT INTO `patients` (`clinic_id`, `identifier`, `id`) VALUES ('%d', '%s', NULL)",
				clinic_id, identifier);
		try {
			if (!patientInDatabase(identifier))
				queryUpdate(patientInsert);
			return true;
		} catch (DBReadException dbr) {
			logger.log(DBREADERROR_STR, dbr);
		} catch (SQLException se) {
			logger.log(SQLERROR_STR, se);
		} catch (DBWriteException dbw) {
			logger.log(DBWRITEERROR_STR, dbw);
		}
		return false;
	}
	
	@Override
	public Map<Integer, String> getClinics()
	{
		try (Connection conn = dataSource.getConnection()) {
			Statement s = conn.createStatement();
			ResultSet rs = query(s, "SELECT `id`, `name` FROM `clinics`");
			
			Map<Integer, String> cmap = new TreeMap<Integer, String>();
			while (rs.next()) {
				cmap.put(rs.getInt("id"), rs.getString("name"));
			}
			return cmap;
		} catch (DBReadException dbr) {
			logger.log(DBREADERROR_STR, dbr);
		} catch (SQLException e) {
			logger.log(SQLERROR_STR, e);
		}
		return null;
	}

	@Override
	public _User _getUser(String username)
	{
		try (Connection conn = dataSource.getConnection()) {
			Statement s = conn.createStatement();
			ResultSet rs = query(s, "SELECT `clinic_id`, `name`, `password`, `email`, `salt`, `update_password` FROM `users`");
			
			_User _user = null;
			while (rs.next()) {
				if (rs.getString("name").equals(username)) {
					_user = new _User();
					_user.clinic_id = rs.getInt("clinic_id");
					_user.name = rs.getString("name");
					_user.password = rs.getString("password");
					_user.email = rs.getString("email");
					_user.salt = rs.getString("salt");
					_user.update_password = rs.getInt("update_password") > 0;
					break;
				}
			}
			return _user;
		} catch (DBReadException dbr) {
			logger.log(DBREADERROR_STR, dbr);
		} catch (SQLException se) {
			logger.log(SQLERROR_STR, se);
		}
		return null;
	}

	@Override
	public boolean setPassword(String newPass, String newSalt,
			String name)
	{
		String qInsert = String.format(
				"UPDATE `users` SET `password`='%s',`salt`='%s',`update_password`=%d WHERE `users`.`name` = '%s'",
				newPass, newSalt, 0, name);
		try {
			queryUpdate(qInsert);
			return true;
		} catch (DBWriteException dbw) {
			logger.log(DBWRITEERROR_STR, dbw);
		}
		return false;
	}

	@Override
	public Map<String, _Message> getErrorMessages()
	{
		return getMessages("error_messages");
	}

	@Override
	public Map<String, _Message> getInfoMessages()
	{
		return getMessages("info_messages");
	}
	
	@Override
	public Map<Integer, _Question> loadQuestions()
	{
		try (Connection conn = dataSource.getConnection()) {
			ResultSet rs = query(conn.createStatement(),
					"SELECT * FROM `questionnaire`");
			
			Map<Integer, _Question> qmap = new HashMap<Integer, _Question>();
			while (rs.next()) {
				_Question _question = new _Question();
				for (int i = 0; ; ++i) {
					try {
						String entry = rs.getString(String.format("option%d", i));
						if (entry == null || (entry = entry.trim()).isEmpty())
							break;
						_question.options.add(entry);
					} catch (SQLException e) {
						/* no more options */
						break;
					}
				}
				int id = rs.getInt("id");
				_question.type = rs.getString("type");
				_question.id = id;
				_question.question = rs.getString("question");
				_question.description = rs.getString("description");
				_question.optional = rs.getInt("optional") > 0;
				_question.max_val = rs.getInt("max_val");
				_question.min_val = rs.getInt("min_val");

				qmap.put(id, _question);
			}
			return qmap;
		} catch (DBReadException dbr) {
			logger.log(DBREADERROR_STR, dbr);
		} catch (SQLException e) {
			logger.log(SQLERROR_STR, e);
		}
		return null;
	}

	@Override
	public List<String> loadQResultDates(int clinic_id)
	{
		try (Connection conn = dataSource.getConnection()) {
			Statement s = conn.createStatement();
			ResultSet rs = query(s, String.format(
					"SELECT `date` FROM `questionnaire_answers` WHERE `clinic_id` = %d",
					clinic_id));
			
			List<String> dlist = new ArrayList<String>();
			while (rs.next()) {
				dlist.add(rs.getString("date"));
			}
			return dlist;
		} catch (DBReadException dbr) {
			logger.log(DBREADERROR_STR, dbr);
		} catch (SQLException e) {
			logger.log(SQLERROR_STR, e);
		}
		return null;
	}
	
	@Override
	public List<Map<String, String>> loadQResults(
			List<String> qlist, int clinic_id, Date begin, Date end)
	{
		try (Connection conn = dataSource.getConnection()) {
			Statement s = conn.createStatement();
			
			List<String> lstr = new ArrayList<String>();
			for (String q : qlist)
				lstr.add("`" + q + "`");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			
			ResultSet rs = query(s, String.format(
					"SELECT %s FROM `questionnaire_answers` WHERE `clinic_id` = %d AND `date` BETWEEN '%s' AND '%s'",
					String.join(", ", lstr), clinic_id,
					sdf.format(begin), sdf.format(end)));

			List<Map<String, String>> rlist = new ArrayList<Map<String, String>>();
			while (rs.next()) {
				Map<String, String> amap = new HashMap<String, String>();
				for (String q : qlist)
					amap.put(q, rs.getString(q));
				rlist.add(amap);
			}
			return rlist;
		} catch (DBReadException dbr) {
			logger.log(DBREADERROR_STR, dbr);
		} catch (SQLException e) {
			logger.log(SQLERROR_STR, e);
		}
		return null;
	}
	
	@Override
	public boolean requestRegistration(
			String name, String email, String clinic)
	{
		String emailSubject = "PROM_PREM: Registration request";
		String emailDescription = "Registration reguest from";
		String emailSignature = "This message was sent from the PROM/PREM Collector";
		String emailBody = String.format(
				("%s:<br><br> %s: %s<br>%s: %s<br>%s: %s<br><br> %s"),
				emailDescription, "Name", name, "E-mail",
				email, "Clinic", clinic, emailSignature);
		return send(config.adminEmail, emailSubject, emailBody, "text/html");
	}

	@Override
	public boolean respondRegistration(
			String username, String email, String password)
	{
		String emailSubject = "PROM_PREM: Registration response";
		String emailDescription = "You have been registered at the PROM/PREM Collector. "
				+ "You will find your login details below. When you first log in you will"
				+ "be asked to update your password.";
		String emailSignature = "This message was sent from the PROM/PREM Collector";
		String emailBody = String.format(
				("%s<br><br> %s: %s<br>%s: %s<br><br> %s"),
				emailDescription, "Username", username,
				"Password", password, emailSignature);
		return send(email, emailSubject, emailBody, "text/html");
	}
	
	/* Protected */
	
	/* Package */
	
	/* Private */

	private static final String SQLERROR_STR;
	private static final String DBREADERROR_STR;
	private static final String DBWRITEERROR_STR;
	private static MySQL_Database database;
	private static JSONParser parser;
	private static PPCLogger logger;
	
	/**
	 * Handles connection with the database.
	 */
	private DataSource dataSource;
	
	/**
	 * Configuration data for sending an email from the servlet's email
	 * to the admin's email.
	 */
	private EmailConfig config;
	
	static
	{
		logger = PPCLogger.getLogger();
		parser = new JSONParser();
		SQLERROR_STR = "Error opening connection to database "
				+ "or while parsing SQL ResultSet";
		DBREADERROR_STR = "Database read error";
		DBWRITEERROR_STR = "Database write error";
	}
	
	/**
	 * Initializes variables and loads the database configuration.
	 * This class is a singleton and should only be instantiated once.
	 */
	private MySQL_Database()
	{
		try {
			config = new EmailConfig();
			Context initContext = new InitialContext();
			Context envContext = (Context) initContext.lookup("java:comp/env");
			dataSource = (DataSource) envContext.lookup("jdbc/prom_prem_db");
		} catch (NamingException e) {
			logger.log("FATAL: Could not load database configuration", e);
			System.exit(1);
		} catch (IOException e) {
			logger.log("FATAL: Could not load email configuration", e);
			System.exit(1);
		}
	}
	
	/**
	 * Checks if a patient with the {@code identifier} exists in the database.
	 * 
	 * @param identifier The identifier of the patient.
	 * 
	 * @return {@code true} if the patient exists in the database,
	 * 		{@code false} if not.
	 */
	private boolean patientInDatabase(String identifier)
			throws SQLException, DBReadException
	{
		Connection conn = dataSource.getConnection();
		Statement s = conn.createStatement();
		ResultSet rs = query(s, "SELECT `identifier` FROM `patients`");
		
		while (rs.next())
			if (rs.getString("identifier").equals(identifier))
				return true;
		return false;
	}
	
	/**
	 * Query the database to update an entry i.e. modify an existing
	 * database entry.
	 * 
	 * @param message The command (specified by the SQL language)
	 * 		to send to the database.
	 * 
	 * @return QUERY_SUCCESS on successful query, ERROR on failure.
	 * 
	 * @throws DBWriteException If an update error occurs.
	 */
	private void queryUpdate(String message) throws DBWriteException
	{
		try (Connection c = dataSource.getConnection()) {
			c.createStatement().executeUpdate(message);
		} catch (SQLException se) {
			throw new DBWriteException(String.format(
					"Database could not process request: '%s'. Check your arguments.",
					message));
		}
	}
	
	/**
	 * Query the database, typically for data (i.e. request data from
	 * the database).
	 * 
	 * @param s The statement that executes the query. The statement
	 * 		can be acquired by calling
	 * 		Connection c = DriverManager.getConnection(...)
	 * 		Statement s = c.createStatement()
	 * @param message The command (specified by the SQL language)
	 * 		to send to the database.
	 * 
	 * @return The the ResultSet from the database.
	 * 
	 * @throws DBReadException If the statement is not initialized or a
	 * 		query error occurs.
	 */
	private ResultSet query(Statement s, String message) throws DBReadException
	{
		try {
			return s.executeQuery(message);
		} catch (SQLException se) {
			throw new DBReadException(String.format(
					"Database could not process request: '%s'. Check your arguments.",
					message));
		}
	}

	/**
	 * Retrieves messages from the database and places them in
	 * {@code retobj}
	 * 
	 * @param tableName The name of the (message) table to retrieve
	 * 		messages from.
	 * 
	 * @param retobj The map to put the messages in.
	 * 
	 * @return true if the messages were put in the map.
	 */
	private Map<String, _Message> getMessages(String tableName)
	{
		try (Connection conn = dataSource.getConnection()) {
			ResultSet rs = query(conn.createStatement(), String.format(
					"SELECT `code`, `name`, `locale`, `message` FROM `%s`",
					tableName));
			
			Map<String, _Message> _retobj = new HashMap<String, _Message>();
			while (rs.next()) {
				_Message _msg = new _Message();
				_msg.name = rs.getString("name");
				_msg.code = rs.getString("code");
				_msg.addMessage(rs.getString("locale"), rs.getString("message"));
				
				_retobj.put(_msg.name, _msg);
			}
			return _retobj;
		} catch (DBReadException dbr) {
			logger.log(DBREADERROR_STR, dbr);
		} catch (SQLException e) {
			logger.log(SQLERROR_STR, e);
		}
		return null;
	}
	
	/**
	 * Sends an email from the servlet's email account.
	 * 
	 * @param recipient The email address of to send the email to.
	 * @param emailSubject The subject of the email.
	 * @param emailBody The body/contents of the email.
	 * @param bodyFormat The format of the body. This could for
	 * 		example be 'text', 'html', 'text/html' etc.
	 */
	private boolean send(String recipient, String emailSubject,
			String emailBody, String bodyFormat)
	{
		/* generate session and message instances */
		Session getMailSession = Session.getDefaultInstance(
				config.mailConfig, null);
		MimeMessage generateMailMessage = new MimeMessage(getMailSession);
		try {
			/* create email */
			generateMailMessage.addRecipient(Message.RecipientType.TO,
					new InternetAddress(recipient));
			generateMailMessage.setSubject(emailSubject);
			generateMailMessage.setContent(emailBody, bodyFormat);
			
			/* login to server email account and send email. */
			Transport transport = getMailSession.getTransport();
			transport.connect(config.serverEmail, config.serverPassword);
			transport.sendMessage(generateMailMessage,
					generateMailMessage.getAllRecipients());
			transport.close();
		} catch (MessagingException me) {
			logger.log("Could not send email", me);
			return false;
		}
		return true;
	}
	
	/**
	 * This class contains the configuration data for sending emails.
	 * 
	 * @author Marcus Malmquist
	 *
	 */
	private static final class EmailConfig
	{
		static final String CONFIG_FILE =
				"servlet/implementation/email_settings.txt";
		static final String ACCOUNT_FILE =
				"servlet/implementation/email_accounts.ini";
		Properties mailConfig;
		
		// server mailing account
		String serverEmail, serverPassword, adminEmail;
		
		EmailConfig() throws IOException
		{
			mailConfig = new Properties();
			refreshConfig();
		}
		
		/**
		 * reloads the javax.mail config properties as well as
		 * the email account config.
		 */
		synchronized void refreshConfig() throws IOException
		{
			loadConfig(CONFIG_FILE);
			loadEmailAccounts(ACCOUNT_FILE);
		}
		
		/**
		 * Loads the javax.mail config properties contained in the
		 * supplied config file.
		 * 
		 * @param filePath The file while the javax.mail config
		 * 		properties are located.
		 * 
		 * @return True if the file was loaded. False if an error
		 * 		occurred.
		 */
		synchronized void loadConfig(String filePath) throws IOException
		{
			if (!mailConfig.isEmpty())
				mailConfig.clear();
			mailConfig.load(Utilities.getResourceStream(getClass(), filePath));
		}
		
		/**
		 * Loads the registration program's email account information
		 * as well as the email address of the administrator who will
		 * receive registration requests.
		 * 
		 * @param filePath The file that contains the email account
		 * 		information.
		 * 
		 * @return True if the file was loaded. False if an error
		 * 		occurred.
		 */
		synchronized void loadEmailAccounts(String filePath) throws IOException
		{
			Properties props = new Properties();
			props.load(Utilities.getResourceStream(getClass(), filePath));
			adminEmail = props.getProperty("admin_email");
			serverEmail = props.getProperty("server_email");
			serverPassword = props.getProperty("server_password");
			props.clear();
		}
	}
}