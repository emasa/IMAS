/**
 *  IMAS base code for the practical work.
 *  Copyright (C) 2014 DEIM - URV
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cat.urv.imas.agent;

import cat.urv.imas.onthology.GameSettings;
import cat.urv.imas.behaviour.coordinator.RequesterBehaviour;
import cat.urv.imas.onthology.MessageContent;
import cat.urv.imas.map.BuildingCell;
import cat.urv.imas.map.SettableBuildingCell;
import cat.urv.imas.onthology.GarbageType;

import jade.core.*;
import jade.domain.*;
import jade.proto.ContractNetInitiator;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPANames.InteractionProtocol;
import jade.lang.acl.*;
import java.io.IOException;

import java.util.Date;
import java.util.Vector;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main Coordinator agent. 
 * TODO: This coordinator agent should get the game settings from the System
 * agent every round and share the necessary information to other coordinators.
 */
public class CoordinatorAgent extends ImasAgent {

    /**
     * Game settings in use.
     */
    private GameSettings game;
    /**
     * System agent id.
     */
    private AID systemAgent;
    /**
     * System agent id.
     */
    private AID scoutCoordinatorAgent;

    /**
     * Builds the coordinator agent.
     */
    public CoordinatorAgent() {
        super(AgentType.COORDINATOR);
    }

    /**
     * Agent setup method - called when it first come on-line. Configuration of
     * language to use, ontology and initialization of behaviours.
     */
    @Override
    protected void setup() {
        /* ** Very Important Line (VIL) ***************************************/
        this.setEnabledO2ACommunication(true, 1);
        /* ********************************************************************/

        // Register the agent to the DF
        ServiceDescription ca = new ServiceDescription();
        ca.setType(AgentType.COORDINATOR.toString());
        ca.setName(getLocalName());
        ca.setOwnership(OWNER);
        
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.addServices(ca);
        dfd.setName(getAID());
        
        try {
            DFService.register(this, dfd);
            log("Registered to the DF");
        } catch (FIPAException e) {
            System.err.println(getLocalName() + " registration with DF unsucceeded. Reason: " + e.getMessage());
            doDelete();
        }
       
        // search SystemAgent
        ServiceDescription searchCriterion = new ServiceDescription();
        searchCriterion.setType(AgentType.SYSTEM.toString());
        this.systemAgent = UtilsAgents.searchAgent(this, searchCriterion);
        // search ScoutCoordinatorAgent
        searchCriterion.setType(AgentType.SCOUT_COORDINATOR.toString());
        this.scoutCoordinatorAgent = UtilsAgents.searchAgent(this, searchCriterion);
        // searchAgent is a blocking method, so we will obtain always a correct AID

        /* ********************************************************************/
        ACLMessage gameRequest = new ACLMessage(ACLMessage.REQUEST);
        gameRequest.clearAllReceiver();
        gameRequest.addReceiver(this.systemAgent);
        gameRequest.setProtocol(InteractionProtocol.FIPA_REQUEST);
        log("Request message to agent");
        try {
            gameRequest.setContent(MessageContent.GET_MAP);
            log("Request message content:" + gameRequest.getContent());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // setup finished. When we receive the last inform, the agent itself will add
        // a behaviour to send/receive actions
        
        /* ********************************************************************/
        // contract net system
        ServiceDescription searchHC = new ServiceDescription();     
        searchHC.setType(AgentType.HARVESTER_COORDINATOR.toString());
        this.systemAgent = UtilsAgents.searchAgent(this, searchHC);    
        
        ////////////// Dummy SettableBuildingCell
        SettableBuildingCell celda = new SettableBuildingCell(0, 1);
        celda.setGarbage(GarbageType.PAPER, 2);
        String content = celda.getMapMessage()+" in R"+celda.getRow()+" C"+celda.getCol();        
        ///////////////
        
        // Fill the CFP message
        ACLMessage contract = new ACLMessage(ACLMessage.CFP);
        contract.addReceiver(this.systemAgent);
        contract.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
        // We want to receive a reply in 10 secs
        contract.setReplyByDate(new Date(System.currentTimeMillis() + 10000));
        contract.setContent(content);                             
        
        try {
            contract.setContentObject(celda);
        } catch (IOException ex) {
            Logger.getLogger(CoordinatorAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
        log("ContractNet Started");
        System.out.println("1. Coordinator Agent sent a ContractNet to collect "+content);
        
        this.addBehaviour(new ContractNetInitiator(this, contract) {			
            protected void handlePropose(ACLMessage propose, Vector v) {
                // Receive Proposal
                System.out.println("3. Agent "+propose.getSender().getName()+" Proposed a Coalition to collect: "+propose.getContent());             
            }

            protected void handleRefuse(ACLMessage refuse) {
                System.out.println("3. Agent "+refuse.getSender().getName()+" refused");
            }

            protected void handleFailure(ACLMessage failure) {
                if (failure.getSender().equals(myAgent.getAMS())) {
                    // FAILURE notification from the JADE runtime: the receiver
                    // does not exist
                    System.out.println("Responder does not exist");
                }
                else {
                    System.out.println("Agent "+failure.getSender().getName()+" failed");
                }
            }

            protected void handleAllResponses(Vector responses, Vector acceptances) {
                // Accept Proposal. CA always accepts proposal
                Enumeration e = responses.elements();
                while (e.hasMoreElements()) {
                    ACLMessage proposal = (ACLMessage) e.nextElement();                    
                    ACLMessage accept = proposal.createReply();
                    accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    acceptances.addElement(accept);
                    accept.setContent(proposal.getContent()); 
                    System.out.println("4. "+getLocalName()+" Accepted proposal: "+proposal.getContent());
                }             
            }
                        
            protected void handleInform(ACLMessage inform) {
                System.out.println("8. "+inform.getSender().getName()+" successfully performed: "+inform.getContent());
            }
        });
        
        this.addBehaviour(new RequesterBehaviour(this, gameRequest));
    }

    /**
     * Update the game settings.
     *
     * @param game current game settings.
     */
    public void setGame(GameSettings game) {
        this.game = game;
    }

    /**
     * Gets the current game settings.
     *
     * @return the current game settings.
     */
    public GameSettings getGame() {
        return this.game;
    }

    public AID getScoutCoordinatorAgent() {
        return scoutCoordinatorAgent;
    }

    public void setScoutCoordinatorAgent(AID scoutCoordinatorAgent) {
        this.scoutCoordinatorAgent = scoutCoordinatorAgent;
    }

    public AID getSystemAgent() {
        return systemAgent;
    }

    public void setSystemAgent(AID systemAgent) {
        this.systemAgent = systemAgent;
    }

}
