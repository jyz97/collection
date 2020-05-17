# myTeam.py
# ---------
# Licensing Information:  You are free to use or extend these projects for
# educational purposes provided that (1) you do not distribute or publish
# solutions, (2) you retain this notice, and (3) you provide clear
# attribution to UC Berkeley, including a link to http://ai.berkeley.edu.
# 
# Attribution Information: The Pacman AI projects were developed at UC Berkeley.
# The core projects and autograders were primarily created by John DeNero
# (denero@cs.berkeley.edu) and Dan Klein (klein@cs.berkeley.edu).
# Student side autograding was added by Brad Miller, Nick Hay, and
# Pieter Abbeel (pabbeel@cs.berkeley.edu).

#PEP8 Style checker disabled


from captureAgents import CaptureAgent
import random, time, util, math
from game import Directions
import game
from util import nearestPoint

#################
# Team creation #
#################

def createTeam(firstIndex, secondIndex, isRed,
               first = 'AlternatingAgent', second = 'AlternatingAgent'):
    """
    This function should return a list of two agents that will form the
    team, initialized using firstIndex and secondIndex as their agent
    index numbers.  isRed is True if the red team is being created, and
    will be False if the blue team is being created.

    As a potentially helpful development aid, this function can take
    additional string-valued keyword arguments ("first" and "second" are
    such arguments in the case of this function), which will come from
    the --redOpts and --blueOpts command-line arguments to capture.py.
    For the nightly contest, however, your team will be created without
    any extra arguments, so you should make sure that the default
    behavior is what you want for the nightly contest.
    """

    # The following line is an example only; feel free to change it.
    return [eval(first)(firstIndex), eval(second)(secondIndex)]

##########
# Agents #
##########

class DummyAgent(CaptureAgent):
    """
    A Dummy agent to serve as an example of the necessary agent structure.
    You should look at baselineTeam.py for more details about how to
    create an agent as this is the bare minimum.
    """

    def registerInitialState(self, gameState):
        """
        This method handles the initial setup of the
        agent to populate useful fields (such as what team
        we're on).

        A distanceCalculator instance caches the maze distances
        between each pair of positions, so your agents can use:
        self.distancer.getDistance(p1, p2)

        IMPORTANT: This method may run for at most 15 seconds.
        """

        '''
        Make sure you do not delete the following line. If you would like to
        use Manhattan distances instead of maze distances in order to save
        on initialization time, please take a look at
        CaptureAgent.registerInitialState in captureAgents.py.
        '''
        CaptureAgent.registerInitialState(self, gameState)

        '''
        Your initialization code goes here, if you need any.
        '''


    def chooseAction(self, gameState):
        """
        Picks among actions randomly.
        """
        actions = gameState.getLegalActions(self.index)

        '''
        You should change this in your own agent.
        '''

        return random.choice(actions)






class AlternatingAgent(CaptureAgent):

    def registerInitialState(self, gameState):
        self.start = gameState.getAgentPosition(self.index)
        self.isOffensive = True         #By default, assign offensive method
        self.indices = self.getTeam(gameState)
        self.walls = gameState.getWalls()
        CaptureAgent.registerInitialState(self, gameState)

    def chooseAction(self, gameState):
        """
        Picks among the actions with the highest Q(s,a).
        """
        actions = gameState.getLegalActions(self.index)

        # You can profile your evaluation time by uncommenting these lines
        # start = time.time()
        opIndices = self.getOpponents(gameState)
        opStates = [gameState.getAgentState(i) for i in opIndices]
        opCarry = [x.numCarrying for x in opStates]
        
        if max(opCarry) >= 5:
            self.isOffensive = False
        else:
            self.isOffensive = True

        values = [self.evaluate(gameState, a) for a in actions]
        # print 'eval time for agent %d: %.4f' % (self.index, time.time() - start)

        maxValue = max(values)
        bestActions = [a for a, v in zip(actions, values) if v == maxValue]



        # print if get eaten
        myPos = gameState.getAgentPosition(self.index)
        prevGameState = self.getPreviousObservation()
        if prevGameState is not None:

            previousPos = prevGameState.getAgentPosition(self.index)
            if self.getMazeDistance(myPos, previousPos) > 1:
                print("prePostion",previousPos)
                print()
                previousLegalAction = prevGameState.getLegalActions(self.index)
                print([(self.evaluate(prevGameState, a), a) for a in previousLegalAction])
                print()
                print(self.getNonScaredGhostPos(prevGameState))
                print()
                print()


        return random.choice(bestActions)

    def getSuccessor(self, gameState, action):
        """
        Finds the next successor which is a grid position (location tuple).
        """
        successor = gameState.generateSuccessor(self.index, action)
        pos = successor.getAgentState(self.index).getPosition()
        if pos != nearestPoint(pos):
            # Only half a grid position was covered
            return successor.generateSuccessor(self.index, action)
        else:
            return successor

    def evaluate(self, gameState, action):
        """
        Computes a linear combination of features and feature weights
        """
        features = self.getFeatures(gameState, action)
        weights = self.getWeights(gameState, action)
        return features * weights

    def getFeatures(self, gameState, action):
        """
        Returns a counter of features for the state
        """
        # features = util.Counter()
        # successor = self.getSuccessor(gameState, action)
        # features['successorScore'] = self.getScore(successor)
        # return features
        if self.isOffensive:
            return self.getOffensiveFeatures(gameState, action)
        else:
            return self.getDefensiveFeatures(gameState, action)

    def getWeights(self, gameState, action):
        """
        Normally, weights do not depend on the gamestate.  They can be either
        a counter or a dictionary.
        """
        # return {'successorScore': 1.0}
        if self.isOffensive:
            return self.getOffensiveWeights(gameState, action)
        else:
            return self.getDefensiveWeights(gameState, action)



    def getGhostPos(self, gameState):
        # if self.red:
        #     opIndices = gameState.getBlueTeamIndices()
        # else:
        #     opIndices = gameState.getRedTeamIndices()
        opIndices = self.getOpponents(gameState)
        opGhostPos = [gameState.getAgentPosition(i) for i in opIndices \
                        if not gameState.getAgentState(i).isPacman]
        #print("opponent ghost positions",opGhostPos)
        return opGhostPos
        # opPos = [gameState.getAgentPosition(i) for i in opIndices]
        # opStates = [gamestate.getAgentState(i) for i in opIndices]

    def getNonScaredGhostPos(self, gameState):
        opIndices = self.getOpponents(gameState)
        #TODO 
        self.opGhostPos = [gameState.getAgentPosition(i) for i in opIndices \
                      if (not gameState.getAgentState(i).isPacman) and gameState.getAgentState(i).scaredTimer == 0]
        #print("opponent ghost positions",opGhostPos)
        return self.opGhostPos


    """****************"""
    """OffensiveMethods"""
    """****************"""

    def getOffensiveFeatures(self, gameState, action):
        features = util.Counter()
        successor = self.getSuccessor(gameState, action)
        foodList = self.getFood(successor).asList()
        features['successorScore'] = -len(foodList)#self.getScore(successor)
        features['deadEnd'] = 0

        myPos = successor.getAgentState(self.index).getPosition()
        friendIndex = [i for i in self.indices if i != self.index][0]
        if self.index < friendIndex:
            foodList = [food for food in foodList if food[1] <= self.walls.height/2]
        else:
            foodList = [food for food in foodList if food[1] > self.walls.height/2]

        # agentState = gameState.getAgentState(self.index)
        # Compute distance to the nearest food

        if len(foodList) > 0: # This should always be True,  but better safe than sorry
            
            minDistance = min([self.getMazeDistance(myPos, food) for food in foodList])
            features['distanceToFood'] = minDistance

        #opGhostPos = self.getGhostPos(gameState)
        # Get non Scared Opponent Ghosts

        minDisToGhost = math.inf

        opGhostPos = self.getNonScaredGhostPos(gameState)
        if len(opGhostPos) > 0:
            #if we have ghosts that are 2 grids away
            distToGhost = [self.distancer.getDistance(successor.getAgentPosition(self.index), pos) \
                            for pos in opGhostPos]
            minDisToGhost = min(distToGhost)

            if True in (ele <= 4 for ele in distToGhost):
                features['avoidGhost'] = 1 / (min(distToGhost)+1)
                #TODO predict dead end here
                self.deadEnd = util.Counter()
                # 1 -> Dead End; 0-> not visited; -1 -> Free End
                self.closed = {myPos}
                # self.closed.append(myPos)
                features['deadEnd'] = max(self.findDeadEnd(successor, 3),0)
                #print(features['deadEnd'])
                # if action == Directions.STOP: features['stop'] = 1
        else:
            features['avoidGhost'] = 0


        agentState = gameState.getAgentState(self.index)

        foodLeft = len(self.getFood(gameState).asList())

        if (agentState.numCarrying >= 3 and minDisToGhost < 8) or foodLeft <= 2:
            features['goBack'] = -self.getMazeDistance(self.start,myPos)
        else:
            features['goBack'] = 0

        # if agentState.numCarrying >= 5:
        #     walls = gameState.getWalls()
        #     middle = len(walls[0]) // 2
        #     for i in range(len(walls)):
        #     features['goBack'] =

        print(features)
        return features

    def getOffensiveWeights(self, gameState, action):
        if self.index == self.indices[0]:
            return {'successorScore': 10, 'distanceToFood': -1, 'avoidGhost': -180, 'goBack': 2, 'deadEnd': -250}
        else:
            return {'successorScore': 10, 'distanceToFood': -1, 'avoidGhost': -180, 'goBack': 2, 'deadEnd': -250}

    def findDeadEnd(self, gameState, itr = 3):
        
        myPos = gameState.getAgentPosition(self.index)
        #Base Cases
        # if self.deadEnd[myPos] != 0:
        #     return self.deadEnd.get(myPos)
        
        # self.closed.append(myPos)
        self.closed.add(myPos)
        if self.deadEnd[myPos] != 0:
            return self.deadEnd[myPos]
        if itr == 0:
            self.deadEnd[myPos] = -1
            return -1
        legalActions = gameState.getLegalActions(self.index)
        allSuccessorStates = [gameState.generateSuccessor(self.index, action) for action in legalActions]
        forwardStates = [state for state in allSuccessorStates if state.getAgentPosition(self.index) not in self.closed]
        if len(legalActions) <= 2:
            self.deadEnd[myPos] = 1
            return 1
        if len(forwardStates) == 0:
            self.deadEnd[myPos] = -1
            return -1

        # print("myPos ",myPos)
        # print("monster ",self.opGhostPos)
        # # print("closed", self.closed)
        # print("legal actions:",legalActions)
        # # print()
        # # print("forward: ",forwardStates)
        # # print()
        # print(self.deadEnd)
        # print()
        minSucc = min([self.findDeadEnd(state, itr-1) for state in forwardStates])
        # if minSucc >= 1:
        #     self.deadEnd[myPos] = minSucc + 1
        # else:
        self.deadEnd[myPos] = minSucc
        return self.deadEnd[myPos]


    def isDeadEnd(self,state):
        return len(state.getLegalActions()) <= 2
    """****************"""
    """DefensiveMethods"""
    """****************"""
    def getDefensiveFeatures(self, gameState, action):
        features = util.Counter()
        successor = self.getSuccessor(gameState, action)

        myState = successor.getAgentState(self.index)
        myPos = myState.getPosition()

        # Computes whether we're on defense (1) or offense (0)

        # Computes distance to invaders we can see
        enemies = [successor.getAgentState(i) for i in self.getOpponents(successor)]
        invaders = [a for a in enemies if a.isPacman and a.getPosition() != None]
        features['numInvaders'] = len(invaders)


        if len(invaders) > 0:
            dists = [self.getMazeDistance(myPos, a.getPosition()) for a in invaders]
            features['invaderDistance'] = min(dists)
        else:
            return features
#Closer agent chase, further agent detour
        invaderIndex = 0

        selfToNearestInvader = self.getMazeDistance(myPos,invaders[invaderIndex].getPosition())
        friendIndex = [i for i in self.indices if i != self.index][0]
        friendToNearestInvader = self.getMazeDistance(successor.getAgentPosition(friendIndex),invaders[invaderIndex].getPosition())
        
        if selfToNearestInvader <= 1:
            features['detour'] = 0
        elif friendToNearestInvader <= selfToNearestInvader:
            invaderPos = invaders[invaderIndex].getPosition()
            centerX = self.getCenterX(gameState)
            goal = self.getGoal(gameState, centerX, invaderPos[1]) #avoid not grid
            disToGoal = self.getMazeDistance(goal, invaderPos)
            features['detour'] = -disToGoal
        else:
            features['detour'] = 0

        if action == Directions.STOP: features['stop'] = 1
        rev = Directions.REVERSE[gameState.getAgentState(self.index).configuration.direction]
        if action == rev: features['reverse'] = 1




        opGhostPos = self.getNonScaredGhostPos(gameState)
        if len(opGhostPos) > 0:
            #if we have ghosts that are 2 grids away
            distToGhost = [self.distancer.getDistance(successor.getAgentPosition(self.index), pos) \
                            for pos in opGhostPos]

            if True in (ele <= 4 for ele in distToGhost):
                features['avoidGhost'] = 1 / (min(distToGhost)+1)
                #TODO predict dead end here
                self.deadEnd = util.Counter()
                # 1 -> Dead End; 0-> not visited; -1 -> Free End
                self.closed = {myPos}
                # self.closed.append(myPos)
                features['deadEnd'] = max(self.findDeadEnd(successor, 3),0)
                print(features['deadEnd'])

        return features

    def getDefensiveWeights(self, gameState, action):
        return {'numInvaders': -1000, 'invaderDistance': -10, 'stop': -100, 'reverse': -2, 'detour': 100, 'avoidGhost': -100}

    def getCenterX(self, gameState):
        if self.red:
            return int(gameState.getWalls().width/2) - 1
        else:
            return int(gameState.getWalls().width/2) + 1

    def getGoal(self, gameState, centerX, y):
        walls = gameState.getWalls()
        allPossibleGoals = [(centerX,v) for v in range(walls.height) if not walls[centerX][v]]
        allPossibleGoals.sort(key = lambda pos: abs(pos[1] - y))
        return allPossibleGoals[0]