package com.doanduyhai.elevator.actors

import scala.collection.immutable.Queue

sealed trait Messages

// Elevator messages
case class Pickup(direction: Move) extends Messages
case class UpdateStatus(elevatorId: Int, status: ElevatorStatus, scheduledOrder: Option[Pickup]) extends Messages

//Control system messages
case object GetElevatorStatus extends Messages
case object ElevatorsStatusesAck extends Messages
case object GetQueueStatus extends Messages
case object ProcessOrderQueue

//Simulation messages
case object StartSimulation extends Messages
case object RunSimulation extends Messages
case object SimulationFinished extends Messages
case class ElevatorsStatuses(statuses: Map[Int, (ElevatorStatus, Option[Pickup])]) extends Messages
case class OrderQueue(queue: Queue[Pickup]) extends Messages
case object OrderQueueAck extends Messages
