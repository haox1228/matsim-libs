/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** *
 */

package org.matsim.contrib.drt.analysis.zonal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.contrib.drt.analysis.zonal.DrtGridUtils.createGridFromNetwork;
import static org.matsim.contrib.drt.analysis.zonal.DrtGridUtilsTest.createNetwork;

import java.util.Map;

import org.junit.Test;
import org.matsim.api.core.v01.Id;

/**
 * @author Michal Maciejewski (michalm)
 */
public class DrtZonalSystemTest {

	@Test
	public void test_cellSize100() {
		DrtZonalSystem drtZonalSystem = new DrtZonalSystem(createNetwork(),
				createGridFromNetwork(createNetwork(), 100));
		assertThat(drtZonalSystem.getZoneForLinkId(Id.createLinkId("ab")).getId()).isEqualTo("5");
	}

	@Test
	public void test_cellSize700() {
		DrtZonalSystem drtZonalSystem = new DrtZonalSystem(createNetwork(),
				createGridFromNetwork(createNetwork(), 700));
		assertThat(drtZonalSystem.getZoneForLinkId(Id.createLinkId("ab")).getId()).isEqualTo("1");
	}

	@Test
	public void test_linkOutsideZonalSystem() {
		DrtZonalSystem drtZonalSystem = new DrtZonalSystem(createNetwork(), Map.of());
		assertThat(drtZonalSystem.getZoneForLinkId(Id.createLinkId("ab"))).isNull();
	}
}
