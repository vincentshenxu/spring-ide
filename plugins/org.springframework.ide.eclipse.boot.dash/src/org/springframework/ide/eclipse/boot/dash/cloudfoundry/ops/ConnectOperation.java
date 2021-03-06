/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudFoundryBootDashModel;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudFoundryTargetProperties;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.MissingPasswordException;
import org.springframework.ide.eclipse.boot.dash.dialogs.PasswordDialogModel;
import org.springframework.ide.eclipse.boot.dash.model.RefreshState;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.CannotAccessPropertyException;
import org.springframework.ide.eclipse.boot.util.Log;

/**
 * Operation for connecting/disconnecting CF run target
 *
 * @author Alex Boyko
 *
 */
public class ConnectOperation extends CloudOperation {

	private boolean connect;
	private UserInteractions ui;

	public ConnectOperation(CloudFoundryBootDashModel model, boolean connect) {
		super("Connecting run target " + model.getRunTarget().getName(), model);
		this.connect = connect;
	}

	public ConnectOperation(CloudFoundryBootDashModel model, boolean connect, UserInteractions ui) {
		this(model, connect);
		this.ui = ui;
	}

	@Override
	protected void doCloudOp(IProgressMonitor monitor) throws Exception, OperationCanceledException {
		if (model.getRunTarget() != null) {
			if (connect && !model.getRunTarget().isConnected()) {
				try {
					model.setBaseRefreshState(RefreshState.loading("Connecting..."));
					model.getRunTarget().connect();
					model.refresh(ui);
					model.getRunTarget().getTargetProperties().put(CloudFoundryTargetProperties.DISCONNECTED, null);
					model.getViewModel().updateTargetPropertiesInStore();
					model.setBaseRefreshState(RefreshState.READY);
				} catch (MissingPasswordException|CannotAccessPropertyException|AssertionFailedException e) {
					model.setBaseRefreshState(RefreshState.READY);
					if (ui == null) {
						Log.log(e);
					} else {
						PasswordDialogModel passwordDialogModel = new PasswordDialogModel(
								model.getRunTarget().getTargetProperties().getUsername(), model.getRunTarget().getId(),
								model.getRunTarget().getTargetProperties().isStorePassword());
						ui.openPasswordDialog(passwordDialogModel);
						if (passwordDialogModel.isOk()) {
							model.getRunTarget().getTargetProperties().setStorePassword(passwordDialogModel.getStoreVar().getValue());
							String password = passwordDialogModel.getPasswordVar().getValue();
							// The password cannot be null or empty string - enforced by the dialog
							// Do the check just in case for tests bypassing the UI
							if (password != null && !password.isEmpty()) {
								try {
									model.getRunTarget().getTargetProperties().setPassword(password);
								} catch (CannotAccessPropertyException e1) {
									ui.warningPopup("Failed Storing Password",
											"Failed to store password in Secure Storage for " + passwordDialogModel.getTargetId()
													+ ". Secure Storage is most likely locked. Current password will be kept until disconnect.");
									// Set "remember password" to false. Password hasn't been stored.
									model.getRunTarget().getTargetProperties().setStorePassword(false);
								}
								// At this point the password must be set otherwise an exception from the call above would be thrown
								doCloudOp(monitor);
							}
						}
					}
				} catch (Exception e) {
					model.setBaseRefreshState(RefreshState.error(e));
					if (ui == null) {
						throw e;
					} else {
						Log.log(e);
						ui.errorPopup("Cannot Connect to Cloud Foundry", "Failed to connect to " + model.getRunTarget().getId() + ". Ensure login credentials are correct.");
					}
				}
			} else if (!connect && model.getRunTarget().isConnected()) {
				model.setBaseRefreshState(RefreshState.loading("Disconnecting..."));
				model.getRunTarget().disconnect();
				model.getRunTarget().getTargetProperties().put(CloudFoundryTargetProperties.DISCONNECTED, "true"); //$NON-NLS-1$
				if (!model.getRunTarget().getTargetProperties().isStorePassword()) {
					// Forget password on disconnect if it's not stored
					model.getRunTarget().getTargetProperties().setPassword(null);
				}
				model.getViewModel().updateTargetPropertiesInStore();
				model.setBaseRefreshState(RefreshState.READY);
			}
		}
	}

	public ISchedulingRule getSchedulingRule() {
		return new RefreshSchedulingRule(model.getRunTarget());
	}

}
