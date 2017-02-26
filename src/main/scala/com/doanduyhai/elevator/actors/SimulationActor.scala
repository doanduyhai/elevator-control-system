package com.doanduyhai.elevator.actors

import java.io.PrintStream

import akka.actor.{ActorRef, Actor, ActorLogging}
import scala.collection.immutable.{Queue, ListMap}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class SimulationActor(val root: ActorRef,
                      val controlSystem: ActorRef,
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
    case ElevatorsStatuses(map) =>
//      log.info(s"___ Receiving map $map")
      elevatorsStatus = map
      elevatorsStatusInitialized = true
      printElevatorsStatus
      sender ! ElevatorsStatusesAck
    case StartSimulation =>
      startSimulation
    case RunSimulation =>
      scheduleRunSimulation
    case OrderQueue(queue) =>
      printOrderQueue(queue)
      sender ! OrderQueueAck
    case unknown @ _ => log.error(s"SimulationActor receiving unknown message $unknown")
  }

  def startSimulation: Any = {
    controlSystem ! StartSimulation
    scheduleRunSimulation
  }

  def hasActiveElevator = elevatorsStatus.
    filter{case(_,(status,scheduledOrder)) => (status.isMoving || scheduledOrder.isDefined)}.
    size > 0


  def scheduleRunSimulation = {
    if (hasActiveElevator || !elevatorsStatusInitialized) {
      context.system.scheduler.scheduleOnce(simulationTimeStep, self, RunSimulation)
    }
    else {
      log.info("SIMULATION FINISHED")
      root ! SimulationFinished
    }

  }

  def printOrderQueue(queue: Queue[Pickup]): Unit = {
    printStream.println(s"\n    Control system orders queue: ${queue.map(_.direction).mkString("[", ",", "]")}.\n")
  }

  def printPickup(pickup: Pickup): Unit = {
    printStream.println(s"\n    Order received: $pickup\n")
  }

  def printElevatorsStatus: Unit = {

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
