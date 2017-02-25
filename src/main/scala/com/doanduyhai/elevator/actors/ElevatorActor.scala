package com.doanduyhai.elevator.actors

import akka.actor.{ActorRef, ActorLogging, Actor}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global



sealed trait ElevatorStatus {
  def nextStep: ElevatorStatus
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
  override def isMoving = false
}

private case class EnRoute(status: ElevatorStatus)

class ElevatorActor(val elevatorId: Int, val controlSystem:ActorRef, private var elevatorStatus: ElevatorStatus,
                    val movingSpeed: FiniteDuration = 10.millisecond, private var scheduledOrder: Option[Pickup]=None)
  extends Actor with ActorLogging {

  def receive: Receive = {

    case p @ Pickup(pickup) => elevatorStatus match {
      case AtFloor(currentFloor) => {
        if (currentFloor != pickup.currentFloor) {
          savePickupOrder(p)
          this.elevatorStatus = Move(currentFloor, pickup.currentFloor)
        } else {
          this.elevatorStatus = Move(pickup.currentFloor, pickup.targetFloor)
        }
        scheduleNextMove(this.elevatorStatus.nextStep)
        sendStatusToControlSystem()
      }
      case currentMove @ Move(_,_) => scheduledOrder match {
        case Some(scheduledPickup) =>
          log.error(s"Cannot accept $p because the elevator is moving right now and a pickup $scheduledPickup is already scheduled")
        case None =>
          log.info(s"No pending order, save the pickup order for later")
          savePickupOrder(p)
          scheduleNextMove(currentMove.nextStep)
      }
    }

    case enRoute @ EnRoute(state) => {
      this.elevatorStatus = state
      state match {
        case Move(_,_) =>
          scheduleNextMove(this.elevatorStatus.nextStep)
        case AtFloor(currentFloor) => computeNextStepFromAtFloor(currentFloor,
          s"Elevator has reached destination floor : $currentFloor, state = $state")
      }
      sendStatusToControlSystem()
    }

    case ExecuteSimulation => this.elevatorStatus match {
      case Move(_,_) => scheduleNextMove(this.elevatorStatus.nextStep)
      case AtFloor(currentFloor)  => computeNextStepFromAtFloor(currentFloor,"No order to execute")
    }

  }

  def computeNextStepFromAtFloor(currentFloor: Int, logMsg:String): Unit = {
    scheduledOrder match {
      case Some(Pickup(scheduledPickup)) => {
        if (currentFloor != scheduledPickup.currentFloor) {
          scheduleNextMove(Move(currentFloor, scheduledPickup.currentFloor))
        } else {
          removeScheduledOrder()
          scheduleNextMove(scheduledPickup)
        }
      }
      case None =>
        log.info(logMsg)
    }
  }

  def sendStatusToControlSystem(): Unit = {
    log.debug(s"--------- Send $elevatorStatus to control system")
    controlSystem ! UpdateStatus(this.elevatorId, this.elevatorStatus)
  }

  def scheduleNextMove(nextStep: ElevatorStatus): Unit = {
    context.system.scheduler.scheduleOnce(movingSpeed, self, EnRoute(nextStep))
  }

  def removeScheduledOrder(): Unit = {
    this.scheduledOrder = None
    log.debug(s"--------- Send HasScheduledOrder($elevatorId, None) to control system")
    controlSystem ! UpdateScheduledOrder(elevatorId, None)
  }

  def savePickupOrder(pickup: Pickup): Unit = {
    this.scheduledOrder = Some(pickup)
    log.debug(s"--------- Send HasScheduledOrder($elevatorId, Some($pickup)) to control system")
    controlSystem ! UpdateScheduledOrder(elevatorId, scheduledOrder)
  }
}


