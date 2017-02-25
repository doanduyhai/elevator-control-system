package com.doanduyhai.elevator.actors

import akka.actor.{ActorRef, Actor, ActorLogging}

import scala.collection.immutable.{Queue}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ControlSystemActor(val elevators:Map[Int, (ActorRef,ElevatorStatus,Option[Pickup])],
                         val simulationActor:ActorRef,
                         private var orderQueue:Queue[Pickup] = Queue.empty[Pickup],
                         val orderQueueProcessingFrequency: FiniteDuration = 30.millisecond,
                         val maxQueueSize:Int = 10) extends Actor with ActorLogging {

  private var elevatorsStatus: Map[Int, ElevatorStatus] = elevators
    .map{case(id,(actor,status, _)) => (id, status)}

  private var elevatorsScheduledOrder: Map[Int, Option[Pickup]] = elevators
    .map{case(id,(actor,status, option)) => (id, option)}

  val elevatorById:Map[Int, ActorRef] = elevators.map{case(id, (actor,_,_)) => (id, actor)}


  def availableElevator:Option[Int] = elevatorsStatus.
    filter{case(id,status) => (!status.isMoving) && elevatorsScheduledOrder(id).isEmpty}.
    map{case(id,_) => id}.headOption match {
    case head @ Some(_) => head                 //First check all free elevators
    case None => elevatorsScheduledOrder.       //Then check elevators who have no scheduled order
      filter{case(_,option) => option.isEmpty}.
      map{case(id,_) => id}.
      headOption
  }

  def receive: Receive = {
    case UpdateStatus(elevatorId, status) => {
      elevatorsStatus += ((elevatorId, status))
      if(this.orderQueue.size > 0 && availableElevator.isDefined) scheduleADequeuOperation
    }
    case UpdateScheduledOrder(elevatorId, option) => {
      elevatorsScheduledOrder += ((elevatorId, option))
      if(this.orderQueue.size > 0 && availableElevator.isDefined) scheduleADequeuOperation
    }
    case GetElevatorStatus => simulationActor ! ElevatorStatusReply(elevatorsStatus.map{case(key, status) => (key, (status,elevatorsScheduledOrder(key)))})
    case GetQueueStatus => simulationActor ! this.orderQueue

    case pickupOrder @ Pickup(_) => {
      availableElevator match  {
        case Some(elevatorId) => elevatorById(elevatorId) ! pickupOrder
        case None =>
          if(this.orderQueue.size < maxQueueSize) {
            this.orderQueue = this.orderQueue.enqueue(pickupOrder)
            scheduleADequeuOperation
          } else {
            log.error(s"Cannot enqueue order $pickupOrder because the queue is full")
          }
      }
    }

    case ProcessOrderQueue => orderQueue.dequeueOption match {
      case Some((pickup, tail)) => availableElevator match {
        case Some(elevatorId) =>
          orderQueue = tail //Update the queue
          elevatorById(elevatorId) ! pickup
        case None =>
          log.info("Cannot process order queue yet, all elevators are busy")
          scheduleADequeuOperation
      }
      case None => log.info("Order queue is empty")
    }

    case ExecuteSimulation => elevatorById.values.foreach( _ ! ExecuteSimulation)
  }

  def scheduleADequeuOperation: Unit = {
    context.system.scheduler.scheduleOnce(orderQueueProcessingFrequency, self, ProcessOrderQueue)
  }
}
