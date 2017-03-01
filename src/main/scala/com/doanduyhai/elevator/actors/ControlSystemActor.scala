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


  def availableElevator(pickupSourceFloor:Int):Option[Int] = elevatorsStatus.
    //First check all free elevators
    filter{case(id,(status,scheduledOrder)) => (!status.isMoving) && scheduledOrder.isEmpty}.
    toSeq.
    //Sort AtFloor(x) elevator by closest distance between x and pickupSourceFloor
    sortBy{case(_,(status,_)) => Math.abs(status.targetFloor - pickupSourceFloor)}.
    map{case(id,_) => id}.headOption match {
    case head @ Some(_) => head
    case None => elevatorsStatus.
      //Then check elevators who have no scheduled order
      filter{case(_,(_,scheduledOrder)) => scheduledOrder.isEmpty}.
      toSeq.
      sortBy{case(_,(status,_)) => { computePathLength(pickupSourceFloor, status)
    }}.
      map{case(id,_) => id}.
      headOption
  }


  /**
    * Sort Move(fromFloor,toFloor) elevator by shortest path
    * Path length is computed as |toFloor - fromFloor| + |pickupSourceFloor - toFloor|
    * For example:
    *   - Move(1,5), Pickup(Move(6,1)) = |1 - 5| + (|6 - 5| + 1) = |-4| + |1| + 1 = 6
    *   - Move(1,5), Pickup(Move(2,7)) = |1 - 5| + (|5 - 2| + 1) = |-4| + |3| + 1 = 8
    */
  def computePathLength(pickupSourceFloor: Int, status: ElevatorStatus): Int = {
    val currentPathLength: Int = Math.abs(status.targetFloor - status.currentFloor)
    val pathLengthToPickupSourceFloor: Int =
      if (pickupSourceFloor == status.targetFloor) 0
      else

      /**
        * + 1 to the pathLength because we need one extra stop
        * from status.targetFloor before moving to pickupSourceFloor
        *
        */
        (Math.abs(pickupSourceFloor - status.targetFloor) + 1)
    currentPathLength + pathLengthToPickupSourceFloor
  }

  def receive: Receive = {
    case UpdateStatus(elevatorId, status, scheduledOrder) => {
      elevatorsStatus += ((elevatorId, (status, scheduledOrder)))
      elevatorById = elevatorById.updated(elevatorId, sender())

      if(elevatorsStatus.size >= expectedElevatorCount) {
        printOrderQueue(this.orderQueue)
        printElevatorsStatus(elevatorsStatus)

        if(this.orderQueue.size > 0) {
          val (pickup, _) = this.orderQueue.dequeue
          availableElevator(pickup.direction.currentFloor) match {
            case Some(freeElevator) => dequeueAnOrder(freeElevator)
            case None => //Do nothing
          }
        }
      }
    }

    case pickupOrder @ Pickup(_) => {
      if(elevatorsStatus.size >= expectedElevatorCount) {
        printPickup(pickupOrder)
        availableElevator(pickupOrder.direction.currentFloor) match {
          case Some(freeElevator) =>
            elevatorById(freeElevator) ! pickupOrder
            proactivelyUpdateElevatorStatus(freeElevator, pickupOrder)
          case None => enqueueAnOrder(pickupOrder)
        }
      } else {
        enqueueAnOrder(pickupOrder)
      }
    }

    case unknown @ _ => log.error(s"ControlSystemActor receiving unknown message $unknown")
  }


  def enqueueAnOrder(pickupOrder: Pickup): Unit = {
    if (this.orderQueue.size < maxQueueSize) {
      this.orderQueue = this.orderQueue.enqueue(pickupOrder)
    } else {
      log.error(s"Cannot enqueue order $pickupOrder because the queue is full")
    }
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
    else {
      if(pickup.direction.currentFloor != status.currentFloor)
        elevatorsStatus += ((freeElevator, (Move(status.currentFloor, pickup.direction.currentFloor), Some(pickup))))
      else
        elevatorsStatus += ((freeElevator, (pickup.direction, None)))

    }
  }
}
