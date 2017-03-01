package com.doanduyhai.elevator.actors

import akka.actor.{ActorRef, ActorLogging, Actor}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global



sealed trait ElevatorStatus {
  def nextStep: ElevatorStatus
  def currentFloor: Int
  def targetFloor: Int
  def isMoving: Boolean
}
case class Move(currentFloor:Int, targetFloor:Int) extends ElevatorStatus {
  if(currentFloor < 0 || targetFloor < 0) throw new IllegalArgumentException("Invalid negative floor")

  override def nextStep: ElevatorStatus = {
    if(Math.abs(targetFloor - currentFloor) == 1) {
      AtFloor(targetFloor)
    } else if(targetFloor > currentFloor) {
      Move(currentFloor+1, targetFloor)
    }  else if(targetFloor == currentFloor) {
      AtFloor(targetFloor)
    } else {
      Move(currentFloor - 1, targetFloor)
    }
  }
  override def isMoving = true
}

case class AtFloor(floor: Int) extends ElevatorStatus {
  override def nextStep: ElevatorStatus = AtFloor(floor)
  override def currentFloor = floor
  override def targetFloor = floor
  override def isMoving = false
}

private case class EnRoute(status: ElevatorStatus)

class ElevatorActor(val elevatorId: Int, controlSystem: ActorRef, private var elevatorStatus: ElevatorStatus,
                    val movingSpeed: FiniteDuration = 10.millisecond, private var scheduledOrder: Option[Pickup]=None)
  extends Actor with ActorLogging {

  var simulationStarted = false
  var simulationOngoing = false

  def receive: Receive = {

    case p @ Pickup(pickup) => {
      if(!simulationStarted) sendStatusToControlSystem
      elevatorStatus match {
        case AtFloor(currentFloor) => {
          if (currentFloor != pickup.currentFloor) {
            this.scheduledOrder = Some(p)
            this.elevatorStatus = Move(currentFloor, pickup.currentFloor)
          } else {
            this.elevatorStatus = Move(pickup.currentFloor, pickup.targetFloor)
          }
          sendStatusToControlSystem
          if(!simulationOngoing) scheduleNextMove(this.elevatorStatus.nextStep)
        }
        case currentMove @ Move(_,_) => scheduledOrder match {
          case Some(scheduledPickup) =>
            log.error(s"Cannot accept $p because the elevator is moving right now and a pickup $scheduledPickup is already scheduled")
          case None =>
            log.info(s"No pending order, save the pickup order for later")
            this.scheduledOrder = Some(p)
            sendStatusToControlSystem
            if(!simulationOngoing) scheduleNextMove(currentMove.nextStep)
        }
      }
    }

    case enRoute @ EnRoute(state) => {
      this.elevatorStatus = state
      sendStatusToControlSystem
      state match {
        case Move(_,_) =>
          scheduleNextMove(this.elevatorStatus.nextStep)
        case AtFloor(currentFloor) =>
          computeNextStepFromAtFloor(currentFloor, s"Elevator $elevatorId has reached destination floor : $currentFloor, state = $state")
      }
    }

    case StartSimulation => {
      this.simulationStarted = true
      //The first status sent to control system acts as "registration" of this elevator to the control system
      sendStatusToControlSystem
      this.elevatorStatus match {
        case Move(_,_) =>
          scheduleNextMove(this.elevatorStatus.nextStep)
        case AtFloor(currentFloor)  => computeNextStepFromAtFloor(currentFloor, "No order to execute")
      }
    }
    case unknown @ _ => log.error(s"ElevatorActor receiving unknown message $unknown")
  }

  def computeNextStepFromAtFloor(currentFloor: Int, logMsg:String): Unit = scheduledOrder match {
    case Some(Pickup(scheduledPickup)) => {
      if (currentFloor != scheduledPickup.currentFloor) {
        scheduleNextMove(Move(currentFloor, scheduledPickup.currentFloor))
      } else {
        scheduleNextMove(scheduledPickup)
        this.scheduledOrder = None
      }
    }
    case None =>
      this.simulationOngoing = false
      log.info(logMsg)
  }


  def sendStatusToControlSystem: Unit = {
    log.debug(s"--------- Send UpdateStatus($elevatorId, $elevatorStatus, $scheduledOrder) to control system, [${Thread.currentThread().getId}]")
    controlSystem ! UpdateStatus(this.elevatorId, this.elevatorStatus, this.scheduledOrder)
  }

  def scheduleNextMove(nextStep: ElevatorStatus): Unit = {
    this.simulationOngoing = true
    log.debug(s"**** Schedule Next Move EnRoute($nextStep), [${Thread.currentThread().getId}]")
    context.system.scheduler.scheduleOnce(movingSpeed, self, EnRoute(nextStep))
  }

  def savePickupOrder(pickup: Pickup): Unit = {
    this.scheduledOrder = Some(pickup)
    sendStatusToControlSystem
  }
}


