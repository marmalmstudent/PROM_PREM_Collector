/**
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
package implementation;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import core.interfaces.Locale;
import core.interfaces.Messages;

/**
 * This class is an implementation of locale-related methods.
 * This implementation is created for Sweden.
 * 
 * @author Marcus Malmquist
 *
 */
public class Locale_se implements Locale
{
	/* Public */
	
	/**
	 * 
	 * @return The active instance of this class.
	 */
	public static synchronized Locale_se getLocale()
	{
		if (locale == null)
			locale = new Locale_se();
		return locale;
	}

	@Override
	public final Object clone()
			throws CloneNotSupportedException
	{
		throw new CloneNotSupportedException();
	}

	@Override
	public String formatPersonalID(String pID)
	{
		DateFormat dateFormat;
		Date date = null;
		pID = pID.trim();
		Integer lastFour = null;
		try
		{
			switch(pID.length())
			{
			case 10: // yymmddxxx
			case 11: // yymmdd-xxxx
				dateFormat = new SimpleDateFormat("yyMMdd");
				dateFormat.setLenient(false);
				date = dateFormat.parse(pID.substring(0, 6));
				lastFour = Integer.parseInt(pID.substring(pID.length()-4));
				break;
			case 12: // yyyymmddxxx
			case 13: // yyyymmdd-xxxx
				dateFormat = new SimpleDateFormat("yyyyMMdd");
				dateFormat.setLenient(false);
				date = dateFormat.parse(pID.substring(0, 8));
				lastFour = Integer.parseInt(pID.substring(pID.length()-4));
				break;
			default:
				throw new ParseException("Unknown format", 0);
			}
		} catch (ParseException pe)
		{
			return null;
		}
		return String.format("%s-%04d",
				(new SimpleDateFormat("yyyyMMdd")).format(date), lastFour);
	}
	
	/* Protected */
	
	/* Private */
	
	private static Locale_se locale;
	
	private Locale_se()
	{
		
	}
}
