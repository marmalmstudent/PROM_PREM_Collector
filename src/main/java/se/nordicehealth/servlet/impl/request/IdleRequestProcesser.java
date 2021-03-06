package se.nordicehealth.servlet.impl.request;

import se.nordicehealth.servlet.core.PPCLogger;
import se.nordicehealth.servlet.core.PPCUserManager;
import se.nordicehealth.servlet.impl.io.IPacketData;

public abstract class IdleRequestProcesser extends RequestProcesser {
	protected PPCUserManager um;

	public IdleRequestProcesser(IPacketData packetData, PPCLogger logger, PPCUserManager um) {
		super(packetData, logger);
		this.um = um;
	}

	public boolean refreshTimer(long uid) {
		return um.refreshInactivityTimer(uid);
	}
}
