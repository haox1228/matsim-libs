/* *********************************************************************** *
 * project: org.matsim.*
 * DgRoederGershensonController
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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
 * *********************************************************************** */
package playground.dgrether.signalsystems.roedergershenson;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalController;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalPlan;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.mobsim.qsim.interfaces.SignalGroupState;
import org.matsim.lanes.data.Lane;
import org.matsim.lanes.data.LanesToLinkAssignment;

import com.google.inject.Provider;

import playground.dgrether.signalsystems.LinkSensorManager;


/**
 * @author dgrether
 *
 */
public class DgRoederGershensonSignalController implements SignalController {
	
	public final static String IDENTIFIER = "gretherRoederGershensonSignalControl";
	
	public final static class SignalControlProvider implements Provider<SignalController> {
		private final LinkSensorManager sensorManager;
		private final Scenario scenario;

		public SignalControlProvider(LinkSensorManager sensorManager, Scenario scenario) {
			this.sensorManager = sensorManager;
			this.scenario = scenario;
		}

		@Override
		public SignalController get() {
			return new DgRoederGershensonSignalController(scenario, sensorManager);
		}
	}
	
	private class SignalGroupMetadata {

		private Set<Link> inLinks = new HashSet<Link>();
		private Set<Link> outLinks = new HashSet<Link>();
		
		public void addInLink(Link link) {
			this.inLinks.add(link);
		}

		public void addOutLink(Link outLink) {
			this.outLinks.add(outLink);
		}
		
		public Set<Link> getOutLinks(){
			return this.outLinks;
		}
		
		public Set<Link> getInLinks(){
			return this.inLinks;
		}
		
	}

	private static final Logger log = Logger.getLogger(DgRoederGershensonSignalController.class);
	
	private SignalSystem system;

	protected int tGreenMin =  0; // time in seconds
	protected int minCarsTime = 0; //
	protected double storageCapacityOutlinkJam = 0.8;
	protected double maxRedTime = 15.0;
	
	private boolean interim = false;
	private double interimTime;

	protected boolean outLinkJam;
	protected boolean maxRedTimeActive = false;
	protected double compGreenTime;
	protected double approachingRed;
	protected double approachingGreenLink;
	protected double approachingGreenLane;
	protected double carsOnRefLinkTime;
	protected boolean compGroupsGreen;
	protected SignalGroupState oldState;

	private double switchedGreen = 0;

	private LinkSensorManager sensorManager;
	private Scenario scenario;

	private Map<Id<SignalGroup>, SignalGroupMetadata> signalGroupIdMetadataMap;
	
	private DgRoederGershensonSignalController(Scenario scenario, LinkSensorManager sensorManager) {
		this.scenario = scenario;
		this.sensorManager = sensorManager;
	}

	private void registerAndInitializeSensorManager() {
		for (SignalGroupMetadata metadata : this.signalGroupIdMetadataMap.values()){
			for (Link outLink : metadata.getOutLinks()){
				this.sensorManager.registerNumberOfCarsMonitoring(outLink.getId());
			}
			//TODO initialize inLinks
		}	
	}

	private void initSignalGroupMetadata(){
		SignalsData signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
		this.signalGroupIdMetadataMap = new HashMap<>();
		SignalSystemData systemData = signalsData.getSignalSystemsData().getSignalSystemData().get(this.system.getId());
		
		for (SignalGroup signalGroup : this.system.getSignalGroups().values()){
			if (!this.signalGroupIdMetadataMap.containsKey(signalGroup.getId())){
				this.signalGroupIdMetadataMap.put(signalGroup.getId(), new SignalGroupMetadata());
			}
			SignalGroupMetadata metadata = this.signalGroupIdMetadataMap.get(signalGroup.getId());
			for (Signal signal : signalGroup.getSignals().values()){
				//inlinks
				Link inLink = scenario.getNetwork().getLinks().get(signal.getLinkId());
				metadata.addInLink(inLink);
				//outlinks
				SignalData signalData = systemData.getSignalData().get(signal.getId());
				if (signalData.getTurningMoveRestrictions() == null || signalData.getTurningMoveRestrictions().isEmpty()){
					if (signalData.getLaneIds() == null || signalData.getLaneIds().isEmpty()){
						this.addOutLinksWithoutBackLinkToMetadata(inLink, metadata);
					}
					else { // there are lanes
						LanesToLinkAssignment lanes4link = scenario.getLanes().getLanesToLinkAssignments().get(signalData.getLinkId());
						for (Id<Lane> laneId : signalData.getLaneIds()){
							Lane lane = lanes4link.getLanes().get(laneId);
							if (lane.getToLinkIds() == null || lane.getToLinkIds().isEmpty()){
								this.addOutLinksWithoutBackLinkToMetadata(inLink, metadata);
							}
							else{
								for (Id<Link> toLinkId : lane.getToLinkIds()){
									Link toLink = scenario.getNetwork().getLinks().get(toLinkId);
									if (!toLink.getFromNode().equals(inLink.getToNode())){
										metadata.addOutLink(toLink);
									}
								}
							}
						}
					}
				}
				else {  // turning move restrictions exist
					for (Id<Link> linkid : signalData.getTurningMoveRestrictions()){
						Link outLink = scenario.getNetwork().getLinks().get(linkid);
						metadata.addOutLink(outLink);
					}
				}
			}
		}
	}
	
	private void addOutLinksWithoutBackLinkToMetadata(Link inLink, SignalGroupMetadata metadata){
		for (Link outLink : inLink.getToNode().getOutLinks().values()){
			if (!outLink.getFromNode().equals(inLink.getToNode())){
				metadata.addOutLink(outLink);
			}
		}
	}
	
	
	@Override
	public void simulationInitialized(double simStartTimeSeconds) {
		SignalGroup last = null;
		this.groupStateMap = new HashMap<>();
		for (SignalGroup g : this.system.getSignalGroups().values()){
			GroupState state = new GroupState();
			state.lastDropping = simStartTimeSeconds;
			this.groupStateMap.put(g.getId(), state);
			system.scheduleDropping(simStartTimeSeconds, g.getId());
			last = g;
		}
		last.setState(SignalGroupState.GREEN);
		this.switchGroup2Green(simStartTimeSeconds, last);
	}
	
	private void switchGroup2Green(double timeSeconds, SignalGroup g){
		this.greenGroup =  g;			
		this.system.scheduleOnset(timeSeconds, g.getId());
	}
	
	private void switchGroup2Red(double timeSeconds, SignalGroup g){
		this.groupStateMap.get(g.getId()).lastDropping = timeSeconds;
		this.system.scheduleDropping(timeSeconds, g.getId());
	}
	
	private boolean hasOutLinkJam(SignalGroup group, SignalGroupMetadata metadata){
		for (Link link : metadata.getOutLinks()){
			double storageCap = (link.getLength() * link.getNumberOfLanes()) / (7.5 * this.scenario.getConfig().qsim().getStorageCapFactor());
			if (this.sensorManager.getNumberOfCarsOnLink(link.getId()) > (storageCap * this.storageCapacityOutlinkJam)){
				return true;
			}
		}
		return false;
	}
	
	/**
	 * The signal group for that the next onset will be triggered
	 */
	private SignalGroup greenGroup;
	
	private Map<Id<SignalGroup>, GroupState> groupStateMap;

	private boolean allRed;
	
	private class GroupState {
		double lastDropping;
	}

	
	private SignalGroup checkMaxRedTime(double timeSeconds){
		if (timeSeconds > 15.0){
			log.info("");
		}
		for (SignalGroup group : this.system.getSignalGroups().values()){
			GroupState state = this.groupStateMap.get(group.getId());
			log.error("  Group " + group.getId()  + " state " + group.getState() + " last drop: " + state.lastDropping);
			if (SignalGroupState.RED.equals(group.getState()) 
					&& ((timeSeconds - state.lastDropping) > this.maxRedTime)){
				log.error("  Group " + group.getId() + " red for " + (timeSeconds - state.lastDropping));
				return group;
			}
		}
		return null;
	}
	
	private boolean isSwitching(){
		if (SignalGroupState.GREEN.equals(this.greenGroup.getState())){
			return false;
		}
		return true;
	}
	
	@Override
	public void updateState(double timeSeconds) {
		log.error("Signal system: " + this.system.getId() + " at time " + timeSeconds);
		//if the groups are switching do nothing
		if (this.isSwitching()){
			log.error("  Group: " + this.greenGroup.getId() + " is switching with state: " + this.greenGroup.getState());
			return;
		}
		log.error("  is not switching...");
		SignalGroup newGreenGroup = null;
		//rule 7 
		SignalGroup maxRedViolatingGroup = this.checkMaxRedTime(timeSeconds);
		if (maxRedViolatingGroup != null){
			newGreenGroup = maxRedViolatingGroup;
			log.error("new green group is : " + newGreenGroup.getId());
		}
		if (newGreenGroup != null){
			this.switchGroup2Red(timeSeconds, this.greenGroup);
			this.switchGroup2Green(timeSeconds, newGreenGroup);
		}
		//rule 6
		Set<SignalGroup> notOutLinkJamGroups = new HashSet<SignalGroup>();
		for (SignalGroup group : this.system.getSignalGroups().values()){
			if (!this.hasOutLinkJam(group, this.signalGroupIdMetadataMap.get(group.getId()))){
				notOutLinkJamGroups.add(group);
			}
		}
		if (this.greenGroup != null){
			if (!notOutLinkJamGroups.contains(this.greenGroup)){
				notOutLinkJamGroups.remove(this.greenGroup);
				this.switchGroup2Red(timeSeconds, this.greenGroup);
				this.greenGroup = null;
			}
		}
		if (this.greenGroup == null && !notOutLinkJamGroups.isEmpty()) { //we have an all red but one has no jam
			this.switchGroup2Green(timeSeconds, notOutLinkJamGroups.iterator().next());
		}
		//rule 4
		
		
		
//		for (SignalGroup group : this.system.getSignalGroups().values()){
//			SignalGroupMetadata metadata = this.signalGroupIdMetadataMap.get(group.getId());
//			//rule 6
//			if (group.getState().equals(SignalGroupState.GREEN) && this.hasOutLinkJam(group, metadata)){ 
//				//TODO schedule dropping
//				continue;
//			}
//			
//			else {
//				//if all red switch to green
//			
//				//rule 4
//				if (!approachingGreenInD() && approachingRedInD){
//						//switch something
//				}
//				//rule 3
//				if (group.getState().equals(SignalGroupState.GREEN) && hasMoreThanMVehiclesInR(group)){
//					//don't switch
//				}
//				//rule 2
//				if (group.getState().equals(SignalGroupState.GREEN) && greenTime(group) < minGreenTime){
//					//don't switch
//				}
//				//rule 1
//				if (group.getState().equals(SignalGroupState.RED) && numberCarsInD * timeRed > n_min){
//					//switch to green
//				}
//			}
//		}
	}

		
	
	@Override
	public void addPlan(SignalPlan plan) {
		//nothing to do here as we don't deal with plans
	}

	@Override
	public void reset(Integer iterationNumber) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setSignalSystem(SignalSystem signalSystem) {
		this.system = signalSystem ;		
		
		/* start initialization process. 
		 * can not be started earlier in the constructor because the signal system ID is needed. theresa, jan'17 */
		initSignalGroupMetadata();
		registerAndInitializeSensorManager();
	}

}
