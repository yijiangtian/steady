/**
 * This file is part of Eclipse Steady.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2018 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.sap.psr.vulas.cg;

import java.util.Set;

import com.sap.psr.vulas.backend.BackendConnectionException;
import com.sap.psr.vulas.backend.BackendConnector;
import com.sap.psr.vulas.shared.enums.GoalType;
import com.sap.psr.vulas.shared.enums.PathSource;
import com.sap.psr.vulas.shared.util.ConstructIdUtil;
import com.sap.psr.vulas.shared.util.VulasConfiguration;

/**
 * <p>T2CGoal class.</p>
 *
 */
public class T2CGoal extends AbstractReachGoal {

	private Set<com.sap.psr.vulas.shared.json.model.ConstructId> entryPoints = null;

	private Set<com.sap.psr.vulas.shared.json.model.ConstructId> tracedConstructs = null;

	/**
	 * <p>Constructor for T2CGoal.</p>
	 */
	public T2CGoal() { super(GoalType.T2C); }

	/**
	 * <p>Getter for the field <code>entryPoints</code>.</p>
	 *
	 * @return a {@link java.util.Set} object.
	 */
	protected final Set<com.sap.psr.vulas.shared.json.model.ConstructId> getEntryPoints() {
		if(this.entryPoints==null) {
			try {
				// Get traces
				this.tracedConstructs = BackendConnector.getInstance().getAppTraces(this.getGoalContext(), this.getApplicationContext());
				
				// Filter constructs (if requested)
				final String[] filter = this.getConfiguration().getConfiguration().getStringArray(ReachabilityConfiguration.REACH_CONSTR_FILTER);
				if(filter!=null && filter.length>0 && !(filter.length==1 && filter[0].equals(""))) {
					this.entryPoints = ConstructIdUtil.filterWithRegex(this.tracedConstructs, filter);
				} else {
					this.entryPoints = this.tracedConstructs;
				}
			} catch (BackendConnectionException e) {
				throw new IllegalStateException(e.getMessage());
			}
		}
		return this.entryPoints;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Sets the traced constructs as entry points of the {@link ReachabilityAnalyzer}.
	 */
	protected final void setEntryPoints(ReachabilityAnalyzer _ra) {
		_ra.setEntryPoints(this.getEntryPoints(), PathSource.T2C, this.getConfiguration().getConfiguration().getBoolean(ReachabilityConfiguration.REACH_EXIT_UNKOWN_EP, false));
	}
}
