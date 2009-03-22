package org.tn5250j;

import javax.swing.SwingUtilities;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		System.out.println("xxx Hello World!!");
		My5250.main(new String[]{});
//		SwingUtilities.invokeLater(new Runnable() {
//
//			public void run() {
//				// TODO Auto-generated method stub
//				
//			}
//			
//		});
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		System.out.println("xxx Goodbye World!!");
	}

}
