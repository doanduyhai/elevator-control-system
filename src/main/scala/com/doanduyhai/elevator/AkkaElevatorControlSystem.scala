package com.doanduyhai.elevator

import akka.actor.ActorSystem
import com.doanduyhai.elevator.actors.{Move, ElevatorStatus}

object AkkaElevatorControlSystem extends ElevatorControlSystem{


  def main(input: Array[String]): Unit = {
    val system = ActorSystem("ElevatorControlSystem")

  }


  override def status(): Map[Int, ElevatorStatus] = ???

  override def update(elevatorId: Int, status: ElevatorStatus): Unit = ???

  override def pickup(move: Move): Unit = ???

  override def step(): Unit = ???
}
