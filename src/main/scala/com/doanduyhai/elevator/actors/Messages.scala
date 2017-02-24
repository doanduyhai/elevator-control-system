package com.doanduyhai.elevator.actors

sealed trait Messages

case class EnRoute(status: ElevatorStatus) extends Messages
case class Pickup(direction: Move) extends Messages
case class UpdateStatus(elevatorId: Int, status: ElevatorStatus) extends Messages
case class UpdateScheduledOrder(elevatorId: Int, order: Option[Pickup]) extends Messages
case object GetStatus extends Messages
case object ProcessOrderQueue extends Messages
case object ExecuteSimulation extends Messages
