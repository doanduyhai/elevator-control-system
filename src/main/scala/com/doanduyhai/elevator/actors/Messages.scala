package com.doanduyhai.elevator.actors

sealed trait Messages

// Elevator messages
case class Pickup(direction: Move) extends Messages
case class UpdateStatus(elevatorId: Int, status: ElevatorStatus) extends Messages
case class UpdateScheduledOrder(elevatorId: Int, order: Option[Pickup]) extends Messages

//Control system messages
case object GetElevatorStatus extends Messages
case object GetQueueStatus extends Messages
case object ProcessOrderQueue

case object ExecuteSimulation extends Messages
case class ElevatorStatusReply(statuses: Map[Int, (ElevatorStatus, Option[Pickup])]) extends Messages
