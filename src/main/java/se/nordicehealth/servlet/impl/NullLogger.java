package se.nordicehealth.servlet.impl;

import java.util.logging.Level;

import se.nordicehealth.servlet.core.PPCLogger;

public class NullLogger implements PPCLogger {

	public NullLogger() { }

	@Override
	public void log(String msg) { }

	@Override
	public void log(Level level, String msg) { }

	@Override
	public void fatalLogAndAction(String msg) { }

	@Override
	public void log(String msg, Exception e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void log(Level level, String msg, Exception e) { }

}
