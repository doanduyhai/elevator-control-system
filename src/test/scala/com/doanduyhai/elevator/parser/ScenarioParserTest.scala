package com.doanduyhai.elevator.parser

import com.doanduyhai.elevator.FileContentReader
import com.doanduyhai.elevator.actors.{AtFloor, Pickup, Move}
import com.doanduyhai.elevator.parser.ScenarioParser._
import org.scalatest.{Matchers, FlatSpec}
import scala.concurrent.duration._

class ScenarioParserTest extends FlatSpec with Matchers with FileContentReader {

  "ScenarioParser" should "parse complex scenario" in {
    val parser = new ScenarioParser
    val scenario: Scenario = parser.parseScenario(readContentFromFile("ScenarioParserTest/complex_scenario"))


    scenario.timeStep shouldBe(1.second)
    scenario.orderQueue shouldBe(List(Move(5,8),Move(1,3),Move(2,3)))
    scenario.maxOrderQueueSize shouldBe(10)
    scenario.elevatorStatus.shouldBe(Map(1 -> (Move(4,0), Some(Pickup(Move(1,3)))), 2 -> (Move(1,2), None),3 -> (AtFloor(3), None), 4 ->(AtFloor(5), Some(Pickup(Move(6,2))))))
    scenario.timedPickup.shouldBe(List((Pickup(Move(6,4)), 5.seconds), (Pickup(Move(0,4)), 2.milliseconds)))
  }

  "Parser" should "parse timeStep" in {
    val parser = new ScenarioParser
    val parsed = parser.parse(parser.queries, "timeStep: 1s")
    val value = 1.second
    parsed should matchPattern {
      case parser.Success(List(TimeStep(value)), _) =>
    }
  }

  "Parser" should "parse moving elevator with scheduled order" in {
    val parser = new ScenarioParser
    val parsed = parser.parse(parser.queries, "1[1->3]:Move(4,0)")

    parsed should matchPattern {
      case parser.Success(List(ElevatorInput(1, Move(4,0), Some(Pickup(Move(1,3))))), _) =>
    }
  }

  "Parser" should "parse moving elevator" in {
    val parser = new ScenarioParser
    val parsed = parser.parse(parser.queries, "2: Move(1,2)")

    parsed should matchPattern {
      case parser.Success(List(ElevatorInput(2, Move(1,2), None)), _) =>
    }
  }

  "Parser" should "parse still elevator" in {
    val parser = new ScenarioParser
    val parsed = parser.parse(parser.queries, "3: AtFloor(3)")

    parsed should matchPattern {
      case parser.Success(List(ElevatorInput(3, AtFloor(3), None)), _) =>
    }
  }

  "Parser" should "parse still elevator with scheduled order" in {
    val parser = new ScenarioParser
    val parsed = parser.parse(parser.queries, "4[6->2]: AtFloor(5)")

    parsed should matchPattern {
      case parser.Success(List(ElevatorInput(4, AtFloor(5), Some(Pickup(Move(6,2))))), _) =>
    }
  }

  "Parser" should "parse order queue" in {
    val parser = new ScenarioParser
    val parsed = parser.parse(parser.queries, "OrderQueue: Move(5,8),Move(1,3),Move(2,3)")

    parsed should matchPattern {
      case parser.Success(List(OrderQueue(List(Move(5,8), Move(1,3), Move(2,3)))), _) =>
    }
  }

  "Parser" should "max order queue size" in {
    val parser = new ScenarioParser
    val parsed = parser.parse(parser.queries, "MaxOrderQueueSize: 10")

    parsed should matchPattern {
      case parser.Success(List(OrderQueueSize(10)), _) =>
    }
  }

  "Parser" should "pickup order" in {
    val parser = new ScenarioParser
    val parsed = parser.parse(parser.queries, "Pickup(0,4): 2ms")
    val delay = 2.millisecond
    parsed should matchPattern {
      case parser.Success(List(TimedPickup(Pickup(Move(0,4)), delay)), _) =>
    }
  }

  "Parser" should "parse timestep and pickup order" in {
    val value = 1.second
    val delay = 2.millisecond
    val parser = new ScenarioParser
    val parsed = parser.parseAll(parser.queries,"timeStep: 1s\nPickup(0,4): 2ms")

    parsed should matchPattern {
      case parser.Success(List(TimeStep(value), TimedPickup(Pickup(Move(0,4)), delay)), _) =>
    }
  }
}
