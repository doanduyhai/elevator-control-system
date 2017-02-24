package com.doanduyhai.elevator.actors

import akka.actor.{ActorRef, ActorLogging, Actor}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


sealed trait ElevatorStatus {
  def nextStep: ElevatorStatus
  def isAvailable: Boolean
}

case class Move(currentFloor:Int, targetFloor:Int) extends ElevatorStatus {
  if(currentFloor < 0 || targetFloor < 0) throw new IllegalArgumentException("Invalid negative floor")

  override def nextStep: ElevatorStatus = {
    if(Math.abs(targetFloor - currentFloor) == 1) {
      Still(targetFloor)
    } else if(targetFloor > currentFloor) {
      Move(currentFloor+1, targetFloor)
    }  else if(targetFloor == currentFloor) {
      Still(targetFloor)
    } else {
      Move(currentFloor - 1, targetFloor)
    }
  }

  override def isAvailable = false
}

case class Still(floor: Int) extends ElevatorStatus {
  override def nextStep: ElevatorStatus = Still(floor)
  override def isAvailable = true
}


class ElevatorActor(val elevatorId: Int, val movingSpeed: FiniteDuration,
                    private var elevatorStatus: ElevatorStatus, private var scheduledOrder: Option[Pickup]=None)
  extends Actor with ActorLogging {

  def receive: Receive = {

    case Pickup(move) => elevatorStatus match {
      case Still(currentFloor) => {
        if (currentFloor != move.currentFloor) {
          scheduleAnOrder(sender, move)
          this.elevatorStatus = Move(currentFloor, move.currentFloor)
          val nextStep = elevatorStatus.nextStep
          scheduleNextMove(nextStep)
          log.debug(s"After receiving Pickup($move), add scheduledOrder $scheduledOrder and set state = $elevatorStatus, nextStep = $nextStep")
        } else {
          this.elevatorStatus = Move(move.currentFloor, move.targetFloor)
          val nextStep = elevatorStatus.nextStep
          scheduleNextMove(nextStep)
          log.debug(s"After receiving Pickup($move), set state = $elevatorStatus, nextStep = $nextStep")
        }
        sendStatusToControlSystem(sender)
      }
      case move @ Move(_,_) => scheduledOrder match {
        case Some(scheduledPickup) =>
          log.error(s"Cannot accept Pickup($move) because the elevator is moving right now and a pickup $scheduledPickup is already scheduled")
        case None =>
          scheduleAnOrder(sender, move)
          log.info(s"No pending order, save the pickup order for later")
          scheduleNextMove(move.nextStep)
      }
    }

    case enRoute @ EnRoute(sender, state) => {
      state match {
        case Move(_,_) =>
          this.elevatorStatus = state
          scheduleNextMove(state.nextStep)
          sendStatusToControlSystem(sender)
        case still @ Still(currentFloor) => scheduledOrder match {
          case Some(Pickup(move)) => {
            if (currentFloor != move.currentFloor) {
              scheduleNextMove(Move(currentFloor, move.currentFloor))
            } else {
              removeOrderSchedule(sender)
              scheduleNextMove(move)
            }
            this.elevatorStatus = state
            sendStatusToControlSystem(sender)
          }
          case None =>
            log.info(s"Elevator has reached destination floor : $currentFloor, state = $state")
            this.elevatorStatus = state
            sendStatusToControlSystem(sender)
        }
      }
      log.debug(s"After receiving EnRoute($state), set state = $state, scheduledOrder = $scheduledOrder, nextStep = ${state.nextStep}")
    }
  }

  def sendStatusToControlSystem(sender: ActorRef): Unit = {
    sender ! UpdateStatus(this.elevatorId, this.elevatorStatus)
  }

  def scheduleNextMove(nextStep: ElevatorStatus): Unit = {
    context.system.scheduler.scheduleOnce(movingSpeed, self, EnRoute(sender, nextStep))
  }

  def removeOrderSchedule(sender: ActorRef): Unit = {
    scheduledOrder = None
    sender ! HasScheduledOrder(elevatorId, false)
  }

  def scheduleAnOrder(sender: ActorRef, move: Move): Unit = {
    scheduledOrder = Some(Pickup(move))
    sender ! HasScheduledOrder(elevatorId, true)
  }
}


