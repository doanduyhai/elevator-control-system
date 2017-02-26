package com.doanduyhai.elevator.actors

import java.io.PrintStream

import akka.actor.{ActorRef, Actor, ActorLogging}

import scala.collection.immutable.{Queue}

class ControlSystemActor(val expectedElevatorCount: Int,
                         private[actors] var orderQueue:Queue[Pickup] = Queue.empty[Pickup],
                         val maxQueueSize:Int = 10,
                         val printStream: PrintStream = System.out) extends Actor with ActorLogging with StatusPrinter {

  private[actors] var elevatorsStatus: Map[Int, (ElevatorStatus, Option[Pickup])] = Map()

  private[actors] var elevatorById:Map[Int, ActorRef] = Map()


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
      elevatorById = elevatorById.updated(elevatorId, sender())

      if(elevatorsStatus.size >= expectedElevatorCount) {
        printOrderQueue(this.orderQueue)
        printElevatorsStatus(elevatorsStatus)

        if(this.orderQueue.size > 0) {
          availableElevator match {
            case Some(freeElevator) => dequeueAnOrder(freeElevator)
            case None => //Do nothing
          }
        }
      }
    }

    case pickupOrder @ Pickup(_) => {
      printPickup(pickupOrder)
      availableElevator match {
        case Some(freeElevator) =>
          elevatorById(freeElevator) ! pickupOrder
          proactivelyUpdateElevatorStatus(freeElevator, pickupOrder)
        case None =>
          if(this.orderQueue.size < maxQueueSize) {
            this.orderQueue = this.orderQueue.enqueue(pickupOrder)
          } else {
            log.error(s"Cannot enqueue order $pickupOrder because the queue is full")
          }
      }
    }

    case unknown @ _ => log.error(s"ControlSystemActor receiving unknown message $unknown")
  }


  def dequeueAnOrder(freeElevator: Int): Unit = {
    val (pickup, tail) = this.orderQueue.dequeue
    elevatorById(freeElevator) ! pickup
    this.orderQueue = tail
    proactivelyUpdateElevatorStatus(freeElevator, pickup)
    printDequeueOperation(freeElevator, pickup.direction)
  }

  def proactivelyUpdateElevatorStatus(freeElevator: Int, pickup: Pickup): Unit = {
    //Pro-actively update elevator status in map before getting the update from the elevator itself
    val (status, _) = elevatorsStatus(freeElevator)
    if (status.isMoving)
      elevatorsStatus += ((freeElevator, (status, Some(pickup))))
    else
      elevatorsStatus += ((freeElevator, (pickup.direction, None)))
  }
}
