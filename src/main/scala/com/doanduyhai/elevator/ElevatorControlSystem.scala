package com.doanduyhai.elevator

import com.doanduyhai.elevator.actors.{Move, ElevatorStatus}

trait ElevatorControlSystem {
  def status(): Map[Int, ElevatorStatus]
  def update(elevatorId:Int, direction: ElevatorStatus)
  def pickup(move: Move)
  def step()
}

