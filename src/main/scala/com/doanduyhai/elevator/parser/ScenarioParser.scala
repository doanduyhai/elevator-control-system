package com.doanduyhai.elevator.parser

import com.doanduyhai.elevator.actors.{AtFloor, Move, Pickup, ElevatorStatus}

import scala.concurrent.duration._
import scala.util.parsing.combinator.RegexParsers

object ScenarioParser extends RegexParsers {

  /**
    * Sample scenario definition:
    *
    * timeStep: 1s
    * 1[1->3]:Move(4,0)
    * OrderQueue: Move(5,8)
    * MaxOrderQueueSize: 5
    * Pickup(0,4): 2s
    * Pickup(6,4): 5s
    *
    */

  val MOVE =
    """Move\(([0-9]+),([0-9]+)\)"""

  val STILL = """AtFloor\(([0-9]+)\)"""
  val ELEVATOR = """\s*([0-9]+)(\[[0-9]+->[0-9]+\])?"""

  val TIMESTEP_PATTERN = ("""(?i)^\s*timeStep\s*:\s*([0-9]+)(s|ms)\s*$""").r

  val SCHEDULED_ORDER_PATTERN = ("""\[([0-9]+)->([0-9]+)\]""").r
  val STILL_ELEVATOR_PATTERN = (ELEVATOR + """\s*:\s*""" + STILL).r
  val MOVE_ELEVATOR_PATTERN = (ELEVATOR + """\s*:\s*""" + MOVE).r

  val MOVE_PATTERN = ("""\s*Move\(([0-9]+),([0-9]+)\)\s*""").r
  val ORDER_QUEUE_PATTERN = ("""(?i)^\s*OrderQueue\s*:\s*((?:Move\([0-9]+,[0-9]+\)(?:,(?!$))?)*)\s*$""").r
  val MAX_ORDER_QUEUE_SIZE_PATTERN = ("""(?i)^\s*MaxOrderQueueSize\s*:\s*([0-9]+)\s*$""").r

  val PICKUP_ORDER_PATTERN = ("""(?i)^\s*Pickup\(([0-9]+),([0-9]+)\)\s*:\s*([0-9]+)(s|ms)\s*$""").r

  sealed trait InputLine
  case class TimeStep(value: FiniteDuration) extends InputLine

  case class OrderQueue(orderQueue: List[Move]) extends InputLine

  case class OrderQueueSize(size: Int) extends InputLine

  case class ElevatorInput(id: Int, status: ElevatorStatus, scheduledOrder: Option[Pickup]) extends InputLine

  case class TimedPickup(pickup: Pickup, delay: FiniteDuration) extends InputLine

  case class Scenario(timeStep: FiniteDuration,
                      orderQueue: List[Move],
                      maxOrderQueueSize: Int,
                      elevatorStatus: Map[Int, (ElevatorStatus, Option[Pickup])],
                      timedPickup: List[(Pickup, FiniteDuration)])

}

class ScenarioParser extends RegexParsers {
  import ScenarioParser._

  def parseScenario(inputString: String): Scenario = {

    var timeStep: TimeStep = TimeStep(1.second)
    var orderQueue: List[Move] = List()
    var maxOrderQueueSize: Int = 10
    var elevatorStatus: Map[Int, (ElevatorStatus, Option[Pickup])] = Map()
    var timedPickup: List[(Pickup, FiniteDuration)] = List()

    def extractScenarioValues(lines: List[InputLine]): Unit = {
      lines.foreach(value => value match {
        case t:TimeStep => timeStep = t
        case OrderQueue(moves) => orderQueue = moves
        case OrderQueueSize(size) =>  maxOrderQueueSize = size
        case ElevatorInput(id, status, scheduledOrder) => elevatorStatus += ((id, (status, scheduledOrder)))
        case TimedPickup(pickup, delay) => timedPickup =  (pickup, delay) :: timedPickup
        case value @ _ => throw new IllegalArgumentException(s"Unsupported scenario value $value")
      })
    }

    Option(inputString) match {
      case Some(notNull) =>
        val parser = new ScenarioParser
        val parsingResult = parser.parse(parser.queries, notNull.trim)
        parsingResult match {
          case parser.Success(lines,_) => extractScenarioValues(lines)
          case parser.Failure(msg,next) => {
            throw new IllegalArgumentException(s"Error parsing scenario:\n\t'$inputString'\n msg = $msg")
          }
          case parser.Error(msg,next) => {
            throw new IllegalArgumentException(s"Error parsing scenario:\n\t'$inputString'\n")
          }
          case _ => throw new IllegalArgumentException(s"Error parsing scenario:\n\t'$inputString'\n")
        }
      case None =>
    }
    Scenario(timeStep.value, orderQueue, maxOrderQueueSize, elevatorStatus, timedPickup)
  }

  def queries: Parser[List[InputLine]] = rep(pickupOrder | timeStep | orderQueue | maxOrderQueueSize | stillElevator | movingElevator)

  def timeStep: Parser[TimeStep] = """\s*timeStep.+""".r ^^ { case x => x match {
    case TIMESTEP_PATTERN(value, unit) => {
      unit match {
        case "s" => TimeStep(value.toInt.second)
        case "ms" => TimeStep(value.toInt.millisecond)
        case _ => throw new IllegalArgumentException(s"timeStep unit should be either second (s) or millisec (ms)")
      }
    }
    case _ => throw new IllegalArgumentException(s"timeStep syntax should obey to pattern $TIMESTEP_PATTERN")
  }
  }

  def orderQueue: Parser[OrderQueue] = """\s*OrderQueue.+""".r ^^ { case x => x match {
    case ORDER_QUEUE_PATTERN(moves) => OrderQueue(moves.split("""(?<=\)),""").map(move => parseMove(move)).toList)
    case _ => throw new IllegalArgumentException(s"OrderQueue syntax should obey to pattern $ORDER_QUEUE_PATTERN")
  }
  }

  def maxOrderQueueSize: Parser[OrderQueueSize] = """\s*MaxOrderQueueSize.+""".r ^^ { case x => x match {
    case MAX_ORDER_QUEUE_SIZE_PATTERN(size) => OrderQueueSize(size.toInt)
    case _ => throw new IllegalArgumentException(s"MaxOrderQueueSize syntax should obey to pattern $MAX_ORDER_QUEUE_SIZE_PATTERN")
  }
  }

  def stillElevator: Parser[ElevatorInput] = """\s*[0-9].+AtFloor.+""".r ^^ { case x => x match {
    case STILL_ELEVATOR_PATTERN(id, option, currentFloor) => Option(option) match {
      case Some(scheduledOrder) => ElevatorInput(id.toInt, AtFloor(currentFloor.toInt), parseScheduledOrder(scheduledOrder))
      case None => ElevatorInput(id.toInt, AtFloor(currentFloor.toInt), None)
    }
    case _ => throw new IllegalArgumentException(s"Still elevator declaration syntax should obey to pattern $STILL_ELEVATOR_PATTERN")
  }
  }

  def movingElevator: Parser[ElevatorInput] = """\s*[0-9].+Move.+""".r ^^ { case x => x match {
    case MOVE_ELEVATOR_PATTERN(id, option, from, to) => Option(option) match {
      case Some(scheduledOrder) => ElevatorInput(id.toInt, Move(from.toInt, to.toInt), parseScheduledOrder(scheduledOrder))
      case None => ElevatorInput(id.toInt, Move(from.toInt, to.toInt), None)
    }
    case _ => throw new IllegalArgumentException(s"Moving elevator declaration syntax should obey to pattern $MOVE_ELEVATOR_PATTERN")
  }
  }

  def pickupOrder: Parser[TimedPickup] = """\s*Pickup.+""".r ^^ { case x => x match {
    case PICKUP_ORDER_PATTERN(from, to, duration, unit) => unit match {
      case "s" => TimedPickup(Pickup(Move(from.toInt, to.toInt)), duration.toInt.second)
      case "ms" => TimedPickup(Pickup(Move(from.toInt, to.toInt)), duration.toInt.millisecond)
      case _ => throw new IllegalArgumentException(s"Pickup delay time unit should be either second (s) or millisec (ms)")
    }
    case _ => throw new IllegalArgumentException(s"Pickup declaration syntax should obey to pattern $PICKUP_ORDER_PATTERN")
  }
  }

  private def parseScheduledOrder(scheduledOrder: String): Option[Pickup] = {
    scheduledOrder match {
      case SCHEDULED_ORDER_PATTERN(from, to) => Some(Pickup(Move(from.toInt, to.toInt)))
      case _ => throw new IllegalArgumentException(s"Elevator scheduled order syntax should obey to pattern $SCHEDULED_ORDER_PATTERN")
    }
  }

  private def parseMove(move: String): Move = {
    move match {
      case MOVE_PATTERN(from, to) => Move(from.toInt, to.toInt)
      case _ => throw new scala.IllegalArgumentException(s"$move syntax should obey to pattern $MOVE_PATTERN")
    }
  }
}
