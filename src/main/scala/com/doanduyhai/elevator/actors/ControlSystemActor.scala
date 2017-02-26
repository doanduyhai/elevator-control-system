package com.doanduyhai.elevator.actors

import akka.actor.{ActorRef, Actor, ActorLogging}

import scala.collection.immutable.{Queue}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ControlSystemActor(val elevators:Map[Int, (ActorRef,ElevatorStatus,Option[Pickup])],
                         simulationActor: => ActorRef,
                         private var orderQueue:Queue[Pickup] = Queue.empty[Pickup],
                         val orderQueueProcessingFrequency: FiniteDuration = 30.millisecond,
                         val maxQueueSize:Int = 10) extends Actor with ActorLogging {

  private var elevatorsStatus: Map[Int, (ElevatorStatus, Option[Pickup])] = elevators
    .map{case(id,(actor,status,scheduledOrder)) => (id, (status,scheduledOrder))}

  val elevatorById:Map[Int, ActorRef] = elevators.map{case(id, (actor,_,_)) => (id, actor)}


  def availableElevator:Option[Int] = elevatorsStatus.
    filter{case(id,(status,scheduledOrder)) => (!status.isMoving) && scheduledOrder.isEmpty}.
    map{case(id,_) => id}.headOption match {
    case head @ Some(_) => head                 //First check all free elevators
    case None => elevatorsStatus.               //Then check elevators who have no scheduled order
      filter{case(_,(_,scheduledOrder)) => scheduledOrder.isEmpty}.
      map{case(id,_) => id}.
      headOption
  }

  def receive: Receive = {
    case UpdateStatus(elevatorId, status, scheduledOrder) => {
      elevatorsStatus += ((elevatorId, (status, scheduledOrder)))
      simulationActor ! ElevatorsStatuses(elevatorsStatus)
      if(this.orderQueue.size > 0 && availableElevator.isDefined) scheduleADequeuOperation
    }

    case GetElevatorStatus => simulationActor ! ElevatorsStatuses(elevatorsStatus)
    case GetQueueStatus => simulationActor ! this.orderQueue

    case pickupOrder @ Pickup(_) => {
      availableElevator match  {
        case Some(elevatorId) => elevatorById(elevatorId) ! pickupOrder
        case None => {
          if(this.orderQueue.size < maxQueueSize) {
            this.orderQueue = this.orderQueue.enqueue(pickupOrder)
            scheduleADequeuOperation
          } else {
            log.error(s"Cannot enqueue order $pickupOrder because the queue is full")
          }
        }
      }
    }

    case ProcessOrderQueue => maybeProcessOrder

    case StartSimulation =>
      if(!orderQueue.isEmpty) simulationActor ! OrderQueue(orderQueue)
      elevatorById.values.foreach( _ ! StartSimulation)
      maybeProcessOrder


    case ElevatorsStatusesAck =>
    case OrderQueueAck =>
    case unknown @ _ => log.error(s"ControlSystemActor receiving unknown message $unknown")
  }

  def maybeProcessOrder: Unit = {
    orderQueue.dequeueOption match {
      case Some((pickup, tail)) => availableElevator match {
        case Some(elevatorId) =>
          orderQueue = tail //Update the queue
          log.info(s"Send order $pickup to elevator $elevatorId")
          elevatorById(elevatorId) ! pickup
        case None =>
          log.debug("Cannot process order queue yet, all elevators are busy")
          scheduleADequeuOperation
      }
      case None => {
        log.debug("Order queue is empty")
      }
    }
  }

  def scheduleADequeuOperation: Unit = {
    context.system.scheduler.scheduleOnce(orderQueueProcessingFrequency, self, ProcessOrderQueue)
  }
}
