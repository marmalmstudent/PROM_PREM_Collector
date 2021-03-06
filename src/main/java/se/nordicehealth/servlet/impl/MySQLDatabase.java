package se.nordicehealth.servlet.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import se.nordicehealth.servlet.core.PPCDatabase;
import se.nordicehealth.servlet.core.PPCLogger;
import se.nordicehealth.servlet.core.PPCStringScramble;
import se.nordicehealth.servlet.impl.exceptions.DBReadException;
import se.nordicehealth.servlet.impl.exceptions.DBWriteException;

public class MySQLDatabase implements PPCDatabase {
	
	public MySQLDatabase(DataSource dataSource, PPCStringScramble crypto, PPCLogger logger) {
		this.logger = logger;
		this.dataSource = dataSource;
		this.crypto = crypto;
	}

	@Override
	public String escapeAndConvertToSQLEntry(String str) {
		if (str == null) { return null; }
		
		return String.format("'%s'", escapeReplaceStr(str));
	}
	
	private String escapeReplaceStr(String str) {
		return str.replace("'", "''");
	}

	@Override
	public String convertToSQLList(List<String> lstr) {
		if (lstr == null) { return null; }
		
		List<String> out = new ArrayList<String>();
		for (String str : lstr) {
			out.add(String.format("\"%s\"", str));
		}
		return String.format("[%s]", String.join(",", out));
	}
	
	public boolean isSQLList(String s) {
		if (s.startsWith("[") && s.endsWith("]")) {
			for (String str : s.substring(1, s.length()-1).trim().split(",")) {
				if (!str.startsWith("\"") || !str.endsWith("\"")) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	public List<String> SQLListToJavaList(String l) throws IllegalArgumentException {
		if (!isSQLList(l)) {
			throw new IllegalArgumentException("Not an SQL list");
		}
		List<String> jlist = new ArrayList<String>();
		for (String str : l.substring(1, l.length()-1).split(",")) {
			jlist.add(str.trim().substring(1, str.length()-1));
		}
		return jlist;
	}

	@Override
	public boolean addUser(int clinic_id, String name,
			String password, String email, String salt)
	{
		String qInsert = String.format(
				"INSERT INTO `users` (`clinic_id`, `name`, `password`, `email`, `registered`, `salt`, `update_password`) VALUES ('%d', '%s', '%s', '%s', '%s', '%s', '%d')",
				clinic_id,
				escapeReplaceStr(name),
				escapeReplaceStr(password),
				escapeReplaceStr(email),
				new SimpleDateFormat("yyyy-MM-dd").format(new Date()),
				escapeReplaceStr(salt), 1);
		try {
			writeToDatabase(qInsert);
			return true;
		} catch (DBWriteException dbw) {
			logger.log("Database write error", dbw);
			return false;
		}
	}
	
	@Override
	public boolean addPatient(int clinic_id, String identifier)
	{
		String patientInsert = String.format(
				"INSERT INTO `patients` (`clinic_id`, `identifier`, `id`) VALUES ('%d', '%s', NULL)",
				clinic_id,
				escapeReplaceStr(identifier));
		try {
			if (!patientInDatabase(identifier)) {
				writeToDatabase(patientInsert);
			}
			return true;
		} catch (DBReadException dbr) {
			logger.log("Database read error", dbr);
		} catch (SQLException se) {
			logger.log("Error opening connection to database or while parsing SQL ResultSet", se);
		} catch (DBWriteException dbw) {
			logger.log("Database write error", dbw);
		}
		return false;
	}

	@Override
	public boolean addQuestionnaireAnswers(
			int clinic_id, String identifier,
			List<String> _question_answers)
	{
		List<String> question_ids = new ArrayList<String>();
		List<String> question_answers = new ArrayList<String>();

		for (int i = 0; i < _question_answers.size(); ++i) {
			question_ids.add(String.format("`question%d`", i));
			question_answers.add(escapeAndConvertToSQLEntry(_question_answers.get(i)));
		}
		String resultInsert = String.format("INSERT INTO `questionnaire_answers` (`clinic_id`, `patient_identifier`, `date`, %s) VALUES ('%d', '%s', '%s', %s)",
				String.join(", ", question_ids),
				clinic_id,
				escapeReplaceStr(identifier),
				new SimpleDateFormat("yyyy-MM-dd").format(new Date()),
				String.join(", ", question_answers));
		try {
			writeToDatabase(resultInsert);
			return true;
		} catch (DBWriteException dbw) {
			logger.log("Database write error", dbw);
			return false;
		}
	}

	@Override
	public boolean addClinic(String name)
	{
		String qInsert = String.format(
				"INSERT INTO `clinics` (`id`, `name`) VALUES (NULL, '%s')",
				escapeReplaceStr(name));
		try {
			writeToDatabase(qInsert);
			return true;
		} catch (DBWriteException dbw) {
			logger.log("Database write error", dbw);
			return false;
		}
	}
	
	@Override
	public Map<Integer, String> getClinics()
	{
		Map<Integer, String> cmap = new TreeMap<Integer, String>();
		Connection conn = null;
		try {
			conn = dataSource.getConnection();
			ResultSet rs = readFromDatabase(conn, "SELECT `id`, `name` FROM `clinics`");
			while (rs.next()) {
				cmap.put(rs.getInt("id"), rs.getString("name"));
			}
		} catch (DBReadException dbr) {
			logger.log("Database read error", dbr);
		} catch (SQLException e) {
			logger.log("Error opening connection to database "
					+ "or while parsing SQL ResultSet", e);
		} finally {
			if (conn != null) {
				try { conn.close(); } catch (SQLException e) { }
			}
		}
		return cmap;
	}

	@Override
	public User getUser(String username)
	{
		String q = String.format("SELECT `clinic_id`, `name`, `password`, `email`, `salt`, `update_password` FROM `users` WHERE `users`.`name`='%s'",
				escapeReplaceStr(username));
		Connection conn = null;
		try {
			conn = dataSource.getConnection();
			ResultSet rs = readFromDatabase(conn, q);

			User _user = null;
			if (rs.next()) {
				_user = new User(crypto);
				_user.clinic_id = rs.getInt("clinic_id");
				_user.name = rs.getString("name");
				_user.password = rs.getString("password");
				_user.email = rs.getString("email");
				_user.salt = rs.getString("salt");
				_user.update_password = rs.getInt("update_password") > 0;
			}
			return _user;
		} catch (DBReadException dbr) {
			logger.log("Database read error", dbr);
		} catch (SQLException se) {
			logger.log("Error opening connection to database "
					+ "or while parsing SQL ResultSet", se);
		} finally {
			if (conn != null) {
				try { conn.close(); } catch (SQLException e) { }
			}
		}
		return null;
	}

	@Override
	public boolean setPassword(String name, String oldPass,
			String newPass, String newSalt)
	{
		User _user = getUser(name);
		if (_user == null || !_user.password.equals(escapeReplaceStr(oldPass))) {
			return false;
		}
		
		String qInsert = String.format(
				"UPDATE `users` SET `password`='%s',`salt`='%s',`update_password`='%d' WHERE `users`.`name`='%s'",
				escapeReplaceStr(newPass),
				escapeReplaceStr(newSalt),
				0,
				escapeReplaceStr(name));
		try {
			writeToDatabase(qInsert);
			return true;
		} catch (DBWriteException dbw) {
			logger.log("Database write error", dbw);
			return false;
		}
	}
	
	@Override
	public Map<Integer, QuestionData> loadQuestions()
	{
		Map<Integer, QuestionData> _questions = new HashMap<Integer, QuestionData>();
		Connection conn = null;
		try {
			conn = dataSource.getConnection();
			ResultSet rs = readFromDatabase(conn,
					"SELECT * FROM `questionnaire`");
			
			while (rs.next()) {
				QuestionData _question = new QuestionData();
				for (int i = 0; ; ++i) {
					try {
						String entry = rs.getString(String.format("option%d", i));
						if (entry == null || (entry = entry.trim()).isEmpty())
							break;
						_question.options.add(i, entry);
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

				_questions.put(id, _question);
			}
		} catch (DBReadException dbr) {
			logger.log("Database read error", dbr);
		} catch (SQLException e) {
			logger.log("Error opening connection to database or while parsing SQL ResultSet", e);
		} finally {
			if (conn != null) {
				try { conn.close(); } catch (SQLException e) { }
			}
		}
		return _questions;
	}

	@Override
	public List<String> loadQuestionResultDates(int clinic_id)
	{
		String q = String.format(
				"SELECT `date` FROM `questionnaire_answers` WHERE `clinic_id`='%d'",
				clinic_id);
		List<String> dlist = new ArrayList<String>();
		Connection conn = null;
		try {
			conn = dataSource.getConnection();
			ResultSet rs = readFromDatabase(conn, q);
			
			while (rs.next()) {
				dlist.add(rs.getString("date"));
			}
		} catch (DBReadException dbr) {
			logger.log("Database read error", dbr);
		} catch (SQLException e) {
			logger.log("Error opening connection to database or while parsing SQL ResultSet", e);
		} finally {
			if (conn != null) {
				try { conn.close(); } catch (SQLException e) { }
			}
		}
		return dlist;
	}
	
	@Override
	public List<Map<Integer, String>> loadQuestionResults(
			int clinic_id, List<Integer> qlist, Date begin, Date end)
	{
		List<Map<Integer, String>> _results = new ArrayList<Map<Integer, String>>();
		Connection conn = null;
		try {
			conn = dataSource.getConnection();
			List<String> lstr = new ArrayList<String>();
			for (Integer i : qlist)
				lstr.add(String.format("`question%d`", i));

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			String q = String.format(
					"SELECT %s FROM `questionnaire_answers` WHERE `clinic_id`='%d' AND `date` BETWEEN '%s' AND '%s'",
					String.join(", ", lstr),
					clinic_id,
					sdf.format(begin),
					sdf.format(end));
			ResultSet rs = readFromDatabase(conn, q);

			while (rs.next()) {
				Map<Integer, String> _answers = new HashMap<Integer, String>();
				for (Integer i : qlist) {
					_answers.put(i, rs.getString(String.format("question%d", i)));
				}
				_results.add(_answers);
			}
		} catch (DBReadException dbr) {
			logger.log("Database read error", dbr);
		} catch (SQLException e) {
			logger.log("Error opening connection to database or while parsing SQL ResultSet", e);
		} finally {
			if (conn != null) {
				try { conn.close(); } catch (SQLException e) { }
			}
		}
		return _results;
	}

	private PPCLogger logger;
	private DataSource dataSource;
	private PPCStringScramble crypto;
	
	private boolean patientInDatabase(String identifier) throws SQLException, DBReadException
	{
		Connection conn = null;
		try {
			conn = dataSource.getConnection();
			String q = String.format(
					"SELECT `identifier` FROM `patients` where `patients`.`identifier`='%s'",
					escapeReplaceStr(identifier));
			ResultSet rs = readFromDatabase(conn, q);
			boolean exsist = rs.next();
			return exsist;
		} finally {
			if (conn != null) {
				try { conn.close(); } catch (SQLException e) { }
			}
		}
	}
	
	private void writeToDatabase(String query) throws DBWriteException {
		Connection conn = null;
		try {
			conn = dataSource.getConnection();
			conn.createStatement().executeUpdate(query);
		} catch (SQLException se) {
			throw new DBWriteException(String.format("Database could not process request: '%s'. Check your arguments.", query));
		} finally {
			if (conn != null) {
				try { conn.close(); } catch (SQLException e) { }
			}
		}
	}
	
	private ResultSet readFromDatabase(Connection conn, String query) throws DBReadException {
		try {
			return conn.createStatement().executeQuery(query);
		} catch (SQLException se) {
			throw new DBReadException(String.format("Database could not process request: '%s'. Check your arguments.", query));
		}
	}
}