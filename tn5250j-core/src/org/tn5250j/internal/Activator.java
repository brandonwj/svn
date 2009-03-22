package org.tn5250j.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.tn5250j.My5250;

public class Activator implements BundleActivator {
	
	private BundleContext bc = null;
	private Thread runner = null;

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		this.bc = context;
		//FIXME: According to OSGI spec., a bundle is in status STARTING as long
		//       as the Activator is not successfully finished.
		//       So, we should somehow finish this activator AND additionally
		//       starting the My5250.main() method.
		runner = new Thread() {
			@Override
			public void run() {
				My5250.main(new String[]{});  
			}
		};
		runner.setDaemon(true);
		runner.start();
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		try {
			this.runner.interrupt();
		} catch (Exception ignoreIt) {;}
		this.bc = null;
	}

}
