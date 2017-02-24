package com.doanduyhai.elevator.actors

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit, ImplicitSender, EventFilter}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import scala.concurrent.duration._


class ElevatorActorTest extends TestKit(ActorSystem("ElevatorActorSystem",
    ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ElevatorActor" must  {

    "move to target floor from initial pickup floor" in {
      val elevator = system.actorOf(Props(new ElevatorActor(1, 10.millisecond, Still(0))))

      elevator ! Pickup(Move(0, 3))

      expectMsgAllOf(
        UpdateStatus(1, Move(0,3)), UpdateStatus(1, Move(1,3)), UpdateStatus(1, Move(2,3)),
        UpdateStatus(1, Still(3)))
    }

    "move to target floor from floor higher than pickup floor" in {
      val elevator = system.actorOf(Props(new ElevatorActor(1, 10.millisecond, Still(5))))

      elevator ! Pickup(Move(3, 1))

      expectMsgAllOf(
        HasScheduledOrder(1,true),
        UpdateStatus(1, Move(5,3)), UpdateStatus(1, Move(4,3)),
        UpdateStatus(1, Still(3)),
        HasScheduledOrder(1,false),
        UpdateStatus(1, Move(3,1)), UpdateStatus(1, Move(2,1)),
        UpdateStatus(1, Still(1)))
    }

    "move to target floor from floor lower than pickup floor" in {
      val elevator = system.actorOf(Props(new ElevatorActor(1, 10.millisecond, Still(1))))

      elevator ! Pickup(Move(4, 2))

      expectMsgAllOf(
        HasScheduledOrder(1,true),
        UpdateStatus(1, Move(1,4)), UpdateStatus(1, Move(2,4)), UpdateStatus(1, Move(3,4)),
        UpdateStatus(1, Still(4)),
        HasScheduledOrder(1,false),
        UpdateStatus(1, Move(4,2)), UpdateStatus(1, Move(3,2)),
        UpdateStatus(1, Still(2)))
    }

    "save pickup order and move to target floor when current move is finished" in {
      val elevator = system.actorOf(Props(new ElevatorActor(1, 10.millisecond, Move(1, 3))))

      elevator ! Pickup(Move(5, 2))

      expectMsgAllOf(
        HasScheduledOrder(1,true),
        UpdateStatus(1, Move(2,3)),
        UpdateStatus(1, Still(3)),
        UpdateStatus(1, Move(3,5)),UpdateStatus(1, Move(4,5)),
        UpdateStatus(1, Still(5)),
        HasScheduledOrder(1,false),
        UpdateStatus(1, Move(5,2)),UpdateStatus(1, Move(4,2)), UpdateStatus(1, Move(3,2)),
        UpdateStatus(1, Still(2)))
    }

    "throws exception when already has scheduled order" in {
      val elevator = system.actorOf(Props(new ElevatorActor(1, 10.millisecond, Move(1, 3), Option(Pickup(Move(5,2))))))

      EventFilter.error(message = s"Cannot accept Pickup(Move(3,0)) " +
        s"because the elevator is moving right now and a pickup Pickup(Move(5,2)) is already scheduled", occurrences = 1) intercept {
        elevator ! Pickup(Move(3, 0))
      }
    }
  }
}
