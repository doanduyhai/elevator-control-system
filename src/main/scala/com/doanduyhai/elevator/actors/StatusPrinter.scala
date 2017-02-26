package com.doanduyhai.elevator.actors

import java.io.PrintStream

import scala.collection.immutable.{Queue, ListMap}

trait StatusPrinter {

  val FLOOR_PATTERN = " _ "

  def printStream:PrintStream

  def printDequeueOperation(elevatorId:Int, move: Move): Unit = {
    printStream.println(s"\n    Send queued pickup order: $move to elevator $elevatorId\n")
  }

  def printOrderQueue(queue: Queue[Pickup]): Unit = {
    printStream.println(s"\n    Control system orders queue: ${queue.map(_.direction).mkString("[", ",", "]")}.\n")
  }

  def printPickup(pickup: Pickup): Unit = {
    printStream.println(s"\n    Order received: $pickup\n")
  }

  def printElevatorsStatus(elevatorsStatus:Map[Int, (ElevatorStatus, Option[Pickup])]): Unit = {

    def formatDisplay(line: (Int, ElevatorStatus, Option[Pickup])): String = {

      def atFloor(floor:Int) = s"|$floor|"
      def targetFloor(floor:Int) = s"{$floor}"
      def up(from:Int, to:Int) = (FLOOR_PATTERN * from) + s"|$from>" + (FLOOR_PATTERN * (to - from - 1)) + targetFloor(to)
      def down(from:Int, to:Int) = (FLOOR_PATTERN * to) + targetFloor(to) + (FLOOR_PATTERN * (from - to - 1)) + s"<$from|"

      def formatMovingElevator(currentFloor:Int, targetFloor:Int):String = {
        if(currentFloor < targetFloor)
          up(currentFloor, targetFloor)
        else
          down(currentFloor, targetFloor)
      }

      val scheduledOrder = line._3.
        map{case Pickup(move) => s"${move.currentFloor}->${move.targetFloor}"}.
        getOrElse(" " * 4)
      val elevatorWithScheduledOrder = s"${line._1}[$scheduledOrder]: "
      val status = line._2 match {
        case AtFloor(floor) => (FLOOR_PATTERN * floor) + atFloor(floor)
        case Move(current,target) => formatMovingElevator(current, target)
      }
      elevatorWithScheduledOrder + status
    }

    printStream.println("-" * 50)
    ListMap(elevatorsStatus.toSeq.sortBy(_._1):_*).
      map{case(id, (status,scheduledOrder)) => formatDisplay((id,status,scheduledOrder))}.
      foreach(x => printStream.println(x))
    printStream.println("-" * 50)
    printStream.println("")
  }
}
