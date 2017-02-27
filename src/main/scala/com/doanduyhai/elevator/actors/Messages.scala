package com.doanduyhai.elevator.actors

sealed trait Messages

case object StartSimulation extends Messages
case class Pickup(direction: Move) extends Messages
case class UpdateStatus(elevatorId: Int, status: ElevatorStatus, scheduledOrder: Option[Pickup]) extends Messages

