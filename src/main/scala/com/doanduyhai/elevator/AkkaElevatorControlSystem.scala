package com.doanduyhai.elevator

import java.io.File

import akka.actor.{ActorRef, Props, ActorSystem}

import com.doanduyhai.elevator.actors._
import com.doanduyhai.elevator.parser.ScenarioParser
import com.doanduyhai.elevator.parser.ScenarioParser.Scenario

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable.Queue


object AkkaElevatorControlSystem {


  def main(input: Array[String]): Unit = {
    printBanner
    if(checkArguments(input)) {
      val scenario = extractArgumentToScenario(input)
      var orderQueue: Queue[Pickup] = Queue()
      var elevators:Map[Int, ActorRef] = Map()
      scenario.orderQueue.foreach(move => {
        orderQueue = orderQueue.enqueue(Pickup(move))
      })
      val scheduledPickup = scenario.timedPickup.map{case (pickup, duration) => (duration, pickup)}.toSeq.sortBy(_._1)

      val system = ActorSystem("ElevatorControlSystem")
      val scheduler = system.scheduler
      registerShutdownHook(system)

      val controlSystem = system.actorOf(Props(new ControlSystemActor(scenario.elevatorStatus.size, orderQueue, scenario.maxOrderQueueSize)), "controlSystem")

      scenario.elevatorStatus.foreach{case (id, (status,scheduledOrder)) => {
        val elevator = system.actorOf(Props(new ElevatorActor(id, controlSystem, status, scenario.timeStep, scheduledOrder)), s"elevator$id")
        elevators += ((id, elevator))
      }}

      elevators.values.foreach(_ ! StartSimulation)

      scheduledPickup.foreach{case (delay, pickup) => {
        scheduler.scheduleOnce(delay){controlSystem ! pickup}
      }}
    }
  }

  def registerShutdownHook(system: ActorSystem): Unit = {
    system.registerOnTermination {
      println("------------- Terminating the Akka Elevator Control System ----------------")
    }

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        system.terminate()
      }
    })
  }

  def extractArgumentToScenario(input: Array[String]): Scenario = {
    val inputScenario = readContentFromFile(input(0))
    new ScenarioParser().parseScenario(inputScenario)
  }

  def readContentFromFile(filePath: String):String =  {
    val file = new File(filePath)
    if(!file.isFile) throw new IllegalArgumentException(s"Cannot access file $filePath")
    val source = scala.io.Source.fromFile(file.getAbsolutePath)
    val lines = try source.getLines mkString "\n" finally source.close()
    lines
  }

  def checkArguments(input: Array[String]): Boolean = {
    Option(input) match {
      case Some(input) =>
        if(input.size != 1) {
          displayUsage
          false
        } else true
      case None => {
        displayUsage
        false
      }
    }

  }

  def displayUsage:Unit = {
    println(
      """You must give a scenario file with absolute path as argument. Ex
        |
        | java -jar akka-elevator-control-system-<version>.jar /tmp/simulation1
        |
        | Example of simulation file format:
        |
        | --------------------
        | timeStep: 1s
        | 1[1->3]:Move(4,0)
        | OrderQueue: Move(5,8)
        | MaxOrderQueueSize: 5
        | Pickup(0,4): 2s
        | Pickup(6,4): 5s
        | ---------------------
        |
        | - timeStep: specify the time delay between each status evaluation.
        |             Format = timeStep: <duration><time-unit>
        |             <duration> should be a positive integer
        |             The supported <time-unit> are second (s) or millisecond (ms)
        |             Ex: timeStep: 20ms
        |
        | - Elevator : specify each elevator status.
        |             Format = <elevator-number>([<scheduledOrder>])?: <current-status>
        |             the <scheduledOrder> format is <from>-><to> where <from> is the souce floor and <to> the destination floor
        |             the <current-status> format can be:
        |
        |               - Move(<from>, <to>)
        |               - AtFloor(<floor>)
        |
        |             Ex:
        |
        |               - 1: Move(0,2) elevator 1 with no scheduled order moving from 0 to 2
        |               - 2[4->2]: Move (3,5) elevator 2 with a scheduled order Move(4,2) moving from 3 to 5
        |               - 3: AtFloor(0) elevator 3 with no scheduled and idle at floor 0
        |               - 4[0-3]: AtFloor(5) elevator 4 with a scheduled order Move(0,3) currently at floor 5
        |
        | - OrderQueue: specify a list of orders to be enqueued in the control system
        |             Format = OrderQueue: <move>,<move>, ...,<move>
        |             <move> format is simply Move(<from>,<to>)
        |             Ex:
        |
        |               - OrderQueue: Move(0,4)
        |               - OrderQueue: Move(1,3), Move(7,9), Move(1,2)
        |
        |
        | - MaxOrderQueueSize: specify the max size of the order queue
        |             Ex:
        |
        |               - MaxOrderQueueSize: 3
        |
        | - scheduled pickup: specify a sequence of pickup ordered, delayed with a given time unit
        |             Format = Pickup(<from>,<to>): <duration><time-unit>
        |             <from> is the source floor
        |             <to> is the target floor
        |             <duration> is a positive integer
        |             <time-unit> are second (s) or millisecond (ms)
        |             Ex:
        |
        |               - Pickup(0,6): 3s  schedule a pickup from 0 to 6 in 3 seconds
        |               - Pickup(5,4): 30ms  schedule a pickup from 5 to 4 in 30 milliseconds
        |
        | There are some sample scenarios you can play with located in folder `sample_scenarios`
        |
      """.stripMargin)
  }
  def printBanner: Unit = {
    println(
      """
        |      .o.       oooo        oooo                  oooooooooooo oooo                                      .
        |     .888.      `888        `888                  `888'     `8 `888                                    .o8
        |    .8"888.      888  oooo   888  oooo   .oooo.    888          888   .ooooo.  oooo    ooo  .oooo.   .o888oo  .ooooo.  oooo d8b
        |   .8' `888.     888 .8P'    888 .8P'   `P  )88b   888oooo8     888  d88' `88b  `88.  .8'  `P  )88b    888   d88' `88b `888""8P
        |  .88ooo8888.    888888.     888888.     .oP"888   888    "     888  888ooo888   `88..8'    .oP"888    888   888   888  888
        | .8'     `888.   888 `88b.   888 `88b.  d8(  888   888       o  888  888    .o    `888'    d8(  888    888 . 888   888  888
        |o88o     o8888o o888o o888o o888o o888o `Y888""8o o888ooooood8 o888o `Y8bod8P'     `8'     `Y888""8o   "888" `Y8bod8P' d888b
        |
        |
        |
        |  .oooooo.                             .                      oooo   .oooooo..o                          .
        | d8P'  `Y8b                          .o8                      `888  d8P'    `Y8                        .o8
        |888           .ooooo.  ooo. .oo.   .o888oo oooo d8b  .ooooo.   888  Y88bo.      oooo    ooo  .oooo.o .o888oo  .ooooo.  ooo. .oo.  .oo.
        |888          d88' `88b `888P"Y88b    888   `888""8P d88' `88b  888   `"Y8888o.   `88.  .8'  d88(  "8   888   d88' `88b `888P"Y88bP"Y88b
        |888          888   888  888   888    888    888     888   888  888       `"Y88b   `88..8'   `"Y88b.    888   888ooo888  888   888   888
        |`88b    ooo  888   888  888   888    888 .  888     888   888  888  oo     .d8P    `888'    o.  )88b   888 . 888    .o  888   888   888
        | `Y8bood8P'  `Y8bod8P' o888o o888o   "888" d888b    `Y8bod8P' o888o 8""88888P'      .8'     8""888P'   "888" `Y8bod8P' o888o o888o o888o
        |                                                                                .o..P'
        |                                                                                `Y8P'
        |
      """.stripMargin)
  }
}
