package com.doanduyhai.elevator.actors

import java.io.PrintStream

import akka.actor.{ActorRef, Actor, ActorLogging}
import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class SimulationActor(val controlSystem: ActorRef,
                      val simulationTimeStep: FiniteDuration = 1.second,
                      private val printStream: PrintStream = System.out)
  extends Actor with ActorLogging {



  var elevatorsStatusInitialized = false
  var elevatorsStatus:Map[Int, (ElevatorStatus, Option[Pickup])] = Map()
  val FLOOR_PATTERN = " _ "

  def receive: Receive = {
    case pickup @ Pickup(_) =>
      printPickup(pickup)
      controlSystem ! pickup
      getElevatorStatus
    case ElevatorStatusReply(map) =>
      elevatorsStatus = map
      elevatorsStatusInitialized = true
      printElevatorsStatus
      sender ! "OK"
    case ExecuteSimulation => controlSystem ! ExecuteSimulation
    case GetElevatorStatus => getElevatorStatus
  }

  def hasActiveElevator = elevatorsStatus.
    filter{case(_,(status,scheduledOrder)) => (status.isMoving || scheduledOrder.isDefined)}.
    size > 0

  def getElevatorStatus: Unit = {
    if (hasActiveElevator || !elevatorsStatusInitialized) {
      controlSystem ! GetElevatorStatus
      scheduleGetElevatorStatus()
    }
  }

  def scheduleGetElevatorStatus(delay:FiniteDuration=simulationTimeStep): Unit = {
    context.system.scheduler.scheduleOnce(delay, self, GetElevatorStatus)
  }

  def printPickup(pickup: Pickup): Unit = {
    printStream.println(s"\n\t\tOrder received: $pickup \n")
  }

  def printElevatorsStatus: Unit = {

    def formatDisplay(line: (Int, ElevatorStatus, Option[Pickup])): String = {

      def atFloor(floor:Int) = s"|$floor|"
      def targetFloor(floor:Int) = s"{$floor}"
      def up(from:Int, to:Int) = (FLOOR_PATTERN * from) + s"|$from>" + (FLOOR_PATTERN * (to - from)) + targetFloor(to)
      def down(from:Int, to:Int) = (FLOOR_PATTERN * to) + targetFloor(to) + (FLOOR_PATTERN * (from - to)) + s"<$from|"

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

    ListMap(elevatorsStatus.toSeq.sortBy(_._1):_*).
      map{case(id, (status,scheduledOrder)) => formatDisplay((id,status,scheduledOrder))}.
      foreach(x => printStream.println(x))
  }
}
